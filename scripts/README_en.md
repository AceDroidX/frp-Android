# frp Binaries Build & Update

This directory provides two approaches to obtain frp binaries and place them into the Android project's `jniLibs` directory:

## Method 1: Build from Source

Script `build_frp_binaries.sh` uses Go cross-compilation to build Android native libraries directly from the [fatedier/frp](https://github.com/fatedier/frp) source code, leveraging the Android NDK Clang toolchain.

Prerequisites
- Go toolchain
- Android NDK (set `NDK_ROOT` environment variable)
- frp source code (set `FRP_ROOT` environment variable)
- frp-Android source code (set `FRP_ANDROID_ROOT` environment variable)

Linux Usage Examples:
```
# Set environment variables then run
export NDK_ROOT=/path/to/android-ndk
export FRP_ROOT=/path/to/frp/source
export FRP_ANDROID_ROOT=/path/to/frp-Android
./scripts/build_frp_binaries.sh
```

## Method 2: Download Prebuilt Binaries from Release

Script `update_frp_binaries.sh` fetches the latest release of [fatedier/frp](https://github.com/fatedier/frp) and extracts prebuilt `frpc`/`frps` executables for the following architectures:

- `android_arm64` -> `app/src/main/jniLibs/arm64-v8a/`
- `linux_amd64`   -> `app/src/main/jniLibs/x86_64/`
- `linux_arm`     -> `app/src/main/jniLibs/armeabi-v7a/`

Files will be copied as `libfrpc.so` and `libfrps.so` in the target directories (executable bit preserved).

Prerequisites
- `curl`, `jq`, `tar` and `bash` must be available on your system.

Linux Usage Examples:
```
# Download latest release and place in jniLibs
./scripts/update_frp_binaries.sh

# Specify release tag
./scripts/update_frp_binaries.sh --tag v0.65.0

# Dry-run (safe):
./scripts/update_frp_binaries.sh --dry-run

# Use a custom destination base directory
./scripts/update_frp_binaries.sh --dest app/src/main/jniLibs_custom

# Use a GitHub token to increase API quota
./scripts/update_frp_binaries.sh --token <GITHUB_TOKEN>
```

Windows PowerShell Usage Examples:
```
# Download latest release
pwsh ./scripts/update_frp_binaries.ps1

# Use a specific release tag
pwsh ./scripts/update_frp_binaries.ps1 -Tag v0.65.0

# Dry-run:
pwsh ./scripts/update_frp_binaries.ps1 -DryRun
```
