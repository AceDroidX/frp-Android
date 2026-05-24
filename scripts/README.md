# frp 二进制文件构建与更新

本目录提供两种方式获取 frp 二进制文件，将其放置在 Android 项目的 `jniLibs` 目录中：

## 方法一：从源代码编译

脚本 `build_frp_binaries.sh` 使用 Go 交叉编译直接从 [fatedier/frp](https://github.com/fatedier/frp) 源代码构建 Android 原生库，利用 Android NDK 的 Clang 工具链实现跨平台编译。

先决条件
- Go 工具链
- Node.js & npm
- Android NDK（需设置 `NDK_ROOT` 环境变量）
- frp 源代码（需设置 `FRP_ROOT` 环境变量）
- frp-Android 源代码（需设置 `FRP_ANDROID_ROOT` 环境变量）

Linux 使用示例
```
# 设置环境变量后执行
export NDK_ROOT=/path/to/android/ndk/30.0.14904198
export FRP_ROOT=/path/to/github/frp
export FRP_ANDROID_ROOT=/path/to/github/frp-Android
./scripts/build_frp_binaries.sh
```

## 方法二：从 Release 下载预编译文件

脚本 `update_frp_binaries.sh` 会获取 [fatedier/frp](https://github.com/fatedier/frp) 的最新 release（或使用指定的 tag），并从预编译的压缩包中提取 `frpc` / `frps` 可执行文件，支持以下架构映射：

- `android_arm64` -> `app/src/main/jniLibs/arm64-v8a/`
- `linux_amd64`   -> `app/src/main/jniLibs/x86_64/`
- `linux_arm`     -> `app/src/main/jniLibs/armeabi-v7a/`

提取的文件会被复制为 `libfrpc.so` 与 `libfrps.so`，并保留可执行权限（即 `chmod +x`）。

先决条件
- 系统需安装 `curl`、`jq`、`tar` 和 `bash`。

Linux 使用示例
```
# 下载最新 release 并更新 jniLibs
./scripts/update_frp_binaries.sh

# 指定 release tag
./scripts/update_frp_binaries.sh --tag v0.65.0

# 仅模拟运行（不会下载或写入文件）：
./scripts/update_frp_binaries.sh --dry-run

# 指定自定义目标基础目录
./scripts/update_frp_binaries.sh --dest app/src/main/jniLibs_custom

# 使用 GitHub Token 提升 API 请求配额
./scripts/update_frp_binaries.sh --token <GITHUB_TOKEN>
```

Windows PowerShell 使用示例:
```
# 下载最新 release 并更新 jniLibs
pwsh ./scripts/update_frp_binaries.ps1

# 指定 release tag
pwsh ./scripts/update_frp_binaries.ps1 -Tag v0.65.0

# 模拟运行（不会写入）
pwsh ./scripts/update_frp_binaries.ps1 -DryRun
```

