# Sonos Advanced Controller app state

Inventory of the states present on main app instance **126** in the supplied C7 export. Sizes are UTF-8 bytes of each state value encoded as compact JSON (including string quotes); they exclude the state key and export metadata.

| State | Size | Purpose and use |
|---|---:|---|
| `logLevel` | 7 B | Last applied logging level (`trace` in this export). Compared with the selected setting to detect changes and record a level-change message. The actual preference is `settings.logLevel`. |
| `alarmManagementPreferencesApplied` | 34 B | Remembers whether alarm manager and alarm switch features were enabled the last time they were configured. Used to detect preference changes and avoid unnecessary device setup. |
| `staticDriverActivePlayerRoster` | 416 B | Cached roster of six active Sonos player IDs and device network IDs, with refresh time and schema version. Published to player devices so the static driver can identify active players and request app work. |
| `clonedName` | 27 B | Hubitat clone/rename metadata (`Sonos Advanced Controller`). It is not read or written by the app source. |

**Total:** 484 B of encoded values. This is a snapshot of states present in the export, not every key the app can create at runtime; transient queues and update-check state may be empty and therefore absent. App settings are not included.
