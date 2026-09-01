# Stage 23 性能与正确性结果

## 1. 证据分层

本报告严格区分四层：

1. **理论模型**：由 API 分页上限与候选数量推导，不是运行时间。
2. **主机确定性测试**：固定 seed、结构计数和键集合比较；不依赖 wall-clock。
3. **API 36 AOSP x86_64 emulator**：Room migration/DAO 与 Compose UI 结果。
4. **实体机/真实账号**：本阶段禁止连接实体机，因此统一为尚未验证。

## 2. 一百万消息模型

页面上限固定为 100。旧路径页数是 `ceil(totalMessages/100)=10,000`；新路径是 `max(1, ceil(videoMessages/100))`。最终键集合使用固定 seed 23 和确定性无碰撞排列生成，旧参考扫描全部消息后过滤，新路径只分页视频键。

| 视频密度 | 视频数 | 旧请求页 | 新请求页 | 请求下降倍数 | 旧映射对象 | 新映射对象 | 峰值页对象 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.1% | 1,000 | 10,000 | 10 | 1,000× | 1,000,000 | 1,000 | 100 |
| 1% | 10,000 | 10,000 | 100 | 100× | 1,000,000 | 10,000 | 100 |
| 5% | 50,000 | 10,000 | 500 | 20× | 1,000,000 | 50,000 | 100 |
| 20% | 200,000 | 10,000 | 2,000 | 5× | 1,000,000 | 200,000 | 100 |
| 100% | 1,000,000 | 10,000 | 10,000 | 1× | 1,000,000 | 1,000,000 | 100 |

以上数字是**理论模型**；对应的键集合相等、结构计数和固定结果由主机测试 `Stage23ScanPerformanceModelTest` 验证。100% 视频最坏稠密场景的请求数与旧基线相同，不明显劣化。

## 3. Room 结构调用模型

旧 `commitPage` 对每个视频执行存在性查询和单条替换，结构调用模型为：

```text
legacyDaoCalls = 5 * videoCount + 2 * historyPages
```

其中每个视频包含存在性查询、upsert、tag insert、cross-ref delete/insert，每页另有 channel update 和全局 orphan cleanup。

新路径每个含候选的页面最多 6 个批量 DAO 往返（existing keys、videos、tags、delete refs、insert refs、channel），全扫描结束再执行 1 次 orphan cleanup：

```text
filteredDaoCalls <= 6 * filteredPages + 1
```

标签从 0 增到每视频 40 个只增加批量行数，不增加页级 DAO 往返。页面事务最大只持有 100 个 TDLib 候选及其标签；不会把整个频道放进一个事务。

## 4. 正确性/恢复模型

确定性模型与 repository/Room 测试覆盖：

- 五档密度的旧参考/新过滤最终视频键 `BitSet` 完全相同。
- 短页 next 非零、空页 next 推进、非空最终页 next 为零。
- 重复边界和页面内重复键只保留一个复合主键实体。
- 非零游标相同或反向时进入分页停滞，不再请求无限循环。
- 页面结果到达前取消时事务数和游标不变；事务提交后模拟重启从持久 next cursor 继续。
- 严重不均衡多频道首轮顺序保证每个频道在任何频道第二页前先获得一页；生产 worker 上限为 2。
- 多 hashtag 与无 hashtag 的 DAO 往返上界相同。
- recent 对账页与持久历史游标精确对齐时，每个已提交页同时推进历史游标；不同语义或不相等的游标保持隔离。

### 4.1 对齐 recent 页去重模型

主机确定性 repository 场景使用 `lastNewMessageId=80`，服务端游标链 `0→90→70→50→0`。旧实现只在 recent 首页面推进一次历史游标，越过边界后从 90 重放 90/70 两页：

```text
旧请求/事务页：0, 90, 70, 90, 70, 50  = 6
新请求/事务页：0, 90, 70, 50          = 4
```

这是**主机确定性结构计数**，请求页、映射视频对象和页面事务均下降 33.3%，最终复合视频键集合都为 `{50,70,90,100}`。该收益只发生在 recent 请求游标与持久历史游标精确相等的安全条件下；不据此推断真实 Telegram 网络 wall-clock。

## 5. 实测结果

以下均为 2026-08-30 在 Android Studio JBR 下得到的结果；Gradle 用时只用于命令追踪，不作为性能门槛：

- **主机定向测试**：`TelegramClientManagerTest` 26/26、`TdLibTelegramMessageRepositoryTest` 29/29、`Stage23ScanPerformanceModelTest` 5/5，共 60/60；`ChannelSelectionViewModelTest` 7/7。新增的第 29 项 repository 测试锁定 recent 对齐页不重放。
- **主机 Compose Path B**：instrumentation Kotlin 编译通过；Robolectric-Compose 的 Login 16、ChannelSelection 7、ComposeSmoke 2、CacheSettings 3，共 28/28。
- **主机全量**：最终代码上的 `test --rerun-tasks` 退出码 0，345 个 Gradle task 全部执行；JUnit XML 合计 1,081/1,081，failure/error/skipped 均为 0。
- **主机静态/构建**：`lint --rerun-tasks` 退出码 0（220 个 task）；`assembleDebug --rerun-tasks` 退出码 0（190 个 task）；额外 `:app:assembleRelease --rerun-tasks` 退出码 0（222 个 task）。
- **API 36 AOSP x86_64 emulator / Room**：Room v5 migration、批量 DAO、标签清理和 query-plan 共 15/15。
- **API 36 AOSP x86_64 emulator / Compose**：最终代码的完整套件 `OK (99 tests)`，失败/崩溃为 0。另以 360dp 折叠/展开、320dp + 1.35 字号生成 3 张截图；视觉反馈循环先发现长计数截断，再改为折叠双行和紧凑首行字号，最终处理量、已索引量和四格详情均完整可读。
- **APK 静态结果**：debug/release 有效权限都仅为 `INTERNET`、`ACCESS_NETWORK_STATE`；二者 native 条目都只在 `arm64-v8a`，且只有既有白名单的 `libtdjni.so`、`libandroidx.graphics.path.so`、`libdatastore_shared_counter.so`；instrumentation target/test APK 的 native 条目为 0。
- **iQOO 12、其他实体机、真实 Telegram 账号、真实超长频道网络往返和 wall-clock benchmark**：**尚未验证**。

## 6. 结论门槛

在主机确定性测试通过后，稀疏频道复杂度门槛由结构证据满足：1% 视频从 10,000 请求页降到 100 页（理论 100×）；0.1% 为理论 1,000×；100% 与基线持平。该结论不宣称 TDLib 服务器实际延迟、缓存命中或实体机 CPU/磁盘耗时已经验证。
