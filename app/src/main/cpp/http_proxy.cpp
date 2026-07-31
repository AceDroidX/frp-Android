#include <jni.h>
#include <android/log.h>

#include <arpa/inet.h>
#include <netdb.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdint>
#include <cerrno>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_set>
#include <utility>
#include <vector>

namespace {

constexpr char kLogTag[] = "NativeHttpProxy";
constexpr size_t kMaxHeaderBytes = 64 * 1024;
constexpr int kIoTimeoutSeconds = 30;

std::atomic<bool> g_running{false};
std::atomic<int> g_listen_fd{-1};
std::thread g_accept_thread;
std::mutex g_state_mutex;
std::mutex g_clients_mutex;
std::unordered_set<int> g_client_fds;

void log_error(const std::string &message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
}

void close_socket(int fd) {
    if (fd >= 0) {
        shutdown(fd, SHUT_RDWR);
        close(fd);
    }
}

class TrackedSocket {
public:
    explicit TrackedSocket(int fd = -1) : fd_(fd) {
        if (fd_ >= 0) {
            std::lock_guard lock(g_clients_mutex);
            g_client_fds.insert(fd_);
        }
    }

    TrackedSocket(const TrackedSocket &) = delete;
    TrackedSocket &operator=(const TrackedSocket &) = delete;

    TrackedSocket(TrackedSocket &&other) noexcept : fd_(std::exchange(other.fd_, -1)) {}

    ~TrackedSocket() { reset(); }

    int get() const { return fd_; }

