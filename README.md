# orgtd

一个把 org 文件当数据源的 Android GTD 客户端：滴答清单式的交互，写回的是纯文本 `.org`。

> 状态：设计已确认，尚未开始编码。设计文档见 [docs/plans/2026-11-24-orgtd-design.md](docs/plans/2026-11-24-orgtd-design.md)。

## 它解决什么

在 Mac 上用 Emacs 管理 `~/org`，在手机上也想随时看今天要做什么、勾掉、临时加一条——但不想再维护第二套任务系统，也不想让手机 App 把 org 文件改成自己看不懂的样子。

## 怎么工作

```
Emacs (Mac)  ──sync──  坚果云 (dav.jianguoyun.com/dav/)
                              │ WebDAV
                        FolderSync (Android, 定时双向)
                              │
                    /storage/emulated/0/orgtd
                              │ SAF 授权目录
                        orgtd App
```

App 只读写一个被授权的普通目录，同步交给系统里的同步工具。文件是唯一真源，App 本地不存副本。

## 关键约定

- 清单 = 顶层 headline（`* 工作`），任务 = 子 headline（`** TODO 写周报`）。
- 完成写 `DONE` + `CLOSED:`；日期写 `SCHEDULED`/`DEADLINE`；重复用 repeater（`+1w` / `.+3d`）。
- 给 `gtd.org` 加 `#+TODO: TODO WAITING | DONE CANCELLED`，给被修改过的任务补 `:ID:`。
- 未触及的行逐字节保留。

## 开发

工具链尚未安装，步骤见设计文档 §12。模块划分：

- `:org` — 纯 JVM 的解析/序列化库，可单测。
- `:app` — Jetpack Compose 界面。
