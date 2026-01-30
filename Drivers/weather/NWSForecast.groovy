/**
 *  MIT License
 *  Copyright 2023 Daniel Winks (daniel.winks@gmail.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

import hubitat.scheduling.AsyncResponse
import groovy.json.JsonOutput
import groovy.transform.CompileStatic

/**
 * ==============================================================================
 * National Weather Service (NWS) Forecast Driver for Hubitat
 * ==============================================================================
 *
 * OVERVIEW:
 * This driver connects to the National Weather Service API (weather.gov) to
 * retrieve weather forecasts for a specific geographic location. It provides
 * both detailed text forecasts and hourly forecast data that can be used in
 * Hubitat automations, dashboards, and rules.
 *
 * KEY FEATURES:
 * - Fetches detailed forecast text from NWS API
 * - Provides current conditions (temperature, humidity, wind, etc.)
 * - Optionally retrieves hourly forecasts (up to 36 hours ahead)
 * - Calculates high/low temperatures for the next 12 hours
 * - Automatic scheduled updates (configurable time)
 * - Handles unit conversions (Celsius to Fahrenheit)
 *
 * HOW IT WORKS:
 * 1. User provides latitude/longitude coordinates
 * 2. Driver queries NWS API to get forecast endpoint URLs for that location
 * 3. Driver fetches forecast data on a scheduled basis (user configurable)
 * 4. Weather data is parsed and stored in device attributes
 * 5. Hourly forecasts are optionally fetched every hour (if enabled)
 *
 * NWS API DOCUMENTATION:
 * https://www.weather.gov/documentation/services-web-api
 *
 * ==============================================================================
 */

// =============================================================================
// IMPORTS AND LIBRARIES
// =============================================================================
// Include shared utilities library (provides logging helpers: logDebug, logInfo, etc.)
#include dwinks.UtilitiesAndLoggingLibrary


// =============================================================================
// CONSTANTS
// =============================================================================
// Base URL for the National Weather Service API
// All API requests are made to this domain
@Field static final String NWS_API_BASE_URL = "https://api.weather.gov"


