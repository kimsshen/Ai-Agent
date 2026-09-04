## DOC:push-log-fields|ALL|data-dictionary|push_log 字段说明
# push_log 告警日志字段

- `channel`：消息通道，在当前业务中代表车间或工序。
- `exception_name`：告警类型或异常名称。
- `msg`：告警内容，描述具体异常信息。
- `creation_date`：告警创建时间，用于时间范围过滤和发生时间分析，格式为 `yyyy-MM-dd HH:mm:ss.SSS`。

统计时应以数据库中的原始记录为准，不能根据告警内容猜测数量。
---
## DOC:push-log-statistics|ALL|analysis-rule|push_log 告警统计规则
# 告警统计规则

给定消息通道和时间范围后，按照 `channel + exception_name + msg` 分组统计。
输出总告警数、每种告警类型和告警内容的发生次数、首次创建时间和最后创建时间。
时间范围使用左闭右开区间：`creation_date >= from AND creation_date < to`。
没有匹配记录时，应明确说明该通道在指定时间范围内没有告警记录。
