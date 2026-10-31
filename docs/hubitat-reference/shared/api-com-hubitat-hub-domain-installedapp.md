# Installed app

- **ID:** `api-com-hubitat-hub-domain-installedapp`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.InstalledApp`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `addModeRestriction` | `void addModeRestriction(Long modeId)` | Adds a mode id to this app's mode restriction list. Parameters: modeId - Mode database id to append; duplicate ids are retained. |
| `getAppTypeId` | `Long getAppTypeId()` | Returns the app type id from the stored id or associated app type. Returns: Stored app type id when non-zero, otherwise the associated app type id or null. |
| `getCleanLabelForDAO` | `String getCleanLabelForDAO()` | Returns the trimmed, length-limited label for persistence. Returns: Trimmed label, limited to 255 characters, or null when the stored label is blank. |
| `getDocumentationLink` | `String getDocumentationLink()` | Returns the documentation link from the associated app type, loading that type by id when needed. Returns: App type documentation URL; throws when the referenced app type cannot be |
| `getLabel` | `String getLabel()` | Returns the user-facing label, falling back to the app name. Returns: Label when set, otherwise the transient name; null if both are null. |
| `getName` | `String getName()` | Returns the app name, falling back to the associated app type name. Returns: Transient name when non-empty, otherwise the app type name or null. |
| `getTrueLabel` | `String getTrueLabel()` | Returns the stored label without the getLabel() fallback. Returns: Stored label, or null when no label was saved. |
| `isSingleThreaded` | `boolean isSingleThreaded()` | Reports whether the app type runs single-threaded. Returns: App type single-threaded flag, or false when no app type is associated. |
| `isSystemAppType` | `boolean isSystemAppType()` | Reports whether the associated app type is a system type. Returns: True when an associated app type is marked system; false when absent or not system. |
| `isUiTransactionInProgress` | `boolean isUiTransactionInProgress()` | Reports a pending UI transaction only for app types that require UI commit. Returns: True when the app type requires commit and this installation has a pending UI transaction. |
| `setAppType` | `void setAppType(AppType appType)` | Associates app type metadata with this installation. Parameters: appType - App type to store; null clears the relation without clearing the separate stored id. |
| `setLabel` | `void setLabel(String newLabel)` | Sets the app label using the same 255-character limit as updateLabel(String). Parameters: newLabel - Label to store, or null to clear the stored label. |
| `toMap` | `Map toMap()` | Creates the compact installed-app record used by app-list responses. Returns: Compact map with these entries; requires a non-null appType. id: Long installed app id. name: String l |
| `updateLabel` | `void updateLabel(String newLabel)` | Stores an app label, truncating values longer than 255 characters. Parameters: newLabel - Label to store, or null to clear the stored label; long values keep the first 252 characte |

## Properties

| Name | Type | Description |
|---|---|---|
| `appType` | `AppType` | App type metadata associated with this installation, when loaded. |
| `appTypeId` | `Long` | App type database id; may be derived from appType. |
| `disabled` | `boolean` | True when this installed app is disabled. |
| `id` | `Long` | Installed app database id. |
| `installed` | `boolean` | True after installation completes; false by default. |
| `label` | `String` | Optional user-facing app label; limited to 255 characters by updateLabel(String). |
| `name` | `String` | Transient app name; getName() falls back to the app type name. |
| `parentAppId` | `Long` | Parent installed app id, or null for a top-level app. |
| `restrictedModes` | `List<Long>` | Mode ids that restrict this app, initially empty. |
| `uiContext` | `boolean` | Transient flag set by the UI when the current request has app UI context. |
| `uiTransactionInProgress` | `boolean` | Whether this app type has an uncommitted UI transaction. |
| `version` | `Long` | Version counter stored for the installed app record. |