// =============================================================================
// DRIVER METADATA DEFINITION
// =============================================================================
// The metadata block defines the driver's identity, capabilities, attributes,
// commands, and user preferences.
metadata {
  definition(
    name: "NWS Forecast And Alerts",    // Display name shown in Hubitat
    namespace: "dwinks",                // Namespace prevents conflicts with other drivers
    author: "Daniel Winks",             // Author information
    importUrl: "https://raw.githubusercontent.com/DanielWinks/Hubitat/main/drivers/weather/NWSForecast.groovy",
    singleThreaded: true                // Ensures only one thread executes at a time
  ) {
    // ---------------------------------------------------------------------------
    // CAPABILITIES
    // ---------------------------------------------------------------------------
    // Standard Hubitat capabilities this driver implements
    capability "Sensor"    // Indicates this device can sense/report values
    capability "Refresh"   // Adds refresh() command to manually update data

    // ---------------------------------------------------------------------------
    // CURRENT CONDITIONS ATTRIBUTES
    // ---------------------------------------------------------------------------
    // These attributes store the current weather conditions from the forecast

    attribute "temperature", "NUMBER"               // Current temperature (°F or °C)
    attribute "temperatureHi", "NUMBER"             // High temperature for next 12 hours
    attribute "temperatureLo", "NUMBER"             // Low temperature for next 12 hours
    attribute "probabilityOfPrecipitation", "NUMBER" // Chance of precipitation (%)
    attribute "relativeHumidity", "NUMBER"          // Relative humidity (%)
    attribute "dewpoint", "NUMBER"                  // Dewpoint temperature
    attribute "detailedForecast", "STRING"          // Full text forecast description
    attribute "windSpeed", "STRING"                 // Wind speed (e.g., "10 mph")
    attribute "windDirection", "STRING"             // Wind direction (e.g., "NW", "SE")
    attribute "generatedAt", "DATE"                 // When the forecast was generated by NWS
    attribute "zoneId", "STRING"                    // NWS zone ID for alerts (e.g., OHZ065)
    attribute "alertsJson", "JSON_OBJECT"           // Active alerts from NWS (JSON)
    attribute "alertsFriendlyText", "STRING"       // Human-friendly alert summary text

    // ---------------------------------------------------------------------------
    // HOURLY FORECAST ATTRIBUTES
    // ---------------------------------------------------------------------------
    // These JSON attributes store hourly forecast data
    // Each attribute contains: temperature, wind, precipitation chance, etc.
    // Users can configure how many hours to retrieve (0-36 hours)

    attribute "forecast1h", "JSON_OBJECT"   // 1 hour from now
    attribute "forecast2h", "JSON_OBJECT"   // 2 hours from now
    attribute "forecast3h", "JSON_OBJECT"   // 3 hours from now
    attribute "forecast4h", "JSON_OBJECT"   // 4 hours from now
    attribute "forecast5h", "JSON_OBJECT"   // 5 hours from now
    attribute "forecast6h", "JSON_OBJECT"   // 6 hours from now
    attribute "forecast7h", "JSON_OBJECT"   // 7 hours from now
    attribute "forecast8h", "JSON_OBJECT"   // 8 hours from now
    attribute "forecast9h", "JSON_OBJECT"   // 9 hours from now
    attribute "forecast10h", "JSON_OBJECT"  // 10 hours from now
    attribute "forecast11h", "JSON_OBJECT"  // 11 hours from now
    attribute "forecast12h", "JSON_OBJECT"  // 12 hours from now
    attribute "forecast13h", "JSON_OBJECT"  // 13 hours from now
    attribute "forecast14h", "JSON_OBJECT"  // 14 hours from now
    attribute "forecast15h", "JSON_OBJECT"  // 15 hours from now
    attribute "forecast16h", "JSON_OBJECT"  // 16 hours from now
    attribute "forecast17h", "JSON_OBJECT"  // 17 hours from now
    attribute "forecast18h", "JSON_OBJECT"  // 18 hours from now
    attribute "forecast19h", "JSON_OBJECT"  // 19 hours from now
    attribute "forecast20h", "JSON_OBJECT"  // 20 hours from now
    attribute "forecast21h", "JSON_OBJECT"  // 21 hours from now
    attribute "forecast22h", "JSON_OBJECT"  // 22 hours from now
    attribute "forecast23h", "JSON_OBJECT"  // 23 hours from now
    attribute "forecast24h", "JSON_OBJECT"  // 24 hours from now
    attribute "forecast25h", "JSON_OBJECT"  // 25 hours from now
    attribute "forecast26h", "JSON_OBJECT"  // 26 hours from now
    attribute "forecast27h", "JSON_OBJECT"  // 27 hours from now
    attribute "forecast28h", "JSON_OBJECT"  // 28 hours from now
    attribute "forecast29h", "JSON_OBJECT"  // 29 hours from now
    attribute "forecast30h", "JSON_OBJECT"  // 30 hours from now
    attribute "forecast31h", "JSON_OBJECT"  // 31 hours from now
    attribute "forecast32h", "JSON_OBJECT"  // 32 hours from now
    attribute "forecast33h", "JSON_OBJECT"  // 33 hours from now
    attribute "forecast34h", "JSON_OBJECT"  // 34 hours from now
    attribute "forecast35h", "JSON_OBJECT"  // 35 hours from now
    attribute "forecast36h", "JSON_OBJECT"  // 36 hours from now

    // ---------------------------------------------------------------------------
    // CUSTOM COMMANDS
    // ---------------------------------------------------------------------------
    // Additional commands beyond standard capabilities

    command "initialize"                // Initializes/configures the driver
    command "refreshCurrentConditions"  // Updates detailed forecast only
    command "refreshHourlyForecasts"    // Updates hourly forecast data only
  }

  // ---------------------------------------------------------------------------
  // USER PREFERENCES (Configuration Options)
  // ---------------------------------------------------------------------------
  // These settings appear in the device configuration page
  section() {
    // Geographic location for weather data
    // Defaults to hub's location if available
    input(
      name: "latitude",
      type: "text",
      title: "Latitude",
      description: "Latitude coordinate (e.g., 40.7128)",
      required: true,
      defaultValue: location.latitude
    )

    input(
      name: "longitude",
      type: "text",
      title: "Longitude",
      description: "Longitude coordinate (e.g., -74.0060)",
      required: true,
      defaultValue: location.longitude
    )

    // When to update forecast data daily
    input(
      name: "dailyUpdateHour",
      type: "enum",
      title: "Daily Update Time",
      description: "What hour to perform the daily scheduled update",
      required: true,
      options: [
        [1: "1:00 AM"],
        [2: "2:00 AM"],
        [3: "3:00 AM"],
        [4: "4:00 AM"],
        [5: "5:00 AM"],
        [6: "6:00 AM"]
      ],
      defaultValue: 4
    )

    // How many hours of hourly forecast to retrieve
    // Set to 0 to disable hourly forecasts
    input(
      name: "numberOfHourlyForecasts",
      type: "enum",
      title: "Hourly Forecast Duration",
      description: "How many hours of hourly forecast to retrieve (0 = disabled)",
      required: true,
      options: [
        [0: "None (Disabled)"],
        [2: "2 Hours"],
        [4: "4 Hours"],
        [6: "6 Hours"],
        [8: "8 Hours"],
        [12: "12 Hours"],
        [18: "18 Hours"],
        [24: "24 Hours"],
        [36: "36 Hours"]
      ],
      defaultValue: 0
    )

    // Optional: enable active weather alerts
    input(
      name: "enableAlerts",
      type: "bool",
      title: "Enable Active Alerts",
      description: "Fetch active NWS alerts for the forecast zone",
      required: false,
      defaultValue: false
    )

    input(
      name: "alertsRefreshMinutes",
      type: "enum",
      title: "Alerts Refresh Interval",
      description: "How often to refresh active alerts (minutes)",
      required: false,
      defaultValue: 15,
      options: [
        [5: "5 Minutes"],
        [10: "10 Minutes"],
        [15: "15 Minutes"],
        [30: "30 Minutes"],
        [60: "60 Minutes"]
      ]
    )
  }
}


// =============================================================================
// HUBITAT LIFECYCLE METHODS
// =============================================================================
// These methods are called automatically by Hubitat at specific points in the
// driver's lifecycle (install, update, initialize, etc.)

/**
 * Called when the device needs to be initialized (e.g., after hub restart).
 * Hubitat calls this automatically when the hub boots up.
 * Delegates to configure() for full setup.
 */
void initialize() {
  configure()
}

/**
 * Configures the device by clearing old data, fetching API endpoints,
 * and setting up scheduled updates.
 * Called during installation, updates, and initialization.
 */
void configure() {
  // Remove any existing scheduled tasks to prevent duplicates
  unschedule()

  // Clear any stale state data and device attributes
  clearAllStates()

  // Fetch the NWS API endpoint URLs for this location
  // This queries the NWS API to get forecast URLs specific to the lat/long
  fetchNWSForecastEndpoints()

  // Set up automatic scheduled refreshes
  setupAutomaticRefreshSchedules()
}

/**
 * Manually triggers a refresh of all weather data.
 * This is called by the Refresh capability and can be triggered from
 * dashboards, rules, or manually by the user.
 */
void refresh() {
  // Ensure forecast endpoints are available before attempting refresh
  if (!state.forecastUri || !state.forecastHourlyUri) {
    logWarn("Forecast endpoints not initialized. Fetching endpoints before refresh.")
    fetchNWSForecastEndpoints()
    return
  }

  // Always refresh the detailed current conditions forecast
  refreshCurrentConditions()

  // Only refresh hourly forecasts if the user has enabled them
  Integer hoursToFetch = settings.numberOfHourlyForecasts as Integer
  if (hoursToFetch > 0) {
    refreshHourlyForecasts()
  }

  // Optionally refresh active alerts
  if (settings.enableAlerts) {
    refreshAlerts()
  }
}


// =============================================================================
// REFRESH COMMAND METHODS
// =============================================================================
// These methods allow selective refresh of different forecast types.
// They can be called manually or from rules/automations.

