# XiaoMi-VersionHook

按作用域精准回显（伪装）Android 系统版本属性的 LSPosed 模块。
Modern LibXposed API 102 · 纯 Java · 无 Gradle 构建。

## 功能

- 勾选进作用域的 App，读取以下版本键时返回你在 App 里设定的值：
  `ro.build.version.*` / `ro.mi.os.version.*` / `ro.system.build.version.*`
- 按 key 语义返回：`incremental` → 完整串、`name` → `OS主.次`、`code` → 纯数字
  （数字语义很重要：输入法门禁对 code 做 toIntOrNull，回完整串会判死）
- 覆盖 `get` / `get(String,String)` / `getInt` / `getLong` / `getBoolean`
- 同时反射修正 `Build.VERSION.INCREMENTAL` / `Build.INCREMENTAL` 静态快照
  （zygote 启动时固化，属性污染会遗传给所有进程，属性 hook 拦不住字段读取）

## 安装

LSPosed 管理器（需支持 Modern API 101+ 的 fork）→ 模块 → 从存储安装 → 启用 → 勾作用域。
配置 App 打开即用；保存写入本地 Prefs，强停目标 App 后生效。
留空保存 = 恢复内置默认（config.properties 里的 `fake_version`，可自行改）。

## 已知副作用

改版本号后，读版本做门禁的组件会受影响（实测：超级小爱输入法提示"需要 HyperOS 4.0"）。
code 键回纯数字、或填保格式值（`OS99.99.99.99.XPNCNXM`）可缓解。

## 无 Gradle 构建

见 `build.sh`。要点：
- `libxposed:api` 是 compileOnly（运行时框架提供），**不要**打进 dex
- KSU late-load 模式下模块注入发生在 exploit 触发后的新 fork 进程
- `res/mipmap-xxxhdpi/ic_launcher.png` 图标 + `META-INF/xposed/*` 经 python zipfile 追加进 aapt2 产物

## 声明

改自己设备，风险自担。Author: Lolipop
