# Stage 17：弱网连续播放性能结果

## 1. 方法

- 生产修改前先运行既有 selector、TDLib file manager/client、Media3 DataSource/preload/player metrics 和 ViewModel 定向测试，全部通过。
- 修改前使用固定 seed `170017` 冻结基线；修改后用完全相同的 30 条预生成 trace、相同 RTT/抖动/暂停和相同缓存序列重跑。
- profile：NORMAL 12 Mbps/30 ms/±5%；SLOW05 0.5 Mbps/180 ms/±35%/短暂停顿；SLOW10 1 Mbps/120 ms/±25%/短暂停顿；SLOW20 2 Mbps/80 ms/±15%/短暂停顿。
- 每档 30 次转场，缓存序列固定循环：冷缓存、shifted partial 128 KiB、下一条 prefix 最多 256 KiB；chunk 32 KiB，20 秒视频，启动模型 1.5 秒。
- synthetic H.264：fileId 101=360p/0.35 Mbps、102=480p/0.65 Mbps、103=720p/1.6 Mbps。baseline 模拟 Stage 16 Wi-Fi AUTO 固定 720p；final 使用 0.7 安全带宽。
- 模拟器只证明同条件策略性能；真实基础设施行为由 manager/client/DataSource/ViewModel 单测独立证明。

## 2. 修改前冻结基线

未修改代码时定向测试均通过。固定 seed baseline：NORMAL 首帧 p95 329.2 ms；SLOW05/SLOW10/SLOW20 分别为 7031.1/3238.7/1538.7 ms。SLOW05 与 SLOW10 均为 30 次 rebuffer；每档 shifted partial 的可避免重复字节为 1,310,720。

## 3. 相同 trace baseline/final

时间单位为 ms；`p50/p95`。

| Profile | 质量 fileId | settled→prepare | prepare→READY | settled→首帧 | rebuffer 次数/总时长 | TDLib calls | 重复字节 |
|---|---:|---:|---:|---:|---:|---:|---:|
| NORMAL baseline | 103 / 720p | 10.4/11.9 | 143.1/238.5 | 233.7/329.2 | 0/0 | 40 | 1,310,720 |
| NORMAL final | 103 / 720p | 10.4/11.9 | 143.1/238.5 | 233.7/329.2 | 0/0 | 30 | 0 |
| SLOW05 baseline | 103 / 720p | 10.5/12.0 | 2718.3/6943.0 | 2808.6/7031.1 | 30/1,421,100 | 40 | 1,310,720 |
| SLOW05 final | 101 / 360p | 10.5/12.0 | 180.0/1484.4 | 271.1/1572.5 | 3/1,267 | 10 | 0 |
| SLOW10 baseline | 103 / 720p | 9.5/11.7 | 1466.7/3148.7 | 1555.4/3238.7 | 30/408,182 | 40 | 1,310,720 |
| SLOW10 final | 102 / 480p | 9.5/11.7 | 120.0/1350.4 | 210.0/1440.4 | 0/0 | 10 | 0 |
| SLOW20 baseline | 103 / 720p | 9.8/11.9 | 785.2/1450.5 | 877.1/1538.7 | 0/0 | 40 | 1,310,720 |
| SLOW20 final | 102 / 480p | 9.8/11.9 | 80.0/636.8 | 170.2/725.0 | 0/0 | 10 | 0 |

### 验收计算

- NORMAL 三项 p95 均 0.0% 回归，低于 5% 门槛。
- SLOW05 首帧 p95 改善 77.6%，rebuffer 总时长改善 99.9%。
- SLOW10 首帧 p95 改善 55.5%，rebuffer 30→0。
- SLOW20 首帧 p95 改善 52.9%；原 baseline 无 rebuffer，final 仍为 0。
- 每档可避免重复字节 1,310,720→0；NORMAL 保持 720p，弱网下一条自动选择 360p/480p/480p。

## 4. 范围、取消与资源证据

- manager 单测证明 shifted snapshot prefix 足够时相同 range 的 `downloadFile` 调用为 0；不足/超时才回退准确的有界 offset/limit。
- prefix query 记录同 offset single-flight、timeout/cancel/late ignored、一次路径刷新；任意 seek 精确查询 5 MiB offset。
- test-only 快速反向模型取消数为 4；离线恢复和 logout 清理为 true；seek 为 0/5 MiB/1 MiB。
- 当前最大 request/read-ahead 4,194,304 bytes；NEXT 最大 262,144 bytes；最大 speculative file=1；ExoPlayer ledger=1。
- 模拟状态上限 65,536 bytes；本轮 JUnit JVM used heap 诊断快照为 11,289,840 bytes（受 GC/测试 JVM 影响，不作稳定验收门槛）。真实 Android PSS/heap：尚未验证。

## 5. 候选结论

| 候选 | 结论 | 证据 |
|---|---|---|
| A TDLib 任意 offset prefix | **保留** | 重复字节归零，manager 竞态/超时/seek/logout 测试通过。 |
| B TDLib progress 吞吐 + AUTO | **保留** | 弱网首帧/rebuffer 明确改善，NORMAL p95 无回归，显式质量测试通过。 |
| C `DefaultPreloadManager` | **拒绝** | 现有 factory 会走 CURRENT 4 MiB read-ahead，不能证明 NEXT≤256 KiB；未留下生产代码。 |

## 6. 适用限制

- 主机全量 `test` 为 977/977；`lint`、`assembleDebug`、instrumentation compile 和规定 Robolectric 均通过。API 36 AOSP x86_64 emulator Compose/UI 为 95/95。
- 这些数值是固定种子的 test-only 事件模型，不代表任何 CDN/SIM/Wi-Fi 的绝对承诺。
- 真实 Telegram/CDN、Android 网络栈、decoder 和 surface 的首帧数据：尚未验证。
- emulator UI 与完整主机 Proof 以最终阶段报告为准。
- 实体机：尚未验证（用户明确禁止实体机测试）。