/**
 * Refreshes the current conditions and detailed text forecast.
 * This fetches the primary forecast data including:
 * - Current temperature
 * - Detailed forecast text
 * - Wind speed and direction
 * - Humidity and dewpoint
 * - Precipitation probability
 * Also calculates high/low temperatures for the next 12 hours.
 */
void refreshCurrentConditions() {
  fetchDetailedForecastFromNWS()
  calculateHighAndLowTemperatures()
}

/**
 * Refreshes the hourly forecast data.
 * This fetches hour-by-hour forecast details based on the user's
 * configured number of hours to retrieve.
 */
void refreshHourlyForecasts() {
  fetchHourlyForecastsFromNWS()
}

/**
 * Refreshes active NWS alerts for the forecast zone.
 * Requires a valid zoneId, which is derived from the /points endpoint.
 */
void refreshAlerts() {
  if (!settings.enableAlerts) {
    return
  }

  if (!state.zoneId) {
    logWarn("Zone ID not available. Fetching endpoints first.")
    fetchNWSForecastEndpoints()
    return
  }

  fetchActiveAlertsForZone()
}


// =============================================================================
// SCHEDULING METHODS
// =============================================================================
// These methods set up automatic scheduled refreshes of weather data.

/**
 * Sets up automatic scheduled refreshes based on user preferences.
 * Creates two types of schedules:
 * 1. Daily update at the user-configured hour (refreshes all data)
 * 2. Hourly updates at :55 past each hour (only if hourly forecasts enabled)
 */
void setupAutomaticRefreshSchedules() {
  // Remove any existing schedules first to prevent duplicates
  unschedule()

  // Schedule the daily comprehensive refresh
  // Cron format: "seconds minutes hours dayOfMonth month dayOfWeek"
  // "0 0 4 ? * *" means: at second 0, minute 0, hour 4 (4:00 AM), every day
  String dailyUpdateCron = "0 0 ${settings.dailyUpdateHour} ? * *"
  schedule(dailyUpdateCron, refresh)
  logDebug("Scheduled daily refresh at ${settings.dailyUpdateHour}:00 AM")

  // If hourly forecasts are enabled, also schedule hourly updates
  Integer hoursToFetch = settings.numberOfHourlyForecasts as Integer
  if (hoursToFetch > 0) {
    // Schedule at :55 past each hour to get fresh hourly data
    // This runs at 12:55, 1:55, 2:55, etc.
    String hourlyCron = "0 55 * ? * *"
    schedule(hourlyCron, "fetchHourlyForecastsFromNWS")
    logDebug("Scheduled hourly forecast refresh at :55 past each hour")
  }

  // If alerts are enabled, schedule independent alerts refresh
  if (settings.enableAlerts) {
    Integer intervalMinutes = settings.alertsRefreshMinutes as Integer
    if (intervalMinutes == null || intervalMinutes <= 0) {
      intervalMinutes = 15
    }
    String alertsCron = runEveryCustomMinutes(intervalMinutes)
    schedule(alertsCron, "refreshAlerts")
    logDebug("Scheduled alerts refresh every ${intervalMinutes} minutes")
  }
}


// =============================================================================
// NWS API ENDPOINT DISCOVERY
// =============================================================================
// These methods query the NWS API to get the correct forecast URLs for
// the user's configured latitude/longitude.

/**
 * Extracts the zone ID from an NWS zone URL.
 * Example: https://api.weather.gov/zones/forecast/OHZ065 -> OHZ065
 *
 * @param zoneUrl The NWS zone URL
 * @return The zone ID, or null if not found
 */
@CompileStatic
private String extractZoneIdFromUrl(String zoneUrl) {
  if (!zoneUrl) {
    return null
  }
  List parts = zoneUrl.tokenize('/')
  return parts ? parts[-1] : null
}

/**
 * Logs detailed information about an HTTP response for debugging.
 * Includes status, headers (if available), and a truncated response body.
 *
 * @param response     The async HTTP response
 * @param contextLabel Friendly label used in log messages
 * @param requestUrl   The URL that was requested (optional)
 */
private void logNwsHttpResponseDetails(AsyncResponse response, String contextLabel, String requestUrl = null) {
  Integer statusCode = getHttpStatusCode(response)
  String statusText = response?.status?.toString()
  String urlText = requestUrl ? " URL=${requestUrl}" : ""

  logDebug("NWS ${contextLabel} response received.${urlText} status=${statusText ?: 'unknown'} (code=${statusCode ?: 'unknown'})")

  // Log headers if available
  try {
    Object headers = response?.headers
    if (headers != null) {
      logTrace("NWS ${contextLabel} response headers: ${headers}")
    }
  } catch (Exception e) {
    logTrace("NWS ${contextLabel} response headers not available: ${e.message}")
  }

  // Log body length and a truncated preview to avoid huge logs
  try {
    String bodyText = response?.getData()?.toString()
    Integer bodyLength = bodyText?.length() ?: 0
    logDebug("NWS ${contextLabel} response body length: ${bodyLength}")

    if (bodyText) {
      Integer previewLength = Math.min(bodyText.length(), 2000)
      String preview = bodyText.substring(0, previewLength)
      logTrace("NWS ${contextLabel} response body preview (first ${previewLength} chars): ${preview}")
    }
  } catch (Exception e) {
    logTrace("NWS ${contextLabel} response body not available: ${e.message}")
  }
}

/**
 * Safely parses JSON from an AsyncResponse.
 * If the response is empty or non-JSON, schedules a retry using the
 * shared retry logic and returns null.
 *
 * @param response        The async HTTP response
 * @param retryMethodName The retry method to schedule on parse failure
 * @param retryStateKey   State key for tracking retries
 * @param contextLabel    Friendly label used in log messages
 * @param requestUrl      The URL that was requested (optional)
 * @return Map parsed JSON data, or null if parsing failed
 */
