# frp-Android
A frp client for Android  
一个Android的frpc客户端

简体中文 | [English](README_en.md)

<div style="display:inline-block">
<img src="https://cdn.jsdelivr.net/gh/AceDroidX/frp-Android/image/image1.jpg" alt="image1.png" width="300">
<img src="https://cdn.jsdelivr.net/gh/AceDroidX/frp-Android/image/image2.jpg" alt="image2.png" width="300">
</div>

## 编译方法

如果您想自定义frp内核，可以通过Github Actions或通过Android Studio编译

### 通过Github Actions编译

1. 将您的apk签名密钥文件转为base64，以下为Linux示例
```shell
base64 -w 0 keystore.jks > keystore.jks.base64
```
2. fork本项目
3. 转到Github项目的此页面：Settings > Secrets and variables > Actions > Repository secrets
4. 添加以下四个环境变量：
```KEY_ALIAS``` ```KEY_PASSWORD``` ```STORE_FILE``` ```STORE_PASSWORD```  
其中```STORE_FILE```的内容为步骤1的base64，其他环境变量内容请根据您的密钥文件自行填写
5. Push提交自动触发编译或在Actions页面手动触发

### 通过Android Studio编译

1. 在项目根目录创建apk签名密钥设置文件```keystore.properties```，内容参考同级的```keystore.example.properties```
2. 使用Android Studio进行编译打包

## 常见问题
### 项目的frp内核(libfrpc.so)是怎么来的？
直接从[frp的release](https://github.com/fatedier/frp/releases)里把对应ABI的Linux版本压缩包解压之后重命名frpc为libfrpc.so  
项目不是在代码里调用so中的方法，而是把so作为一个可执行文件，然后通过shell去执行对应的命令  
因为Golang的零依赖特性，所以可以直接在Android里通过shell运行可执行文件