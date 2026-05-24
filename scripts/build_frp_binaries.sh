NDK_ROOT=${NDK_ROOT:-/ndk}
FRP_ROOT=${FRP_ROOT:-/frp}
FRP_ANDROID_ROOT=${FRP_ANDROID_ROOT:-/workspaces/frp-Android}

echo "Start building web assets (frps)"
cd $FRP_ROOT/web/frps
make build

echo "Start building web assets (frpc)"
cd $FRP_ROOT/web/frpc
make build

cd $FRP_ROOT

# https://dave.engineer/blog/2025/11/cross-compiling-go-android/
echo "Start building frp binaries for Android..."

echo "arm64-v8a"
export CC=$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android36-clang
GOOS=android GOARCH=arm64 CGO_ENABLED=1 go build -trimpath -ldflags "-s -w -checklinkname=0" -tags frpc -o $FRP_ANDROID_ROOT/app/src/main/jniLibs/arm64-v8a/libfrpc.so ./cmd/frpc
GOOS=android GOARCH=arm64 CGO_ENABLED=1 go build -trimpath -ldflags "-s -w -checklinkname=0" -tags frps -o $FRP_ANDROID_ROOT/app/src/main/jniLibs/arm64-v8a/libfrps.so ./cmd/frps

echo "x86_64"
export CC=$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android36-clang
GOOS=android GOARCH=amd64 CGO_ENABLED=1 go build -trimpath -ldflags "-s -w -checklinkname=0" -tags frpc -o $FRP_ANDROID_ROOT/app/src/main/jniLibs/x86_64/libfrpc.so ./cmd/frpc
GOOS=android GOARCH=amd64 CGO_ENABLED=1 go build -trimpath -ldflags "-s -w -checklinkname=0" -tags frps -o $FRP_ANDROID_ROOT/app/src/main/jniLibs/x86_64/libfrps.so ./cmd/frps

echo "armeabi-v7a"
export CC=$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi36-clang
GOOS=android GOARCH=arm GOARM=7 CGO_ENABLED=1 go build -trimpath -ldflags "-s -w -checklinkname=0" -tags frpc -o $FRP_ANDROID_ROOT/app/src/main/jniLibs/armeabi-v7a/libfrpc.so ./cmd/frpc
GOOS=android GOARCH=arm GOARM=7 CGO_ENABLED=1 go build -trimpath -ldflags "-s -w -checklinkname=0" -tags frps -o $FRP_ANDROID_ROOT/app/src/main/jniLibs/armeabi-v7a/libfrps.so ./cmd/frps

echo "Done!"