private Map parseNwsJsonResponse(
    AsyncResponse response,
    String retryMethodName,
    String retryStateKey,
    String contextLabel,
    String requestUrl = null
) {
  // Log full response details for debugging/trace
  logNwsHttpResponseDetails(response, contextLabel, requestUrl)

  Object data = response?.getData()

  // If response data is already a Map, return as-is
  if (data instanceof Map) {
    return data as Map
  }

  // Convert to string for parsing (handle byte[] bodies)
  String responseText = null
  if (data instanceof byte[]) {
    responseText = new String((byte[]) data, 'UTF-8')
  } else {
    responseText = data?.toString()
  }
  String trimmedResponse = responseText?.trim()

  // Guard against empty or non-JSON responses
  if (!trimmedResponse) {
    handleAsyncHttpFailureWithRetry(
      response,
      retryMethodName,
      retryStateKey,
      DEFAULT_HTTP_RETRY_DELAYS_SECONDS,
      DEFAULT_MAX_HTTP_RETRY_ATTEMPTS,
      "Empty response from NWS ${contextLabel} endpoint"
    )
    return null
  }

  if (!(trimmedResponse.startsWith('{') || trimmedResponse.startsWith('['))) {
    // Attempt Base64 decode if response looks like Base64 data
    String base64Candidate = trimmedResponse
    if (base64Candidate && base64Candidate ==~ /[A-Za-z0-9+\/\r\n=]+/) {
      try {
        byte[] decodedBytes = base64Candidate.decodeBase64()
        String decodedText = new String(decodedBytes, 'UTF-8').trim()
        logTrace("NWS ${contextLabel} response appeared Base64-encoded; decoded preview: ${decodedText.take(500)}")

        if (decodedText.startsWith('{') || decodedText.startsWith('[')) {
          trimmedResponse = decodedText
        } else {
          logWarn("NWS ${contextLabel} response Base64 decode did not produce JSON")
        }
      } catch (Exception e) {
        logWarn("Failed to Base64-decode NWS ${contextLabel} response: ${e.message}")
      }
    }
  }

  if (!(trimmedResponse.startsWith('{') || trimmedResponse.startsWith('['))) {
    handleAsyncHttpFailureWithRetry(
      response,
      retryMethodName,
      retryStateKey,
      DEFAULT_HTTP_RETRY_DELAYS_SECONDS,
      DEFAULT_MAX_HTTP_RETRY_ATTEMPTS,
      "Unexpected non-JSON response from NWS ${contextLabel} endpoint"
    )
    return null
  }

  try {
    return parseJson(trimmedResponse) as Map
  } catch (Exception e) {
    handleAsyncHttpFailureWithRetry(
      response,
      retryMethodName,
      retryStateKey,
      DEFAULT_HTTP_RETRY_DELAYS_SECONDS,
      DEFAULT_MAX_HTTP_RETRY_ATTEMPTS,
      "Failed to parse JSON from NWS ${contextLabel} endpoint: ${e.message}"
    )
    return null
  }
}

/**
 * Fetches the NWS API forecast endpoint URLs for the configured location.
 * The NWS API uses a two-step process:
 * 1. Query /points/{lat},{lon} to get metadata about the location
 * 2. Use the returned URLs to fetch actual forecast data
 *
 * This method performs step 1 and stores the forecast URLs in state.
 * These URLs are then used by other methods to fetch forecast data.
 */
private void fetchNWSForecastEndpoints() {
  logDebug("Fetching NWS forecast endpoints for location...")

  // Build the API request to get location metadata
  String apiEndpoint = "${NWS_API_BASE_URL}/points/${settings.latitude},${settings.longitude}"

  Map httpParams = [:]
  httpParams.uri = apiEndpoint

  // Store params for retry attempts
  state.nwsEndpointsHttpParams = httpParams

  // Reset retry counter for fresh request
  resetHttpRetryCounter('nwsEndpointsRetryCount')

  // Make asynchronous HTTP GET request to the NWS API
  asynchttpGet('handleNwsEndpointsResponse', httpParams)
}

/**
 * Handles the response from the NWS /points endpoint request.
 * Uses the shared retry logic from the utilities library.
 *
 * @param response The async HTTP response
 * @param data     Additional callback data (unused)
 */
void handleNwsEndpointsResponse(AsyncResponse response, Map data) {
  if (isHttpResponseFailure(response)) {
    handleAsyncHttpFailureWithRetry(response, 'executeNwsEndpointsRetry', 'nwsEndpointsRetryCount')
    return
  }

  // Parse the JSON response from NWS (with retry on parse failure)
  Map jsonData = parseNwsJsonResponse(
    response,
    'executeNwsEndpointsRetry',
    'nwsEndpointsRetryCount',
    'endpoints',
    state?.nwsEndpointsHttpParams?.uri
  )
  if (jsonData == null) return

  // Success - reset retry counter
  resetHttpRetryCounter('nwsEndpointsRetryCount')

  // Extract and store the forecast URLs
  // These URLs are specific to this lat/long and are used for all future requests
  Map properties = jsonData.get('properties') as Map
  state.forecastUri = properties?.get('forecast')
  state.forecastHourlyUri = properties?.get('forecastHourly')

  // Extract zone ID for alerts (forecastZone URL contains the zone ID)
  String forecastZoneUrl = properties?.get('forecastZone') as String
  String zoneId = extractZoneIdFromUrl(forecastZoneUrl)
  if (zoneId) {
    state.zoneId = zoneId
    sendEvent(name: "zoneId", value: zoneId, descriptionText: "Updated zoneId from NWS")
  }

  if (!state.forecastUri || !state.forecastHourlyUri) {
    logWarn("NWS endpoints response did not include forecast URLs")
    return
  }

  logDebug("Forecast URL: ${state.forecastUri}")
  logDebug("Hourly forecast URL: ${state.forecastHourlyUri}")

  // Now that endpoints are available, perform an immediate refresh
  refresh()
}

/**
 * Executes a retry attempt for fetching NWS forecast endpoints.
 */
void executeNwsEndpointsRetry() {
  Map httpParams = state.nwsEndpointsHttpParams ?: [
    uri: "${NWS_API_BASE_URL}/points/${settings.latitude},${settings.longitude}"
  ]

  executeHttpRetryGet('handleNwsEndpointsResponse', httpParams, 'executeNwsEndpointsRetry', 'nwsEndpointsRetryCount')
}


