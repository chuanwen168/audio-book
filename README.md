# audio-book · 自建听书小站（Go 服务端 + 安卓客户端）

这是我初学 Go 语言时写的第一个小软件：一个简单的**本地/局域网听书系统**，由一个 Go 后端服务和一个安卓客户端组成。功能不复杂，代码也尽量写得直白，**供 Go / Android 初学者学习交流**。

实际使用中服务端内存占用很低，客户端轻快；家里有一堆儿歌、故事、评书等音频资源的人，可以搭一个自己的小听书站，手机上直接收听，小朋友也爱听。

> 本项目仅用于管理和收听自己有权使用的音频资源。

---

## 功能特性

**服务端（Go + Gin）**

- 扫描媒体目录下的专辑文件夹，自动列出所有含 mp3 的专辑
- 专辑详情：封面、标题、简介、分集音频列表（按文件名自然排序，"第2集" 排在 "第10集" 前）
- 每本书一个 `book.nfo`，记录书名、作者、演播者、分类、年份、简介
- 管理面板：在线编辑书籍信息、上传/替换封面
- 提供静态页面首页、详情页和管理页

**安卓客户端（Kotlin + Jetpack Compose）**

- 首页专辑双列卡片、分类筛选、专辑搜索
- 专辑详情与播放页：进度条、上一集/暂停/下一集、倍速播放
- 定时关闭（10/20/30/40/60 分钟）；连续无操作自动退出
- 后台播放：通知栏/锁屏控制，来电、插拔耳机自动暂停
- 收藏与收听历史，播放记忆（记住专辑、分集与进度，一键续播）
- 自动缓存整本书的音频与封面，离线也能打开并续播；可查看/清理缓存
- 服务地址可配置并测试连接

## 技术栈

| 端 | 技术 |
| --- | --- |
| 服务端 | Go 1.17+、Gin v1.8.2、godotenv |
| 安卓端 | Kotlin、Jetpack Compose（Material 3）、Navigation Compose、ViewModel + StateFlow |
| 网络/图片 | Retrofit2 + Gson + OkHttp、Coil |
| 播放 | AndroidX Media3（ExoPlayer + MediaSessionService） |
| 构建 | AGP 8.10.1 / Gradle 8.11.1，compileSdk 35，minSdk 26 |

## 目录结构

```
audio-book/
├── server-go/
│   └── audiobook/
│       ├── main.go              # Gin 服务主程序：路由、专辑扫描、nfo 读写、封面上传
│       ├── go.mod / go.sum
│       ├── Dockerfile
│       ├── docker-compose.yml
│       ├── .env.example         # 环境变量样例（复制为 .env）
│       └── static/              # 静态页面与默认封面
└── clien-android/
    └── audiobookclien/          # 安卓客户端工程
        ├── app/src/main/java/com/chuanwen/audiobookclien/
        │   ├── data/            # API、模型、仓库、缓存、本地存储
        │   ├── playback/        # ExoPlayer 单例、播放状态、定时关闭
        │   └── ui/              # Compose 界面：首页/详情/播放/搜索/收藏/设置
        └── gradle/wrapper/
```

## 快速开始

### 1. 启动服务端

```bash
cd server-go/audiobook

# 准备环境变量（按需修改媒体目录和端口）
cp .env.example .env

# 编译并运行
go build -o audiobook-server .
./audiobook-server
```

默认监听 `0.0.0.0:8086`。浏览器访问 `http://服务器IP:8086/` 可看到网页首页。

也可以使用 Docker：

```bash
docker compose up -d
```

### 2. 准备音频资源

服务端媒体目录（`.env` 中的 `MEDIA_ROOT`）下，**每个专辑一个子文件夹**，文件夹内直接存放 mp3：

```
MEDIA_ROOT/
├── 001 宝宝巴士睡前故事/
│   ├── 001 小蝌蚪找妈妈.mp3
│   ├── 002 三只小猪.mp3
│   └── cover.jpg            # 可选封面
└── 002 国学启蒙/
    ├── 001 三字经.mp3
    └── book.nfo             # 可选书籍信息
```

### 3. 安装安卓客户端

用 Android Studio 打开 `clien-android/audiobookclien`，Gradle 同步后连接手机运行；或命令行执行 `gradlew.bat assembleDebug` 生成 APK。

首次进入 App，在「我的 / 设置」里填写服务端地址（真机与服务端同局域网时填 `http://电脑IP:8086/`，模拟器访问本机用 `http://10.0.2.2:8086/`），测试连接通过后即可看到专辑列表。

## 书籍信息（book.nfo）示例

```json
{
  "title": "弗莱熊科学探案",
  "author": "宝宝巴士",
  "narrator": "演播者名字",
  "description": "地球文明向宇宙发出信号……",
  "category": "科幻小说",
  "year": "2008"
}
```

## 服务端接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/albums` | 专辑列表（支持 `page` / `page_size` 分页） |
| GET | `/api/v1/album/:folder` | 专辑详情（含分集音频） |
| GET | `/api/v1/album_nfo/:folder` | 读取书籍完整 nfo 信息 |
| POST | `/api/v1/edit_all/:folder` | 保存全部书籍信息 |
| POST | `/api/v1/edit_info/:folder` | 修改简介（旧接口） |
| POST | `/api/v1/edit_title/:folder` | 修改书名（旧接口） |
| POST | `/api/v1/upload_cover/:folder` | 上传/替换封面 |
| GET | `/media/:folder/:file` | 音频与封面静态访问 |

## 说明

- 项目结构和实现以"直白、易读"为优先，适合刚接触 Go Web 开发和 Android 开发的同学对照阅读。
- 服务端默认未做登录鉴权，请仅在可信的内网环境使用；公网部署需自行补充认证与安全措施。
- 客户端更详细的功能与常见问题，见 `clien-android/audiobookclien/README.md`。
