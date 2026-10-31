# Matter.MatterCluster

- **ID:** `api-com-hubitat-matter-matter-mattercluster`
- **Section:** Protocols
- **Class:** `com.hubitat.matter.Matter$MatterCluster`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCluster` | `MatterCluster getCluster(Integer clusterInt)` | Returns the enum class that defines this helper's cluster constants. Parameters: clusterInt - integer cluster identifier Returns: matching cluster enum constant, or null when no co |
| `getCluster` | `MatterCluster getCluster(String clusterHexString)` | Returns the enum class that defines this helper's cluster constants. Parameters: clusterHexString - hexadecimal cluster identifier Returns: matching cluster enum constant, or null  |
| `getClusterEnum` | `String getClusterEnum()` | Returns this cluster enum value name. Returns: enum constant name |
| `getClusterInt` | `Integer getClusterInt()` | Returns the integer identifier assigned to this cluster enum value. Returns: integer cluster identifier stored by this enum constant |
| `getClusterLabel` | `String getClusterLabel()` | Returns the display label assigned to this cluster enum value. Returns: display label stored by this enum constant |

## Properties

| Name | Type | Description |
|---|---|---|
| `ALARMS_CLUSTER` | `Matter$MatterCluster` | — |
| `ANALOG_INPUT_CLUSTER` | `Matter$MatterCluster` | — |
| `ANALOG_OUTPUT_CLUSTER` | `Matter$MatterCluster` | — |
| `ANALOG_VALUE_CLUSTER` | `Matter$MatterCluster` | — |
| `BALLAST_CONFIGURATION_CLUSTER` | `Matter$MatterCluster` | — |
| `BASIC_CLUSTER` | `Matter$MatterCluster` | — |
| `BINARY_INPUT_CLUSTER` | `Matter$MatterCluster` | — |
| `BINARY_OUTPUT_CLUSTER` | `Matter$MatterCluster` | — |
| `BINARY_VALUE_CLUSTER` | `Matter$MatterCluster` | — |
| `BOOLEAN_STATE_CLUSTER` | `Matter$MatterCluster` | — |
| `BRIDGED_DEVICE_BASIC_INFORMATION_CLUSTER` | `Matter$MatterCluster` | — |
| `COLOR_CONTROL_CLUSTER` | `Matter$MatterCluster` | — |
| `DEHUMIDIFICATION_CONTROL_CLUSTER` | `Matter$MatterCluster` | — |
| `DEMAND_RESPONSE_CLUSTER` | `Matter$MatterCluster` | — |
| `DESCRIPTOR_CLUSTER` | `Matter$MatterCluster` | — |
| `DIAGNOSTICS_CLUSTER` | `Matter$MatterCluster` | — |
| `DOOR_LOCK_CLUSTER` | `Matter$MatterCluster` | — |
| `ELECTRICAL_MEASUREMENT_CLUSTER` | `Matter$MatterCluster` | — |
| `FAN_CONTROL_CLUSTER` | `Matter$MatterCluster` | — |
| `FLOW_MEASUREMENT_CLUSTER` | `Matter$MatterCluster` | — |
| `GROUPS_CLUSTER` | `Matter$MatterCluster` | — |
| `IAS_ACE_CLUSTER` | `Matter$MatterCluster` | — |
| `IAS_WD_CLUSTER` | `Matter$MatterCluster` | — |
| `IAS_ZONE_CLUSTER` | `Matter$MatterCluster` | — |
| `IDENTIFY_CLUSTER` | `Matter$MatterCluster` | — |
| `ILLUMINANCE_LEVEL_SENSING_CLUSTER` | `Matter$MatterCluster` | — |
| `ILLUMINANCE_MEASUREMENT_CLUSTER` | `Matter$MatterCluster` | — |
| `KEY_ESTABLISHMENT_CLUSTER` | `Matter$MatterCluster` | — |
| `LEVEL_CONTROL_CLUSTER` | `Matter$MatterCluster` | — |
| `MESSAGING_CLUSTER` | `Matter$MatterCluster` | — |
| `METER_IDENTIFICATION_CLUSTER` | `Matter$MatterCluster` | — |
| `METERING_CLUSTER` | `Matter$MatterCluster` | — |
| `MULTISTATE_INPUT_CLUSTER` | `Matter$MatterCluster` | — |
| `MULTISTATE_OUTPUT_CLUSTER` | `Matter$MatterCluster` | — |
| `MULTISTATE_VALUE_CLUSTER` | `Matter$MatterCluster` | — |
| `OCCUPANCY_SENSING_CLUSTER` | `Matter$MatterCluster` | — |
| `ON_OFF_CLUSTER` | `Matter$MatterCluster` | — |
| `ON_OFF_SWITCH_CONFIGURATION_CLUSTER` | `Matter$MatterCluster` | — |
| `OTA_CLUSTER` | `Matter$MatterCluster` | — |
| `POLL_CONTROL_CLUSTER` | `Matter$MatterCluster` | — |
| `POWER_CONFIGURATION_CLUSTER` | `Matter$MatterCluster` | — |
| `POWER_PROFILE_CLUSTER` | `Matter$MatterCluster` | — |
| `POWER_SOURCE_CLUSTER` | `Matter$MatterCluster` | — |
| `PRESSURE_MEASUREMENT_CLUSTER` | `Matter$MatterCluster` | — |
| `PRICE_CLUSTER` | `Matter$MatterCluster` | — |
| `PUMP_CONFIGURATION_CONTROL_CLUSTER` | `Matter$MatterCluster` | — |
| `RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER` | `Matter$MatterCluster` | — |
| `RSSI_LOCATION_CLUSTER` | `Matter$MatterCluster` | — |
| `SCENES_CLUSTER` | `Matter$MatterCluster` | — |
| `SHADE_CONFIGURATION_CLUSTER` | `Matter$MatterCluster` | — |
| `SOIL_MOISTURE_MEASUREMENT` | `Matter$MatterCluster` | — |
| `SWITCH_CLUSTER` | `Matter$MatterCluster` | — |
| `TEMPERATURE_CONFIGURATION_CLUSTER` | `Matter$MatterCluster` | — |
| `TEMPERATURE_MEASUREMENT_CLUSTER` | `Matter$MatterCluster` | — |
| `THERMOSTAT_CLUSTER` | `Matter$MatterCluster` | — |
| `THERMOSTAT_USER_INTERFACE_CONFIGURATION_CLUSTER` | `Matter$MatterCluster` | — |
| `TIME_CLUSTER` | `Matter$MatterCluster` | — |
| `TUNNELING_CLUSTER` | `Matter$MatterCluster` | — |
| `WINDOW_COVERING_CLUSTER` | `Matter$MatterCluster` | — |