// =============================================================================
// NWS ALERTS
// =============================================================================
// These methods fetch active alerts for the forecast zone.

/**
 * Fetches active alerts for the current forecast zone.
 * Uses the NWS alerts endpoint: /alerts/active/zone/{zoneId}
 */
void fetchActiveAlertsForZone() {
  String zoneId = state.zoneId as String
  if (!zoneId) {
    logWarn("Cannot fetch alerts: zoneId is not available")
    return
  }

  String alertsUrl = "${NWS_API_BASE_URL}/alerts/active/zone/${zoneId}"
  Map httpParams = [:]
  httpParams.uri = alertsUrl

  // Store params for retry attempts
  state.alertsHttpParams = httpParams

  // Reset retry counter for fresh request
  resetHttpRetryCounter('alertsRetryCount')

  // Make asynchronous HTTP GET request
  asynchttpGet('handleAlertsResponse', httpParams)
}

/**
 * Handles the response from the alerts request.
 * Uses shared retry logic on failure.
 *
 * @param response The async HTTP response
 * @param data     Additional callback data (unused)
 */
void handleAlertsResponse(AsyncResponse response, Map data) {
  if (isHttpResponseFailure(response)) {
    handleAsyncHttpFailureWithRetry(response, 'executeAlertsRetry', 'alertsRetryCount')
    return
  }

  // Parse the JSON response from NWS (with retry on parse failure)
  Map jsonData = parseNwsJsonResponse(
    response,
    'executeAlertsRetry',
    'alertsRetryCount',
    'alerts',
    state?.alertsHttpParams?.uri
  )
  if (jsonData == null) return

  // Success - reset retry counter
  resetHttpRetryCounter('alertsRetryCount')

  // Store the alerts JSON as a string to ensure attribute persistence
  String alertsJson = JsonOutput.toJson(jsonData)
  sendEvent(name: "alertsJson", value: alertsJson, descriptionText: "Updated alerts from NWS")

  // Build a human-friendly alert summary
  String friendlyText = buildAlertsFriendlyText(jsonData)
  sendEvent(name: "alertsFriendlyText", value: friendlyText, descriptionText: "Updated alerts friendly text")
}

/**
 * Executes a retry attempt for the alerts request.
 */
void executeAlertsRetry() {
  Map httpParams = state.alertsHttpParams ?: [
    uri: "${NWS_API_BASE_URL}/alerts/active/zone/${state.zoneId}"
  ]

  if (!httpParams?.uri) {
    logWarn("Alerts retry skipped: alerts URL not available")
    return
  }

  executeHttpRetryGet('handleAlertsResponse', httpParams, 'executeAlertsRetry', 'alertsRetryCount')
}

/**
 * Builds a human-friendly alert summary from the NWS alerts response.
 *
 * @param alertsData The parsed JSON alerts response
 * @return A readable summary string
 */
@CompileStatic
private String buildAlertsFriendlyText(Map alertsData) {
  if (alertsData == null) {
    return "No alerts data available"
  }

  List features = alertsData.get('features') as List
  if (!features || features.isEmpty()) {
    return "No active alerts"
  }

  List<String> summaries = []
  for (Object featureObj : features) {
    Map feature = featureObj as Map
    Map properties = feature?.get('properties') as Map
    String eventName = properties?.get('event') as String
    String headline = properties?.get('headline') as String

    if (headline) {
      summaries.add(headline)
    } else if (eventName) {
      summaries.add(eventName)
    }
  }

  if (summaries.isEmpty()) {
    return "Active alerts present, but no headline text available"
  }

  return summaries.join(" | ")
}


// =============================================================================
// DETAILED FORECAST DATA RETRIEVAL
// =============================================================================
// These methods fetch and process the detailed text forecast from NWS.

/**
 * Fetches the detailed forecast from the NWS API and updates device attributes.
 * This retrieves the primary forecast which includes:
 * - Detailed text forecast description
 * - Current temperature
 * - Wind speed and direction
 * - Humidity, dewpoint, precipitation probability
 * - When the forecast was generated
 *
 * The forecast is broken into "periods" (e.g., "This Afternoon", "Tonight", etc.)
 * This method retrieves the first period (most current forecast).
 */
void fetchDetailedForecastFromNWS() {
  logDebug("Fetching detailed forecast from NWS...")

  // Ensure forecast endpoint is available
  if (!state.forecastUri) {
    logWarn("Forecast URL not available. Fetching endpoints first.")
    fetchNWSForecastEndpoints()
    return
  }

  // Use the forecast URL we got from fetchNWSForecastEndpoints()
  Map httpParams = [:]
  httpParams.uri = state.forecastUri

  // Store params for retry attempts
  state.detailedForecastHttpParams = httpParams

  // Reset retry counter for fresh request
  resetHttpRetryCounter('detailedForecastRetryCount')

  // Make asynchronous HTTP GET request to the NWS forecast endpoint
  asynchttpGet('handleDetailedForecastResponse', httpParams)
}

/**
 * Handles the response from the detailed forecast request.
 * Uses shared retry logic on failure.
 *
 * @param response The async HTTP response
 * @param data     Additional callback data (unused)
 */
void handleDetailedForecastResponse(AsyncResponse response, Map data) {
  if (isHttpResponseFailure(response)) {
    handleAsyncHttpFailureWithRetry(response, 'executeDetailedForecastRetry', 'detailedForecastRetryCount')
    return
  }

  // Parse the JSON response from NWS (with retry on parse failure)
  Map jsonData = parseNwsJsonResponse(
    response,
    'executeDetailedForecastRetry',
    'detailedForecastRetryCount',
    'detailed forecast',
    state?.detailedForecastHttpParams?.uri
  )
  if (jsonData == null) return

  // Success - reset retry counter
  resetHttpRetryCounter('detailedForecastRetryCount')

  // Store the unit system (US or SI) for later use
  Map properties = jsonData.get('properties') as Map
  state.units = properties?.get('units')

  // Get all forecast periods from the response
  List allPeriods = properties?.get('periods') as List

  // Find the first period (current forecast)
  // Periods are numbered starting at 1
  Map currentPeriod = null
  if (allPeriods != null) {
    for (Map period : allPeriods) {
      Number numberValue = period?.get('number') as Number
      if (numberValue != null && numberValue.intValue() == 1) {
        currentPeriod = period
        break
      }
    }
  }

  // If no data was found, log and exit
  if (currentPeriod == null) {
    logWarn("No current forecast period found in NWS response")
    return
  }

  // Extract and update all forecast attributes
  updateForecastGeneratedTimestamp(jsonData)
  updateDetailedForecastText(currentPeriod)
  updateWindAttributes(currentPeriod)
  updateTemperatureAttribute(currentPeriod)
  updateHumidityAttribute(currentPeriod)
  updatePrecipitationProbabilityAttribute(currentPeriod)
  updateDewpointAttribute(currentPeriod)
}

