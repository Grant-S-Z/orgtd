# 发布说明

## 签名材料

发布包用 `keystore.properties` + `orgtd-release.jks` 签名，这两个文件**不进仓库**（`.gitignore` 已忽略）。当前备份在仓库之外：

```
~/orgtd-release-keys/
├── orgtd-release.jks
├── keystore.properties
└── README.txt
```

⚠️ 密钥丢失 = 无法覆盖升级已安装的 orgtd，只能卸载重装。请额外备份到密码管理器或离线介质。

没有 `keystore.properties` 时，`assembleRelease` 会产出**未签名**的 APK（不能直接安装）。

## 本地出包

```bash
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

签名校验：

```bash
$ANDROID_HOME/build-tools/37.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

### 从 debug 包切换到 release 包

debug 与 release 签名不同，**必须先卸载** debug 包：

```bash
adb uninstall dev.orgtd
adb install app/build/outputs/apk/release/app-release.apk
```

卸载会清掉 App 内的偏好设置和目录授权（org 文件本身不受影响），首次启动重新选一次目录或填一次 WebDAV 即可。

## 版本号

- `versionCode`：每次发布 +1
- `versionName`：语义化版本，当前 `0.1.0`
- 打 tag：`git tag v0.1.0 && git push --tags`

## GitHub Actions 发布

`.github/workflows/release.yml` 在推送 `v*` tag 时构建 release 包并创建 GitHub Release。需要在仓库 Secrets 里配置：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i orgtd-release.jks` 的输出 |
| `KEYSTORE_PASSWORD` | keystore.properties 里的 storePassword |
| `KEY_ALIAS` | `orgtd` |
| `KEY_PASSWORD` | keystore.properties 里的 keyPassword |

本地生成 base64：

```bash
base64 -i ~/orgtd-release-keys/orgtd-release.jks | pbcopy
```

## 发布前检查清单

- [ ] `./gradlew :orgmode:test :app:testDebugUnitTest` 全绿
- [ ] `./gradlew :app:lintDebug` 无 Error
- [ ] release 包安装后冒烟：读文件 / 勾选 / 新建 / 清单详情 / 搜索 / WebDAV / 提醒
- [ ] `versionCode` 已 +1，`CHANGELOG.md` 已更新
- [ ] 测试 fixture 已脱敏，截图不含个人内容
- [ ] 密钥已备份
