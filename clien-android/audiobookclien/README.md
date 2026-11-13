# 宝宝听书（audiobookclien）

基于自建 `audiobook-server`（Go + Gin，端口 8086）的安卓客户端，UI 风格参考喜马拉雅 / 儿歌多多等大厂儿童音频 App。

## 功能

- **首页**：橙色渐变品牌头 + 搜索入口、精选专辑轮播 Banner（自动播放）、分类频道筛选（睡前故事 / 儿歌 / 侦探探案 / 科普百科 / 国学启蒙 / 精彩故事）、双列专辑卡片（封面 + 标题 + 简介 + 集数角标）
- **专辑详情**：封面、标题、文件夹标签、简介、"播放全部（N 集）"、分集列表；当前播放高亮 + 均衡器动画图标
- **播放页**：大封面、可拖动进度条、上一集/播放暂停/下一集、倍速（0.75x ~ 2x）、定时关闭（10/20/30/40/60 分钟，到点停止播放并退出 App，剩余分钟数每分钟刷新）、播放列表底部弹层
- **后台播放**：Media3 MediaSessionService 前台服务，通知栏 / 锁屏控制、来电自动暂停（音频焦点）、插拔耳机自动暂停、划掉任务后继续播放
- **搜索页**：本地模糊搜索专辑标题 / 文件夹名，热门专辑快捷入口
- **收藏 / 历史**：专辑详情页星标收藏；最近收听自动记录；「收藏」页展示我的收藏与最近收听，点击历史可一键续播（本地保存）
- **播放记忆**：自动记住上次播放的专辑、分集与进度，首页展示「继续收听」卡片一键续播；专辑列表、专辑详情、分集队列都会在本地留一份快照，**离线也能打开专辑页和「最近收听」并续播已缓存的音频**
- **本地缓存**：播放某本书时自动缓存整本书（音频 + 封面一起落盘），播放优先走本地缓存；启动 App 时自动清理 7 天未使用的缓存，并清理下载中断留下的坏文件；「我的」页可查看缓存占用并手动清空
- **自动退出（后门）**：连续 60 分钟无任何操作（触屏、进入应用等），自动停止播放并退出 App；与「定时关闭」是两个独立计时器，互不干扰，家长没设定时也会生效
- **设置页**：服务器地址配置 + 保存 + 连接测试、缓存管理、使用提示、关于

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- AGP 8.10.1 / Gradle 8.11.1 / compileSdk 35 / minSdk 26 / targetSdk 35
- 网络：Retrofit2 + Gson + OkHttp；图片：Coil
- 播放：androidx.media3 ExoPlayer + MediaSessionService
- 架构：单 Activity + Navigation Compose；ViewModel + StateFlow

## 如何构建

**方式一（推荐）：Android Studio**
1. 用 Android Studio 打开本目录 `E:\workspace\audiobookclien`
2. 等待 Gradle 同步完成（首次会自动下载依赖）
3. 连接手机（开启 USB 调试）或启动模拟器，点击 Run ▶

**方式二：命令行**
```bat
set JAVA_HOME=D:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug
```
产物：`app\build\outputs\apk\debug\app-debug.apk`

> 本机 Android SDK 位于 `D:\android`（已写入 `local.properties`），无需额外配置。

## 服务器配置（关键）

1. 启动服务端 `audiobook-server`，确认监听 `0.0.0.0:8086`。
2. **服务端媒体根目录**：在服务端 `.env` 中设置 `MEDIA_ROOT` 指向存放专辑文件夹的目录。例如本机测试音频可直接指向：
   ```
   MEDIA_ROOT=E:\BaiduNetdiskDownload
   ```
   服务端要求每个专辑是一个子文件夹，且文件夹内**直接**存放 mp3 文件（如 `E:\BaiduNetdiskDownload\001音 宝宝巴士丨睡前故事1-300集（共300集）\001 xxx.mp3`）。
3. 打开 App「我的」→ 填写服务器地址并「测试连接」：
   - 真机 + 局域网：`http://电脑IP:8086/`（电脑上 `ipconfig` 查看 IPv4 地址）
   - 模拟器访问本机：`http://10.0.2.2:8086/`（默认值）
   - 保存后返回首页即可看到专辑列表。

## 目录结构

```
app/src/main/java/com/chuanwen/audiobookclien/
├── App.kt                  # Application（全局 Context）
├── MainActivity.kt         # 入口：连接播放服务、通知权限
├── PlayerService.kt        # Media3 后台播放服务
├── data/                   # 数据层：API、模型、服务器配置、仓库
├── playback/               # 播放层：ExoPlayer 单例、UI 状态聚合
└── ui/                     # Compose UI：主题、首页、详情、播放、搜索、设置、导航
```

## 常见问题

| 问题 | 处理 |
| --- | --- |
| 首页"加载失败：Failed to connect" | 检查手机与电脑是否同网段、服务端是否启动、地址端口是否写对 |
| 无法播放音频（能进详情） | 确认服务端 `MEDIA_ROOT` 指向正确、该专辑文件夹内直接是 mp3 |
| 通知栏不显示控制条 | Android 13+ 首次启动需允许通知权限 |
| 改了服务器地址没生效 | 保存后重新进入首页；测试连接通过即可 |
| 离线时首页空白 / 没有专辑图 | 需要先在联网状态浏览或播放过一次，专辑信息与封面会缓存到本地；之后断网仍可打开并播放已缓存的音频 |
| 离线时「最近收听」点不开 | 同上：点击过的专辑会保存分集快照，未联网打开过的专辑离线无法恢复 |
| 定时到点后 App 退出 | 这是预期行为：定时关闭会在到点时停止播放并退出 App；剩余时间每分钟刷新 |

## 服务端接口（本客户端用到的）

- `GET /api/v1/albums` — 专辑列表
- `GET /api/v1/album/{folder}` — 专辑详情（含分集音频）
- 静态：`/media/{folder}/cover.jpg`（封面）、`/media/{folder}/{file}.mp3`（音频）