/**
 * Executes a retry attempt for the detailed forecast request.
 */
void executeDetailedForecastRetry() {
  Map httpParams = state.detailedForecastHttpParams ?: [
    uri: state.forecastUri
  ]

  if (!httpParams?.uri) {
    logWarn("Detailed forecast retry skipped: forecast URL not available")
    return
  }

  executeHttpRetryGet('handleDetailedForecastResponse', httpParams, 'executeDetailedForecastRetry', 'detailedForecastRetryCount')
}

/**
 * Updates the "generatedAt" attribute with when NWS generated the forecast.
 * This timestamp indicates the freshness of the forecast data.
 *
 * @param jsonData The parsed JSON response from NWS
 */
private void updateForecastGeneratedTimestamp(Map jsonData) {
  // Extract the generation timestamp from the JSON
  Map properties = jsonData.get('properties') as Map
  String generatedTimestamp = properties?.get('generatedAt') as String

  // Convert ISO 8601 timestamp string to Date object
  Date generatedDate = toDateTime(generatedTimestamp)

  // Update the device attribute
  sendEvent(
    name: "generatedAt",
    value: generatedDate,
    descriptionText: "Forecast generated by NWS at ${generatedDate}"
  )
}

/**
 * Updates the "detailedForecast" attribute with the full text forecast.
 * This is the human-readable forecast description from NWS.
 *
 * @param currentPeriod The current forecast period data
 */
private void updateDetailedForecastText(Map currentPeriod) {
  String forecastText = currentPeriod.get('detailedForecast') as String

  sendEvent(
    name: "detailedForecast",
    value: forecastText,
    descriptionText: "Updated detailed forecast from NWS"
  )
}

/**
 * Updates wind-related attributes (speed and direction).
 *
 * @param currentPeriod The current forecast period data
 */
private void updateWindAttributes(Map currentPeriod) {
  // Wind speed (e.g., "10 mph", "5 to 10 mph")
  String windSpeed = currentPeriod.get('windSpeed') as String
  sendEvent(
    name: "windSpeed",
    value: windSpeed,
    descriptionText: "Updated wind speed from NWS"
  )

  // Wind direction (e.g., "NW", "SE", "Variable")
  String windDirection = currentPeriod.get('windDirection') as String
  sendEvent(
    name: "windDirection",
    value: windDirection,
    descriptionText: "Updated wind direction from NWS"
  )
}

/**
 * Updates the temperature attribute.
 * Includes the proper unit (°F or °C) based on the forecast data.
 *
 * @param currentPeriod The current forecast period data
 */
private void updateTemperatureAttribute(Map currentPeriod) {
  Number temperatureNumber = currentPeriod.get('temperature') as Number
  Integer temperature = temperatureNumber != null ? temperatureNumber.intValue() : 0
  String temperatureUnit = currentPeriod.get('temperatureUnit') as String

  sendEvent(
    name: "temperature",
    value: temperature,
    unit: "°${temperatureUnit}",
    descriptionText: "Updated temperature from NWS"
  )
}

/**
 * Updates the relative humidity attribute.
 * NWS provides this as an object with a "value" property.
 *
 * @param currentPeriod The current forecast period data
 */
private void updateHumidityAttribute(Map currentPeriod) {
  // Extract humidity data (may be null if not provided)
  Map humidityData = currentPeriod.get('relativeHumidity') as Map
  Number humidityNumber = humidityData?.get('value') as Number
  Integer humidityValue = humidityNumber != null ? humidityNumber.intValue() : 0

  sendEvent(
    name: "relativeHumidity",
    value: humidityValue,
    unit: "%",
    descriptionText: "Updated relative humidity from NWS"
  )
}

/**
 * Updates the probability of precipitation attribute.
 * NWS provides this as an object with a "value" property.
 *
 * @param currentPeriod The current forecast period data
 */
private void updatePrecipitationProbabilityAttribute(Map currentPeriod) {
  // Extract precipitation probability data (may be null if not provided)
  Map precipData = currentPeriod.get('probabilityOfPrecipitation') as Map
  Number precipNumber = precipData?.get('value') as Number
  Integer precipValue = precipNumber != null ? precipNumber.intValue() : 0

  sendEvent(
    name: "probabilityOfPrecipitation",
    value: precipValue,
    unit: "%",
    descriptionText: "Updated precipitation probability from NWS"
  )
}

/**
 * Updates the dewpoint attribute.
 * Handles unit conversion if needed (Celsius to Fahrenheit).
 *
 * @param currentPeriod The current forecast period data
 */
private void updateDewpointAttribute(Map currentPeriod) {
  // Calculate dewpoint with proper unit conversion
  Map dewpointResult = calculateDewpointWithUnitConversion(currentPeriod)

  sendEvent(
    name: "dewpoint",
    value: dewpointResult?.get('value') ?: 0,
    unit: dewpointResult?.get('unit') ?: "°F",
    descriptionText: "Updated dewpoint from NWS"
  )
}


// =============================================================================
// HOURLY FORECAST DATA RETRIEVAL
// =============================================================================
// These methods fetch and process hourly forecast data from NWS.