    void reset() {
        if (fd_ < 0) return;
        {
            std::lock_guard lock(g_clients_mutex);
            g_client_fds.erase(fd_);
        }
        close_socket(fd_);
        fd_ = -1;
    }

private:
    int fd_;
};

bool send_all(int fd, const char *data, size_t length) {
    while (length > 0) {
        const ssize_t written = send(fd, data, length, MSG_NOSIGNAL);
        if (written < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (written == 0) return false;
        data += written;
        length -= static_cast<size_t>(written);
    }
    return true;
}

bool send_all(int fd, const std::string &data) {
    return send_all(fd, data.data(), data.size());
}

void send_http_error(int fd, int status, const char *reason) {
    const std::string body = std::to_string(status) + " " + reason + "\n";
    const std::string response =
        "HTTP/1.1 " + std::to_string(status) + " " + reason + "\r\n"
        "Connection: close\r\nContent-Type: text/plain\r\nContent-Length: " +
        std::to_string(body.size()) + "\r\n\r\n" + body;
    send_all(fd, response);
}

std::string lower_ascii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

bool split_authority(const std::string &authority, std::string &host, std::string &port,
                     const char *default_port) {
    if (authority.empty()) return false;

    if (authority.front() == '[') {
        const size_t bracket = authority.find(']');
        if (bracket == std::string::npos) return false;
        host = authority.substr(1, bracket - 1);
        if (bracket + 1 < authority.size()) {
            if (authority[bracket + 1] != ':') return false;
            port = authority.substr(bracket + 2);
        } else {
            port = default_port;
        }
    } else {
        const size_t colon = authority.rfind(':');
        if (colon != std::string::npos && authority.find(':') == colon) {
            host = authority.substr(0, colon);
            port = authority.substr(colon + 1);
        } else {
            host = authority;
            port = default_port;
        }
    }
    return !host.empty() && !port.empty();
}

int connect_remote(const std::string &host, const std::string &port) {
    addrinfo hints{};
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_family = AF_UNSPEC;

    addrinfo *addresses = nullptr;
    const int resolve_result = getaddrinfo(host.c_str(), port.c_str(), &hints, &addresses);
    if (resolve_result != 0) {
        log_error("DNS resolution failed for " + host + ": " + gai_strerror(resolve_result));
        return -1;
    }

    int remote_fd = -1;
    for (addrinfo *address = addresses; address != nullptr; address = address->ai_next) {
        remote_fd = socket(address->ai_family, address->ai_socktype, address->ai_protocol);
        if (remote_fd < 0) continue;

        timeval timeout{kIoTimeoutSeconds, 0};
        setsockopt(remote_fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(remote_fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
        if (connect(remote_fd, address->ai_addr, address->ai_addrlen) == 0) break;
        close(remote_fd);
        remote_fd = -1;
    }
    freeaddrinfo(addresses);
    return remote_fd;
}

void relay_bidirectional(int first, int second) {
    std::vector<char> buffer(32 * 1024);
    pollfd descriptors[2] = {
        {first, POLLIN, 0},
        {second, POLLIN, 0},
    };

    while (g_running.load(std::memory_order_relaxed)) {
        const int result = poll(descriptors, 2, 1000);
        if (result < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (result == 0) continue;

        for (int index = 0; index < 2; ++index) {
            if ((descriptors[index].revents & (POLLIN | POLLHUP)) == 0) continue;
            const int source = descriptors[index].fd;
            const int destination = descriptors[1 - index].fd;
            const ssize_t received = recv(source, buffer.data(), buffer.size(), 0);
            if (received <= 0 ||
                !send_all(destination, buffer.data(), static_cast<size_t>(received))) {
                return;
            }
        }
    }
}

std::string find_header(const std::string &headers, const std::string &wanted_name) {
    size_t line_start = headers.find("\r\n") + 2;
    while (line_start != std::string::npos && line_start < headers.size()) {
        const size_t line_end = headers.find("\r\n", line_start);
        if (line_end == std::string::npos || line_end == line_start) break;
        const size_t colon = headers.find(':', line_start);
        if (colon != std::string::npos && colon < line_end) {
            const std::string name = lower_ascii(headers.substr(line_start, colon - line_start));
            if (name == wanted_name) {
                size_t value_start = colon + 1;
                while (value_start < line_end &&
                       (headers[value_start] == ' ' || headers[value_start] == '\t')) {
                    ++value_start;
                }
                return headers.substr(value_start, line_end - value_start);
            }
        }
        line_start = line_end + 2;
    }
    return {};
}

void handle_client(int accepted_fd) {
    TrackedSocket client(accepted_fd);
    timeval timeout{kIoTimeoutSeconds, 0};
    setsockopt(client.get(), SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(client.get(), SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

    std::string request;
    request.reserve(4096);
    char chunk[8192];
    size_t header_end = std::string::npos;
    while (request.size() < kMaxHeaderBytes) {
        const ssize_t received = recv(client.get(), chunk, sizeof(chunk), 0);
        if (received <= 0) return;
        request.append(chunk, static_cast<size_t>(received));
        header_end = request.find("\r\n\r\n");
        if (header_end != std::string::npos) break;
    }
    if (header_end == std::string::npos) {
        send_http_error(client.get(), 431, "Request Header Fields Too Large");
        return;
    }

    const size_t first_line_end = request.find("\r\n");
    const size_t first_space = request.find(' ');
    const size_t second_space = request.find(' ', first_space + 1);
    if (first_line_end == std::string::npos || first_space == std::string::npos ||
        second_space == std::string::npos || second_space > first_line_end) {
        send_http_error(client.get(), 400, "Bad Request");
        return;
    }

    const std::string method = request.substr(0, first_space);
    const std::string target = request.substr(first_space + 1, second_space - first_space - 1);
    std::string host;
    std::string port;

    if (lower_ascii(method) == "connect") {
        if (!split_authority(target, host, port, "443")) {
            send_http_error(client.get(), 400, "Bad CONNECT Target");
            return;
        }
        TrackedSocket remote(connect_remote(host, port));
        if (remote.get() < 0) {
            send_http_error(client.get(), 502, "Bad Gateway");
            return;
        }
        if (!send_all(client.get(), "HTTP/1.1 200 Connection Established\r\n\r\n")) return;
        const size_t tunnel_data_start = header_end + 4;
        if (tunnel_data_start < request.size() &&
            !send_all(remote.get(), request.data() + tunnel_data_start,
                      request.size() - tunnel_data_start)) {
            return;
        }
        relay_bidirectional(client.get(), remote.get());
        return;
    }

    std::string origin_target = target;
    const std::string lower_target = lower_ascii(target);
    if (lower_target.rfind("http://", 0) == 0) {
        const size_t authority_start = 7;
        const size_t path_start = target.find_first_of("/?#", authority_start);
        const std::string authority = target.substr(
            authority_start,
            path_start == std::string::npos ? std::string::npos : path_start - authority_start
        );
        if (!split_authority(authority, host, port, "80")) {
            send_http_error(client.get(), 400, "Bad Proxy Target");
            return;
        }
        origin_target = path_start == std::string::npos ? "/" : target.substr(path_start);
        if (!origin_target.empty() && origin_target.front() == '?') origin_target.insert(0, "/");
        const size_t fragment = origin_target.find('#');
        if (fragment != std::string::npos) origin_target.erase(fragment);
    } else if (lower_target.rfind("https://", 0) == 0) {
        send_http_error(client.get(), 400, "Use CONNECT For HTTPS");
        return;
    } else {
        if (!split_authority(find_header(request, "host"), host, port, "80")) {
            send_http_error(client.get(), 400, "Host Header Required");
            return;
        }
    }

    TrackedSocket remote(connect_remote(host, port));
    if (remote.get() < 0) {
        send_http_error(client.get(), 502, "Bad Gateway");
        return;
    }

    // 上游 HTTP 服务器需要 origin-form 请求行；请求体及其他头保持字节级原样转发。
    std::string forwarded = method + " " + origin_target + request.substr(second_space);
    if (!send_all(remote.get(), forwarded)) return;
    relay_bidirectional(client.get(), remote.get());
}

void accept_connections(int listen_fd) {
    while (g_running.load(std::memory_order_relaxed)) {
        sockaddr_storage address{};
        socklen_t address_length = sizeof(address);
        const int client_fd = accept(listen_fd, reinterpret_cast<sockaddr *>(&address),
                                     &address_length);
        if (client_fd < 0) {
            if (errno == EINTR) continue;
            if (g_running.load(std::memory_order_relaxed)) {
                log_error("accept failed: " + std::string(strerror(errno)));
            }
            break;
        }
        std::thread(handle_client, client_fd).detach();
    }
}

std::string start_proxy(int port) {
    std::lock_guard state_lock(g_state_mutex);
    if (g_running.load()) return {};

    const int listen_fd = socket(AF_INET6, SOCK_STREAM, 0);
    if (listen_fd < 0) return "socket: " + std::string(strerror(errno));

    int enabled = 1;
    int ipv6_only = 0;
    setsockopt(listen_fd, SOL_SOCKET, SO_REUSEADDR, &enabled, sizeof(enabled));
    setsockopt(listen_fd, IPPROTO_IPV6, IPV6_V6ONLY, &ipv6_only, sizeof(ipv6_only));

    sockaddr_in6 address{};
    address.sin6_family = AF_INET6;
    address.sin6_addr = in6addr_any;
    address.sin6_port = htons(static_cast<uint16_t>(port));
    if (bind(listen_fd, reinterpret_cast<sockaddr *>(&address), sizeof(address)) != 0) {
        const std::string error = "bind: " + std::string(strerror(errno));
        close(listen_fd);
        return error;
    }
    if (listen(listen_fd, 128) != 0) {
        const std::string error = "listen: " + std::string(strerror(errno));
        close(listen_fd);
        return error;
    }

    g_listen_fd.store(listen_fd);
    g_running.store(true);
    g_accept_thread = std::thread(accept_connections, listen_fd);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "Listening on [::]:%d", port);
    return {};
}

void stop_proxy() {
    std::lock_guard state_lock(g_state_mutex);
    if (!g_running.exchange(false)) return;

    close_socket(g_listen_fd.exchange(-1));
    {
        std::lock_guard clients_lock(g_clients_mutex);
        for (const int fd : g_client_fds) shutdown(fd, SHUT_RDWR);
    }
    if (g_accept_thread.joinable()) g_accept_thread.join();
}

jstring make_jstring(JNIEnv *env, const std::string &value) {
    return value.empty() ? nullptr : env->NewStringUTF(value.c_str());
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_acedroidx_frp_NativeHttpProxy_start(JNIEnv *env, jobject, jint port) {
    if (port <= 0 || port > 65535) return make_jstring(env, "invalid port");
    return make_jstring(env, start_proxy(port));
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_acedroidx_frp_NativeHttpProxy_stop(JNIEnv *, jobject) {
    stop_proxy();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_acedroidx_frp_NativeHttpProxy_isRunning(JNIEnv *, jobject) {
    return g_running.load() ? JNI_TRUE : JNI_FALSE;
}
