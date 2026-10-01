# ZoneStatus

- **ID:** `api-hubitat-zigbee-clusters-iaszone-zonestatus`
- **Section:** Protocols
- **Class:** `hubitat.zigbee.clusters.iaszone.ZoneStatus`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `isAcSet` | `boolean isAcSet()` | Reports the ac set value stored in ac. Returns: ac set flag |
| `isAlarm1Set` | `boolean isAlarm1Set()` | Reports the alarm1 set value stored in alarm1. Returns: alarm1 set flag |
| `isAlarm2Set` | `boolean isAlarm2Set()` | Reports the alarm2 set value stored in alarm2. Returns: alarm2 set flag |
| `isBatteryDefectSet` | `boolean isBatteryDefectSet()` | Reports the battery defect set value stored in batteryDefect. Returns: battery defect set flag |
| `isBatterySet` | `boolean isBatterySet()` | Reports the battery set value stored in battery. Returns: battery set flag |
| `isRestoreReportsSet` | `boolean isRestoreReportsSet()` | Reports the restore reports set value stored in restoreReports. Returns: restore reports set flag |
| `isSupervisionReportsSet` | `boolean isSupervisionReportsSet()` | Reports the supervision reports set value stored in supervisionReports. Returns: supervision reports set flag |
| `isTamperSet` | `boolean isTamperSet()` | Reports the tamper set value stored in tamper. Returns: tamper set flag |
| `isTestSet` | `boolean isTestSet()` | Reports the test set value stored in test. Returns: test set flag |
| `isTroubleSet` | `boolean isTroubleSet()` | Reports the trouble set value stored in trouble. Returns: trouble set flag |
| `ZoneStatus` | `ZoneStatus(int zonestatus)` | Decodes the ten supported IAS Zone status flags from a bit field. Parameters: zonestatus - integer whose bits 0 through 9 populate the named status flags. |

## Properties

| Name | Type | Description |
|---|---|---|
| `ac` | `int` | — |
| `alarm1` | `int` | — |
| `alarm2` | `int` | — |
| `battery` | `int` | — |
| `batteryDefect` | `int` | — |
| `restoreReports` | `int` | — |
| `supervisionReports` | `int` | — |
| `tamper` | `int` | — |
| `test` | `int` | — |
| `trouble` | `int` | — |