/**
 * Fetches hourly forecast data from the NWS API and updates device attributes.
 * This retrieves hour-by-hour forecast data for the number of hours configured
 * by the user (e.g., 12 hours, 24 hours, etc.).
 *
 * Each hour's forecast includes:
 * - Start time of the forecast period
 * - Temperature
 * - Wind speed and direction
 * - Short forecast description
 * - Precipitation probability
 * - Humidity
 * - Dewpoint
 *
 * Additionally, this calculates the high/low temperatures for the next 12 hours.
 */
void fetchHourlyForecastsFromNWS() {
  logDebug("Fetching hourly forecasts from NWS...")

  // Get the number of hours to fetch from user settings
  Integer hoursToFetch = settings.numberOfHourlyForecasts as Integer

  if (hoursToFetch <= 0) {
    logDebug("Hourly forecasts disabled (0 hours configured).")
    return
  }

  // Ensure hourly forecast endpoint is available
  if (!state.forecastHourlyUri) {
    logWarn("Hourly forecast URL not available. Fetching endpoints first.")
    fetchNWSForecastEndpoints()
    return
  }

  // Use the hourly forecast URL we got from fetchNWSForecastEndpoints()
  Map httpParams = [:]
  httpParams.uri = state.forecastHourlyUri

  // Store params for retry attempts
  state.hourlyForecastHttpParams = httpParams

  // Reset retry counter for fresh request
  resetHttpRetryCounter('hourlyForecastRetryCount')

  // Make asynchronous HTTP GET request to the NWS hourly forecast endpoint
  asynchttpGet('handleHourlyForecastResponse', httpParams)
}

/**
 * Handles the response from the hourly forecast request.
 * Uses shared retry logic on failure.
 *
 * @param response The async HTTP response
 * @param data     Additional callback data (unused)
 */
void handleHourlyForecastResponse(AsyncResponse response, Map data) {
  if (isHttpResponseFailure(response)) {
    handleAsyncHttpFailureWithRetry(response, 'executeHourlyForecastRetry', 'hourlyForecastRetryCount')
    return
  }

  // Parse the JSON response from NWS (with retry on parse failure)
  Map jsonData = parseNwsJsonResponse(
    response,
    'executeHourlyForecastRetry',
    'hourlyForecastRetryCount',
    'hourly forecast',
    state?.hourlyForecastHttpParams?.uri
  )
  if (jsonData == null) return

  // Success - reset retry counter
  resetHttpRetryCounter('hourlyForecastRetryCount')

  // Store the unit system for this forecast
  Map properties = jsonData.get('properties') as Map
  state.hourlyUnits = properties?.get('units')

  // Get all hourly forecast periods from the response
  List allHourlyPeriods = properties?.get('periods') as List
  if (!allHourlyPeriods) {
    logWarn("No hourly forecast periods returned from NWS")
    return
  }

  // Process each hour's forecast data and update attributes
  Integer hoursToFetch = settings.numberOfHourlyForecasts as Integer
  for (Integer hourNumber = 1; hourNumber <= hoursToFetch; hourNumber++) {
    // Find the forecast period for this hour
    // Periods are numbered starting at 1 (1 = 1st hour, 2 = 2nd hour, etc.)
    Map hourPeriod = null
    for (Map period : allHourlyPeriods) {
      Number numberValue = period?.get('number') as Number
      if (numberValue != null && numberValue.intValue() == hourNumber) {
        hourPeriod = period
        break
      }
    }

    if (hourPeriod != null) {
      // Build a map with all forecast data for this hour
      Map hourlyForecastData = buildHourlyForecastMap(hourPeriod)

      // Update the device attribute for this hour (e.g., "forecast12h")
      sendEvent(
        name: "forecast${hourNumber}h",
        value: hourlyForecastData,
        descriptionText: "Updated ${hourNumber}-hour forecast from NWS"
      )
    }
  }

  // Calculate and update high/low temperatures for the next 12 hours
  calculateAndUpdateHighLowFromHourlyData(allHourlyPeriods)
}

/**
 * Executes a retry attempt for the hourly forecast request.
 */
void executeHourlyForecastRetry() {
  Map httpParams = state.hourlyForecastHttpParams ?: [
    uri: state.forecastHourlyUri
  ]

  if (!httpParams?.uri) {
    logWarn("Hourly forecast retry skipped: hourly forecast URL not available")
    return
  }

  executeHttpRetryGet('handleHourlyForecastResponse', httpParams, 'executeHourlyForecastRetry', 'hourlyForecastRetryCount')
}

/**
 * Builds a Map containing all relevant data for one hour's forecast.
 * Extracts data from the NWS hourly forecast period and organizes it
 * into a clean structure for storage in device attributes.
 *
 * @param hourPeriod The hourly forecast period data from NWS
 * @return Map containing organized forecast data for this hour
 */
private Map buildHourlyForecastMap(Map hourPeriod) {
  Map forecast = [:]

  // Start time of this forecast period (ISO 8601 format)
  forecast.put('startTime', hourPeriod.get('startTime') as String)

  // Temperature for this hour
  Number temperatureNumber = hourPeriod.get('temperature') as Number
  forecast.put('temperature', temperatureNumber != null ? temperatureNumber.intValue() : 0)
  forecast.put('temperatureUnit', hourPeriod.get('temperatureUnit') as String)

  // Wind information
  forecast.put('windSpeed', hourPeriod.get('windSpeed') as String)
  forecast.put('windDirection', hourPeriod.get('windDirection') as String)

  // Short text forecast (e.g., "Partly Cloudy", "Chance Rain Showers")
  forecast.put('shortForecast', hourPeriod.get('shortForecast') as String)

  // Probability of precipitation (may be null)
  Map precipData = hourPeriod.get('probabilityOfPrecipitation') as Map
  Number precipValue = precipData?.get('value') as Number
  forecast.put('probabilityOfPrecipitation', precipValue)

  // Relative humidity (may be null)
  Map humidityData = hourPeriod.get('relativeHumidity') as Map
  Number humidityValue = humidityData?.get('value') as Number
  forecast.put('relativeHumidity', humidityValue)

  // Dewpoint with unit conversion if needed
  Map dewpointResult = calculateDewpointWithUnitConversion(hourPeriod)
  forecast.put('dewpoint', dewpointResult?.get('value'))

  return forecast
}

