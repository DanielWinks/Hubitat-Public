# Zigbee.ZigbeeCluster

- **ID:** `api-com-hubitat-zigbee-zigbee-zigbeecluster`
- **Section:** Protocols
- **Class:** `com.hubitat.zigbee.Zigbee$ZigbeeCluster`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCluster` | `ZigbeeCluster getCluster(Integer clusterInt)` | Looks up a cluster from its integer identifier. Parameters: clusterInt - cluster identifier as an integer Returns: matching cluster enum, or null when the identifier is unknown |
| `getCluster` | `ZigbeeCluster getCluster(String clusterHexString)` | Looks up a cluster from its hexadecimal identifier. Parameters: clusterHexString - cluster identifier text parsed as hexadecimal Returns: matching cluster enum, or null when the id |
| `getClusterEnum` | `String getClusterEnum()` | Returns this cluster's enum constant name. Returns: enum constant name |
| `getClusterInt` | `Integer getClusterInt()` | Returns this cluster's numeric identifier. Returns: cluster identifier as an Integer |
| `getClusterLabel` | `String getClusterLabel()` | Returns this cluster's human-readable label. Returns: cluster label |

## Properties

| Name | Type | Description |
|---|---|---|
| `ALARMS_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `ANALOG_INPUT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `ANALOG_OUTPUT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `ANALOG_VALUE_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `BALLAST_CONFIGURATION_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `BASIC_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `BINARY_INPUT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `BINARY_OUTPUT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `BINARY_VALUE_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `COLOR_CONTROL_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `DEHUMIDIFICATION_CONTROL_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `DEMAND_RESPONSE_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `DIAGNOSTICS_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `DOOR_LOCK_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `ELECTRICAL_MEASUREMENT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `FAN_CONTROL_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `FLOW_MEASUREMENT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `GROUPS_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `IAS_ACE_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `IAS_WD_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `IAS_ZONE_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `IDENTIFY_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `ILLUMINANCE_LEVEL_SENSING_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `ILLUMINANCE_MEASUREMENT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `KEY_ESTABLISHMENT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `LEVEL_CONTROL_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `MESSAGING_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `METER_IDENTIFICATION_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `METERING_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `MULTISTATE_INPUT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `MULTISTATE_OUTPUT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `MULTISTATE_VALUE_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `OCCUPANCY_SENSING_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `ON_OFF_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `ON_OFF_SWITCH_CONFIGURATION_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `OTA_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `POLL_CONTROL_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `POWER_CONFIGURATION_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `POWER_PROFILE_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `PRESSURE_MEASUREMENT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `PRICE_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `PUMP_CONFIGURATION_CONTROL_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `RSSI_LOCATION_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `SCENES_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `SHADE_CONFIGURATION_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `SOIL_MOISTURE_MEASUREMENT` | `Zigbee$ZigbeeCluster` | — |
| `TEMPERATURE_CONFIGURATION_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `TEMPERATURE_MEASUREMENT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `THERMOSTAT_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `THERMOSTAT_USER_INTERFACE_CONFIGURATION_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `TIME_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `TUNNELING_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
| `WINDOW_COVERING_CLUSTER` | `Zigbee$ZigbeeCluster` | — |
