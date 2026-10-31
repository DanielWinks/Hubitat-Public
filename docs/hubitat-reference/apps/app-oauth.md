# OAuth and tokens

- **ID:** `app-oauth`
- **Section:** Apps
- **Class:** `com.hubitat.hub.executor.AppExecutor`

> Use these methods directly in app code; the hub supplies this execution context.

## Inheritance

- Extends `com.hubitat.hub.executor.BaseExecutor`
- [HTTP requests](../shared/http.md)
- [HTTP requests](../shared/http.md)
- [Date and time](../shared/shared-date-time.md)
- [Hub files](../shared/shared-files.md)
- [Network utilities](../shared/shared-network.md)
- [Scheduling](../shared/shared-scheduling.md)
- [Shared utilities](../shared/shared-utilities.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `createAccessToken` | `String createAccessToken()` | Creates and stores an OAuth access token for this app, when OAuth is enabled. Returns: a UUID String stored as this app’s access token; throws RuntimeException when OAuth is disabl |
| `createAnotherAccessToken` | `String createAnotherAccessToken()` | Creates and stores an additional OAuth access token without replacing existing tokens. Returns: the newly generated UUID String, appended to the stored token list; throws RuntimeEx |
| `generateJavascriptRequestToken` | `String generateJavascriptRequestToken()` | Creates a JavaScript request token for the current installed app. Returns: the JavaScript request token String generated for this app id. |
| `getAccessTokens` | `List<String> getAccessTokens()` | Returns the stored OAuth access tokens as a list when OAuth is enabled. Returns: a List split from the stored comma-separated access tokens, or an empty list when none are stored;  |
| `isHubitatUISecurityEnabled` | `boolean isHubitatUISecurityEnabled()` | Returns whether Hubitat UI security is enabled. Returns: the Manager UI security enabled flag. |
| `isJavascriptRequestTokenValid` | `boolean isJavascriptRequestTokenValid(String token)` | Checks whether the supplied JavaScript request token belongs to the current installed app. Parameters: token - token to validate, revoke, or replace Returns: true when AppService a |
| `propagateJavascriptRequestTokenForMobileDevicesView` | `void propagateJavascriptRequestTokenForMobileDevicesView(String token)` | Propagates the supplied JavaScript request token to the mobile devices view. Parameters: token - token to validate, revoke, or replace |
| `resetSpecificAccessToken` | `void resetSpecificAccessToken(String token)` | Replaces the supplied token text with a newly generated token in the stored token string. Parameters: token - token to validate, revoke, or replace |
| `revokeAccessToken` | `void revokeAccessToken()` | Removes all stored access tokens for this app and clears cached bearer tokens. |
| `revokeSpecificAccessToken` | `void revokeSpecificAccessToken(String token)` | Removes the supplied token text from the stored access-token string and clears cached bearer tokens. Parameters: token - token to validate, revoke, or replace |