/**
 * Calculates high and low temperatures from hourly forecast data
 * and updates the temperatureHi and temperatureLo attributes.
 *
 * This looks at the next 12 hours of forecast data to determine
 * the expected high and low temperatures.
 *
 * @param allHourlyPeriods All hourly forecast periods from NWS
 */
private void calculateAndUpdateHighLowFromHourlyData(List allHourlyPeriods) {
  // Get temperatures from the first 12 hourly periods
  // Filter for periods numbered 1-12, extract temperature values
  List next12HourTemperatures = []
  for (Map period : allHourlyPeriods) {
    Number numberValue = period?.get('number') as Number
    if (numberValue != null && numberValue.intValue() < 13) {
      Number tempValue = period?.get('temperature') as Number
      if (tempValue != null) {
        next12HourTemperatures.add(tempValue.intValue())
      }
    }
  }

  // Find the maximum and minimum temperatures
  if (next12HourTemperatures.isEmpty()) {
    logWarn("No hourly temperatures available to calculate high/low")
    return
  }

  Integer highTemp = next12HourTemperatures.max()
  Integer lowTemp = next12HourTemperatures.min()

  // Update the device attributes
  sendEvent(name: "temperatureHi", value: highTemp)
  sendEvent(name: "temperatureLo", value: lowTemp)

  logDebug("Next 12-hour temperature range: ${lowTemp}° to ${highTemp}°")
}


// =============================================================================
// HIGH/LOW TEMPERATURE CALCULATION
// =============================================================================
// These methods calculate the high and low temperatures for the next 12 hours.

/**
 * Calculates the high and low temperatures for the next 12 hours.
 * This is called after fetching the detailed forecast to provide
 * quick temperature range information without needing hourly data.
 *
 * This method fetches hourly data specifically to calculate the
 * high/low values, even if hourly forecasts are disabled.
 */
void calculateHighAndLowTemperatures() {
  logDebug("Calculating high and low temperatures...")

  // Ensure hourly forecast endpoint is available
  if (!state.forecastHourlyUri) {
    logWarn("Hourly forecast URL not available. Fetching endpoints first.")
    fetchNWSForecastEndpoints()
    return
  }

  // Use the hourly forecast URL to get temperature data
  Map httpParams = [:]
  httpParams.uri = state.forecastHourlyUri

  // Store params for retry attempts
  state.highLowHttpParams = httpParams

  // Reset retry counter for fresh request
  resetHttpRetryCounter('highLowRetryCount')

  // Make asynchronous HTTP GET request
  asynchttpGet('handleHighLowResponse', httpParams)
}

/**
 * Handles the response from the high/low temperature request.
 * Uses shared retry logic on failure.
 *
 * @param response The async HTTP response
 * @param data     Additional callback data (unused)
 */
void handleHighLowResponse(AsyncResponse response, Map data) {
  if (isHttpResponseFailure(response)) {
    handleAsyncHttpFailureWithRetry(response, 'executeHighLowRetry', 'highLowRetryCount')
    return
  }

  // Parse the JSON response from NWS (with retry on parse failure)
  Map jsonData = parseNwsJsonResponse(
    response,
    'executeHighLowRetry',
    'highLowRetryCount',
    'hourly high/low',
    state?.highLowHttpParams?.uri
  )
  if (jsonData == null) return

  // Success - reset retry counter
  resetHttpRetryCounter('highLowRetryCount')

  // Store the unit system
  Map properties = jsonData.get('properties') as Map
  state.hourlyUnits = properties?.get('units')

  // Get all hourly forecast periods
  List allHourlyPeriods = properties?.get('periods') as List
  if (!allHourlyPeriods) {
    logWarn("No hourly forecast periods returned from NWS for high/low calculation")
    return
  }

  // Calculate high/low from the next 12 hours
  calculateAndUpdateHighLowFromHourlyData(allHourlyPeriods)
}

/**
 * Executes a retry attempt for the high/low temperature request.
 */
void executeHighLowRetry() {
  Map httpParams = state.highLowHttpParams ?: [
    uri: state.forecastHourlyUri
  ]

  if (!httpParams?.uri) {
    logWarn("High/low retry skipped: hourly forecast URL not available")
    return
  }

  executeHttpRetryGet('handleHighLowResponse', httpParams, 'executeHighLowRetry', 'highLowRetryCount')
}


// =============================================================================
// UNIT CONVERSION UTILITIES
// =============================================================================
// These methods handle unit conversions for temperature values.

/**
 * Calculates the dewpoint temperature with proper unit conversion.
 * The NWS API sometimes returns dewpoint in Celsius even when the user
 * has configured US units (Fahrenheit). This method handles that conversion.
 *
 * @param forecastPeriod The forecast period data (detailed or hourly)
 * @return Map with 'value' (Integer) and 'unit' (String) keys
 */
private Map calculateDewpointWithUnitConversion(Map forecastPeriod) {
  // Extract dewpoint data from the forecast period
  Map dewpointData = forecastPeriod.get('dewpoint') as Map

  // Determine the unit from the unitCode
  // NWS uses "wmoUnit:degC" for Celsius
  String unitCode = dewpointData?.get('unitCode') as String
  String dewpointUnit = unitCode == "wmoUnit:degC" ? "°C" : "°F"

  // Get the dewpoint value (may be null)
  Number dewpointNumber = dewpointData?.get('value') as Number
  Integer dewpointValue = dewpointNumber != null ? dewpointNumber.intValue() : 0

  // Check if we need to convert Celsius to Fahrenheit
  // This is needed when:
  // 1. The user has US units configured (state.units == "us")
  // 2. The data is in Celsius (unitCode == "wmoUnit:degC")
  // 3. The value is not null
  if (state.units == "us" && unitCode == "wmoUnit:degC" && dewpointData?.get('value') != null) {
    // Convert Celsius to Fahrenheit: F = C * 1.8 + 32
    dewpointValue = (dewpointValue * 1.8 + 32).intValue()
    dewpointUnit = "°F"
  } else {
    // Just convert to integer if no conversion needed
    dewpointValue = dewpointValue?.intValue()
  }

  return [value: dewpointValue, unit: dewpointUnit]
}
