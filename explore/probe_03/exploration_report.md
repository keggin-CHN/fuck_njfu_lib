# Probe 03 深度协议探索报告

- 目标URL: `https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1/?vpn-0#/ic/home`
- 探测时间: `2026-03-01 13:04:54`
- 学号: `2410403132`
- 统一认证密码(掩码): `Zh*************20`
- 图书馆密码(掩码): `nj*******x!`

## 1) 登录链路结论

1. 访问 WebVPN / 目标页，初始化 cookie。
2. 调用 `/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin` 获取 `route`。
3. 打开统一认证登录页，解析隐藏字段：`lt`、`pwdDefaultEncryptSalt`、`execution`、`_eventId` 等。
4. 通过 `authserver/needCaptcha.html` 检查验证码要求。
5. CAS 登录 POST 成功后 302，`Location` 中拿到 `ticket`。
6. 请求 `/rump_frontend/loginFromCas/?ticket=...` 完成 WebVPN-CAS 票据跳转。
7. 调用 `ic-web/login/publicKey` 获取 RSA 公钥与 nonce。
8. 图书馆登录 `ic-web/login/user`，拿到 `token` + `accNo`。
9. 携带 `token` 访问 `ic-web/reserve` / `ic-web/reserve/resvInfo` 完成预约数据查询。

## 2) 座位查询统计（今日）

| 区域 | 总数 | 占用 | 可用 |
|---|---:|---:|---:|
| 二层A区 | 441 | 1 | 440 |
| 二层B区 | 96 | 0 | 96 |
| 三层A区 | 404 | 2 | 402 |
| 三层B区 | 132 | 0 | 132 |
| 三层C区 | 162 | 1 | 161 |
| 三楼夹层 | 20 | 0 | 20 |
| 四层A区 | 428 | 0 | 428 |
| 四层夹层 | 24 | 0 | 24 |
| 五层A区 | 360 | 0 | 360 |
| 六层A区 | 344 | 0 | 344 |
| 七层北侧 | 224 | 0 | 224 |
| 七层南侧 | 114 | 0 | 114 |

## 3) 字段全量目录（结构层）

- seat 顶层字段数: `42`
- resvInfo 字段数: `10`
- resvRule 字段数: `43`

### seat 顶层字段
```json
[
  "addServices",
  "campusId",
  "coordinate",
  "deadlineTime",
  "devId",
  "devName",
  "devProp",
  "devSn",
  "devStatus",
  "deviceAttributes",
  "endDayOpenInfo",
  "icon",
  "kindId",
  "kindName",
  "kindProp",
  "kindUrl",
  "labId",
  "labName",
  "labProp",
  "maintenanceTime",
  "maxUser",
  "minUser",
  "msideCoordinate",
  "onlyView",
  "openEnd",
  "openRulesn",
  "openStart",
  "openState",
  "openTimes",
  "pointProperty",
  "pointSize",
  "resvInfo",
  "resvMemo",
  "resvRule",
  "roomId",
  "roomName",
  "roomProp",
  "roomSn",
  "startDayOfWeek",
  "textSize",
  "timeScopeOpenInfo",
  "usePersonType"
]
```

### resvInfo 字段
```json
[
  "devId",
  "endTime",
  "logonName",
  "resvEndRealTime",
  "resvId",
  "resvStatus",
  "startTime",
  "title",
  "trueName",
  "uuid"
]
```

### resvRule 字段
```json
[
  "agreeWaitTime",
  "allowConflict",
  "cancelTime",
  "classKind",
  "deadlineTime",
  "defaultMode",
  "defaultRatio",
  "defaultValue",
  "deptId",
  "devId",
  "devKindList",
  "devRoomList",
  "devType",
  "earliestResvTime",
  "earlyInTime",
  "endMode",
  "freezingTime",
  "gmtCreate",
  "gmtModified",
  "groupId",
  "ident",
  "laterLineTime",
  "latestResvTime",
  "limit",
  "maxResvTime",
  "memo",
  "minResvTime",
  "priority",
  "prohibitConsecutiveDay",
  "prohibtOperationTime",
  "rangeNum",
  "resvAfterNoticeTime",
  "resvBeforeNoticeTime",
  "resvEndNewTime",
  "resvEndNoticeTime",
  "ruleId",
  "ruleName",
  "sendForThirdPartyTime",
  "seriesTimeLimit",
  "startDayOfWeek",
  "timeInterval",
  "useDuration",
  "uuid"
]
```

## 4) 产物文件

- `probe_03/step1_target_page.html`
- `probe_03/step2_cas_login_page.html`
- `probe_03/network_trace.json`
- `probe_03/summary.json`
- `probe_03/all_areas_stats_today.json`
- `probe_03/my_reservations_today_tomorrow.json`
- `probe_03/sample_occupied_seat.json`
- `probe_03/sample_available_seat.json`
- `probe_03/fields_catalog.json`
