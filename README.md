# 照办（ZhaoBan）

一款 Android 相册整理 App。参考 [Slidebox](https://apps.apple.com/app/slidebox-photo-album-organizer/id897265286) 的交互：全屏单张浏览，靠滑动手势快速完成「收藏 / 删除 / 归类」，一轮整理完成后一键批量同步回系统相册。

**纯本地运行，不上传任何数据。**

## 功能

### 整理流程

| 操作 | 行为 |
|---|---|
| 左右滑动 | 上一张 / 下一张 |
| **下滑** | 加入系统「收藏」（`IS_FAVORITE`） |
| **上滑** | 标记待删除 |
| 点底部相册卡片 | 归入该系统相册文件夹 |
| 点「新建」 | 创建 `Pictures/<名称>` 文件夹并归入 |
| 顶栏「确认同步 (N)」 | **批量落库**：一次授权完成所有移动+收藏，再一次授权完成删除 |

- 整理过程中只在本机记账（Room 持久化队列），**不碰系统文件**；退出 App / 杀进程进度不丢。
- 按下「确认同步」才真正写入系统相册：移动 = 修改 `RELATIVE_PATH`，收藏 = `IS_FAVORITE`，删除 = 移入系统「最近删除」（可恢复，保留期由系统决定，通常 30 天）。
- 全程走系统授权弹窗（`createWriteRequest` / `createTrashRequest`），最多两次，无静默操作。

### 主页只显示未整理

主页网格**只列出未整理的照片**，已归类的不再出现：

- **未整理** = 位于系统默认收件箱目录（`DCIM/Camera`、`DCIM/Screenshots`、`Pictures/Screenshots` 等）；
- **已整理** = 已被 Slidebox / 本 App 移进自定义文件夹的照片；
- 整理完并确认同步后，这批照片自动从主页消失。

### 其他

- 支持格式：JPEG / PNG / WEBP；Android 9+ 额外支持 HEIC / HEIF（iPhone 同步照片）。
- Android 14+ 兼容部分照片授权（可只勾选部分照片让 App 处理）。
- 适配 Android 16（targetSdk 36，边到边 + 预测式返回）。
- 自定义启动图标。

## 技术栈

- Kotlin + Jetpack Compose（Material3）
- Room（待同步队列 + 已整理记录，进程重启可续）
- Coil（图片加载）
- MVVM / StateFlow
- minSdk 26 · targetSdk 36

## 构建

要求：JDK 17+，Android SDK（platform 36）。

```bash
git clone https://github.com/zxidd24/ZhaoBanApp.git
cd ZhaoBanApp
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew :app:assembleDebug     # debug 包
./gradlew :app:assembleRelease   # release 包（需签名配置，见下）
```

代理环境下拉依赖失败时：

```bash
./gradlew ... -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=PORT \
              -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=PORT
```

### 签名（release）

`keystore/` 与 `keystore.properties` 不入库，需自备。在项目根建 `keystore.properties`：

```properties
storeFile=keystore/release.keystore
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

缺该文件时 release 自动退化为 debug 签名。

## 安装体验

1. 把 `app/build/outputs/apk/release/app-release.apk` 传到手机安装（允许未知来源）。
2. 打开「照办」→ 授权读取照片。
3. 首页点「开始整理 N 张」→ 滑动整理 → 顶栏「确认同步」→ 完成。

## 项目结构

```
app/src/main/kotlin/com/zhaoban/app/
  MainActivity.kt            入口（enableEdgeToEdge）
  data/                      Room：pending_action / reviewed；Photo / SystemAlbum 模型
  media/
    PhotoLoader.kt           MediaStore 查询（照片 + 系统相册文件夹聚合）
    MediaAccess.kt           权限三态（全量 / 部分 / 拒绝）
    MediaStoreActions.kt     批量写 / 删 / 收藏 PendingIntent
  vm/MainViewModel.kt        待同步队列、批量同步状态机、未整理判定
  ui/
    AppRoot.kt               权限门 + 导航（Home ⇄ Review）
    screens/HomeScreen.kt    未整理网格
    screens/ReviewScreen.kt  全屏整理（手势 + 相册 dock + 确认同步）
    components/PhotoThumb.kt
```

## 隐私

- 不联网、不上传、无任何第三方统计 SDK。
- 照片只经由系统 MediaStore 读写；删除走系统回收站可随时恢复。

## Roadmap

- [ ] 同相册连滑多张合并为一次系统授权
- [ ] HEIC 动图 / Live Photo（当前只解首帧）
- [ ] 整理统计（本周整理了多少张）
