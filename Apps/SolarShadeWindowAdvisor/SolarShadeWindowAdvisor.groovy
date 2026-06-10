/**
 *  MIT License
 *  Copyright 2026 Daniel Winks (daniel.winks@gmail.com)
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
 *
 *  This app is intentionally STANDALONE - it has NO #include library
 *  dependencies. The logging/lifecycle/HTTP-retry helpers, the sun-position
 *  math, and the solar-load/occlusion/learning/decision math are all inlined
 *  below so the app can be installed by itself with nothing else to manage.
 */

import groovy.transform.CompileStatic
import groovy.transform.Field
import java.text.SimpleDateFormat

definition(
  name: 'Solar Shade & Window Advisor',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Advises when to draw shades and open/close windows per building side, using solar load, per-side occlusion, weather, and an adaptive thermal model that learns from indoor sensors.',
  category: 'Convenience',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

// =============================================================================
// Constants
// =============================================================================

@Field static final String STATUS_DRIVER = 'Solar Shade Advisor Status'
@Field static final String OPEN_METEO_URL = 'https://api.open-meteo.com/v1/forecast'
@Field static final Integer MODEL_FEATURES = 4          // [1, (Tout-Tin), solarLoad, hvac]
@Field static final Double MIN_SAMPLES_FOR_LEARNED = 20.0d
@Field static final Double BLEND_RAMP_SAMPLES = 30.0d
// Cold-start (pre-learning) 1R1C parameters, expressed per-minute (scale-relative)
@Field static final Double COLD_TAU_MIN = 120.0d
@Field static final Double COLD_K_SOLAR = 0.0007d
@Field static final Double COLD_H_HVAC = 0.06d

// Windows-open decision tuning
@Field static final Double WINDOW_HYST = 1.5d              // re-open hysteresis (degrees below ceiling)
@Field static final Integer WINDOW_FORECAST_STEP_MIN = 10  // integration step (min) for the windows-open forecast

// Humidity comfort gate
@Field static final Double HUMIDITY_HYST = 1.0d      // extra hub-degrees of advantage required to CLEAR an active veto

// Forecast bias correction: the observed-minus-hourly-forecast outdoor temperature
// offset is applied to the projection, decaying linearly to zero over this window.
@Field static final Double BIAS_DECAY_MIN = 90.0d

// Learned-model wiring (the thermal-model child device)
@Field static final String THERMAL_DRIVER = 'Solar Shade Thermal Model'
@Field static final Double LEARN_MIN_SAMPLES = 15.0d       // samples before trusting the learned windows-open model
@Field static final Double LEARN_MIN_R2 = 0.3d             // minimum fit quality before trusting it
@Field static final Double COUPLING_MAX = 0.06d            // cap on b1 (tau >= ~16 min) for Euler stability

// Solar-load / occlusion constants
@Field static final double SOLAR_REF_MAX = 1000.0d           // W/m^2 reference for 100% load
@Field static final double DIFFUSE_VERTICAL_FACTOR = 0.5d    // isotropic-sky diffuse on a wall
@Field static final double MIN_SUN_ALTITUDE_DEG = 1.0d
@Field static final double MIN_STRUCTURE_DISTANCE_M = 0.5d

// Sun-position math constants
@Field static final double DEG2RAD = 0.017453292519943d
@Field static final double OBLIQUITY = 0.409099940679715d    // Earth's axial tilt (radians)

// HTTP retry constants
@Field static final List<Integer> HTTP_RETRY_DELAYS = [60, 180, 300]
@Field static final Integer HTTP_MAX_RETRIES = 3

// =============================================================================
// Preferences
// =============================================================================

preferences {
  page(name: 'mainPage')
  page(name: 'wallsPage')
}

Map mainPage() {
  dynamicPage(name: 'mainPage', title: '<h1>Solar Shade &amp; Window Advisor</h1>', install: true, uninstall: true) {
    section('<h2>Climate Control</h2>') {
      input 'thermostat', 'capability.thermostat', title: 'Thermostat (provides heating/cooling setpoints &amp; mode)', required: true, multiple: false
      input 'tempSensors', 'capability.temperatureMeasurement', title: 'Indoor temperature sensors (whole-house average)', required: true, multiple: true
      input 'maxDevHot', 'decimal', title: "Max degrees ABOVE cooling setpoint before windows should close (&deg;${getTempScale()}) - keep tight", required: true, defaultValue: 3.0, range: '0.5..15'
      input 'maxDevCold', 'decimal', title: "Max degrees BELOW heating setpoint tolerated with windows open (&deg;${getTempScale()}) - keep loose; cold is cheap", required: true, defaultValue: 10.0, range: '1..40'
      input 'windowOpenTauMin', 'number', title: 'Windows-open responsiveness (minutes for indoor to track outdoor; lower = reacts faster)', required: true, defaultValue: 45, range: '15..240'
      input 'forecastHorizonHours', 'number', title: 'Forecast horizon (hours) - how far ahead to look (used to decide it is worth opening even if briefly warm)', required: true, defaultValue: 3, range: '1..12'
      input 'closeLeadHours', 'number', title: 'Close-windows lead time (hours) - close only when overheating is this close. Lower = keep windows open longer / close at the last moment', required: true, defaultValue: 1, range: '1..6'
    }

    section('<h2>Humidity &amp; Comfort</h2>') {
      input 'outdoorHumiditySensors', 'capability.relativeHumidityMeasurement', title: 'Outdoor humidity sensor(s) (optional; Open-Meteo is used when unset)', required: false, multiple: true
      input 'outdoorTempSensors', 'capability.temperatureMeasurement', title: 'Outdoor temperature sensor(s) (optional; Open-Meteo is used when unset)', required: false, multiple: true
      input 'indoorHumiditySensors', 'capability.relativeHumidityMeasurement', title: 'Indoor humidity sensor(s) (optional; a muggy interior relaxes the humidity gate)', required: false, multiple: true
      input 'dewPointComfort', 'decimal', title: "Outdoor dew point (&deg;${getTempScale()}) at/above which air starts feeling humid", required: true, defaultValue: (getTempScale() == 'C' ? 15.5 : 60.0), range: '0..100'
      input 'dewPointSlope', 'decimal', title: 'Degrees of indoor-outdoor temperature advantage required per degree of dew point excess', required: true, defaultValue: 1.0, range: '0.1..5'
      paragraph 'Windows are not recommended open when outdoor air is muggy unless it is also enough cooler outside to be worth it. With no sensors selected, Open-Meteo current conditions are used automatically.'
    }

    section('<h2>Windows &amp; Shades</h2>') {
      input 'windowContacts', 'capability.contactSensor', title: 'Window contact sensors (global; open = fresh air in progress)', required: false, multiple: true
      input 'drawThreshold', 'number', title: 'Solar load (0-100) at/above which shades should be drawn', required: true, defaultValue: 60, range: '10..100'
      input 'shadeHysteresis', 'number', title: 'Hysteresis below threshold before shades reopen', required: true, defaultValue: 10, range: '0..50'
    }

    section('<h2>Building Sides (walls)</h2>') {
      input 'wallCount', 'number', title: 'How many walls/sides to configure?', required: true, defaultValue: 4, range: '1..12', submitOnChange: true
      href(name: 'toWalls', page: 'wallsPage', title: 'Configure each wall (azimuth, glazing, occlusion, sensors)', description: 'Tap to configure walls')
    }

    section('<h2>Notifications</h2>') {
      input 'notifiers', 'capability.notification', title: 'Notification devices for advisories', required: false, multiple: true
      input 'alertingEnabled', 'bool', title: 'Send advisory notifications', required: false, defaultValue: true
      input 'disableSwitches', 'capability.switch', title: 'Pause advisories when any of these switches is ON', required: false, multiple: true
      input 'pauseModes', 'mode', title: 'Pause advisories while the hub is in any of these modes', required: false, multiple: true
      input 'quietStart', 'time', title: 'Quiet hours start (suppress advisories from this time)', required: false
      input 'quietEnd', 'time', title: 'Quiet hours end (resume advisories at this time)', required: false
      paragraph 'Quiet hours, modes, and switches only suppress NOTIFICATIONS - the status device keeps updating.'
    }

    section('<h2>Location &amp; Weather</h2>') {
      // NOTE: text (not decimal) inputs - a 'decimal' input renders as an HTML5
      // number field with step=0.01, which rejects the 5-6 decimal precision that
      // coordinates require. Text inputs accept any precision; the code parses them.
      input 'latitude', 'text', title: 'Latitude', description: 'Decimal degrees, e.g. 39.98808 (blank = use hub location)', required: true, defaultValue: location.latitude
      input 'longitude', 'text', title: 'Longitude', description: 'Decimal degrees, e.g. -83.06040 (blank = use hub location)', required: true, defaultValue: location.longitude
      input 'weatherRefreshMinutes', 'number', title: 'Weather refresh interval (minutes)', required: true, defaultValue: 15, range: '5..120'
      paragraph 'Weather (temperature + solar irradiance) is fetched from Open-Meteo (free, no API key required).'
    }

    section('<h2>Adaptive Learning</h2>') {
      input 'learningEnabled', 'bool', title: 'Learn this home&apos;s thermal response from the indoor sensors', required: false, defaultValue: true
      input 'evaluateMinutes', 'number', title: 'Observe &amp; evaluate interval (minutes)', required: true, defaultValue: 10, range: '5..60'
      paragraph 'Learned thermal coefficients accumulate on the "... Thermal Model" child device. The windows-open forecast uses them automatically once enough good open-window/HVAC-off data is collected.'
    }

    section('<h2>Logging</h2>') {
      input 'logEnable', 'bool', title: 'Enable logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
      input(name: 'evaluateBtn', type: 'button', title: 'Refresh &amp; Evaluate Now')
    }

    section() {
      label title: 'App name', required: false
    }
  }
}

Map wallsPage() {
  Integer count = (settings.wallCount ?: 4) as Integer
  dynamicPage(name: 'wallsPage', title: '<h1>Wall Configuration</h1>') {
    for (Integer i = 1; i <= count; i++) {
      section("<h2>Wall ${i}</h2>") {
        input "wall${i}Name", 'text', title: 'Wall name/label', required: false, defaultValue: "Wall ${i}"
        input "wall${i}Azimuth", 'decimal', title: 'Facing azimuth (compass bearing 0-360, 0=N, 90=E, 180=S, 270=W)', required: true, range: '0..360'
        input "wall${i}GlazingArea", 'decimal', title: 'Approx. glazing/window area (optional, for context)', required: false
        input "wall${i}Sensors", 'capability.temperatureMeasurement', title: 'Indoor sensor(s) for this side (optional; enables a per-side learned model)', required: false, multiple: true
        input "wall${i}Contacts", 'capability.contactSensor', title: 'Window contact sensor(s) on this side (optional)', required: false, multiple: true
        input "wall${i}Occlusion", 'enum', title: 'Occlusion mode', required: true, defaultValue: 'none',
          options: ['none': 'None (fully exposed)', 'full': 'Full / shared wall (100% blocked)', 'partial': 'Partial / shared wall (fixed %)', 'shaded': 'Shaded by nearby structure (computed)'],
          submitOnChange: true
        String mode = settings."wall${i}Occlusion"
        if (mode == 'partial') {
          input "wall${i}OcclusionPct", 'number', title: 'Percent of this wall permanently blocked', required: true, defaultValue: 50, range: '1..100'
        }
        if (mode == 'shaded') {
          input "wall${i}StructHeight", 'decimal', title: 'Shading structure height (same units throughout, e.g. meters)', required: true
          input "wall${i}StructDistance", 'decimal', title: 'Horizontal distance to the structure', required: true
          input "wall${i}StructWidth", 'decimal', title: 'Width of the shading structure', required: true
          input "wall${i}WallWidth", 'decimal', title: 'Width of this wall', required: true
          input "wall${i}WallHeight", 'decimal', title: 'Height of this wall (glazing)', required: true
        }
      }
    }
  }
}

// =============================================================================
// Lifecycle
// =============================================================================

void installed() {
  logDebug('Installed...')
  initialize()
  if (settings.logEnable != false) { runIn(1800, 'logsOff') }
  if (settings.debugLogEnable != false) { runIn(1800, 'debugLogsOff') }
}

void updated() {
  logDebug('Updated...')
  configure()
}

void uninstalled() {
  logDebug('Uninstalled...')
  unschedule()
  getChildDevices().each { child -> deleteChildDevice(child.deviceNetworkId) }
}

void initialize() {
  configure()
}

void configure() {
  logInfo('Configuring Solar Shade & Window Advisor...')
  unsubscribe()
  unschedule()
  ensureStatusDevice()
  ensureStatsDevice()

  subscribe(settings.tempSensors, 'temperature', 'temperatureEvent')
  subscribe(settings.thermostat, 'thermostatOperatingState', 'thermostatEvent')
  if (settings.windowContacts) { subscribe(settings.windowContacts, 'contact', 'contactEvent') }
  if (settings.disableSwitches) { subscribe(settings.disableSwitches, 'switch', 'disableEvent') }
  subscribeWallContacts()

  Integer wxMin = (settings.weatherRefreshMinutes ?: 15) as Integer
  Integer evalMin = (settings.evaluateMinutes ?: 10) as Integer
  schedule(runEveryCustomMinutes(wxMin), 'refreshWeather')
  schedule(runEveryCustomMinutes(evalMin), 'recordAndEvaluate')

  refreshWeather()
  runIn(20, 'recordAndEvaluate')
}

void appButtonHandler(String btn) {
  if (btn == 'evaluateBtn') {
    refreshWeather()
    runIn(5, 'recordAndEvaluate')
  }
}

// =============================================================================
// Child status device
// =============================================================================

void ensureStatusDevice() {
  String dni = "ssa-${app.id}"
  com.hubitat.app.ChildDeviceWrapper child = getChildDevice(dni)
  if (child == null) {
    try {
      addChildDevice('dwinks', STATUS_DRIVER, dni, [name: STATUS_DRIVER, label: "${app.label ?: 'Solar Shade Advisor'} Status", isComponent: true])
      logInfo("Created status child device ${dni}")
    } catch (e) {
      logWarn("Could not create status child device (install the '${STATUS_DRIVER}' driver): ${e}")
    }
  }
}

com.hubitat.app.ChildDeviceWrapper getStatusDevice() {
  return getChildDevice("ssa-${app.id}")
}

void ensureStatsDevice() {
  String dni = "ssa-thermal-${app.id}"
  com.hubitat.app.ChildDeviceWrapper child = getChildDevice(dni)
  if (child == null) {
    try {
      addChildDevice('dwinks', THERMAL_DRIVER, dni, [name: THERMAL_DRIVER, label: "${app.label ?: 'Solar Shade Advisor'} Thermal Model", isComponent: true])
      logInfo("Created thermal-model child device ${dni}")
    } catch (e) {
      logWarn("Could not create thermal-model child device (install the '${THERMAL_DRIVER}' driver): ${e}")
    }
  }
}

com.hubitat.app.ChildDeviceWrapper getStatsDevice() {
  return getChildDevice("ssa-thermal-${app.id}")
}

// =============================================================================
// Event handlers
// =============================================================================

void temperatureEvent(evt) { logDebug("Temperature event: ${evt?.displayName} = ${evt?.value}") }
void thermostatEvent(evt) { logDebug("Thermostat operating state: ${evt?.value}"); runIn(3, 'recordAndEvaluate') }
void contactEvent(evt) { logDebug("Contact event: ${evt?.displayName} = ${evt?.value}"); runIn(3, 'recordAndEvaluate') }
void disableEvent(evt) { logDebug("Disable switch: ${evt?.displayName} = ${evt?.value}") }

void subscribeWallContacts() {
  Integer count = (settings.wallCount ?: 0) as Integer
  for (Integer i = 1; i <= count; i++) {
    def contacts = settings."wall${i}Contacts"
    if (contacts) { subscribe(contacts, 'contact', 'contactEvent') }
  }
}

Boolean advisoriesPaused() { return pauseReason() != null }

// Returns a human-readable reason notifications are paused, or null if not paused.
String pauseReason() {
  def onSwitch = settings.disableSwitches?.find { sw -> sw.currentValue('switch') == 'on' }
  if (onSwitch) { return "disable switch '${onSwitch.displayName}' is ON" }
  if (settings.pauseModes && (location.mode in settings.pauseModes)) {
    return "hub mode '${location.mode}' is in the pause-modes list"
  }
  if (inQuietHours()) { return "within quiet hours (${settings.quietStart} to ${settings.quietEnd})" }
  return null
}

Boolean inQuietHours() {
  if (!settings.quietStart || !settings.quietEnd) { return false }
  Long nowMs = now()
  Long startMs = timeToday(settings.quietStart, location.timeZone).time
  Long endMs = timeToday(settings.quietEnd, location.timeZone).time
  if (startMs <= endMs) { return nowMs >= startMs && nowMs <= endMs }
  // Overnight window (e.g. 22:00 -> 07:00): in range if after start OR before end
  return nowMs >= startMs || nowMs <= endMs
}

// =============================================================================
// Weather acquisition (Open-Meteo)
// =============================================================================

String buildWeatherUri() {
  BigDecimal lat = (settings.latitude ?: location.latitude) as BigDecimal
  BigDecimal lng = (settings.longitude ?: location.longitude) as BigDecimal
  String tempUnit = (getTempScale() == 'C') ? 'celsius' : 'fahrenheit'
  String hourly = 'temperature_2m,shortwave_radiation,direct_normal_irradiance,diffuse_radiation,cloud_cover'
  return "${OPEN_METEO_URL}?latitude=${lat}&longitude=${lng}&current=temperature_2m,relative_humidity_2m,dew_point_2m,cloud_cover&hourly=${hourly}&forecast_days=2&timezone=auto&temperature_unit=${tempUnit}"
}

void refreshWeather() {
  Map params = [uri: buildWeatherUri(), timeout: 30]
  state.weatherHttpParams = params
  resetHttpRetryCounter('weatherRetryCount')
  logDebug("Fetching weather: ${params.uri}")
  asynchttpGet('weatherCallback', params)
}

void executeWeatherRetry() {
  executeHttpRetryGet('weatherCallback', state.weatherHttpParams, 'executeWeatherRetry', 'weatherRetryCount')
}

void weatherCallback(response, Map data) {
  if (isHttpResponseFailure(response)) {
    handleAsyncHttpFailureWithRetry(response, 'executeWeatherRetry', 'weatherRetryCount')
    return
  }
  resetHttpRetryCounter('weatherRetryCount')
  Map json
  try {
    json = response.getJson() as Map
  } catch (e) {
    logWarn("Could not parse Open-Meteo response: ${e}")
    return
  }
  Map current = (json?.current ?: [:]) as Map
  Map hourly = (json?.hourly ?: [:]) as Map

  state.wxCurrentTemp = asDouble(current?.temperature_2m)
  state.wxCloudNow = asDouble(current?.cloud_cover)
  // Current humidity/dew point (hub-scale - Open-Meteo returns dew point in the
  // requested temperature_unit). Null when absent so the comfort gate disengages.
  state.wxCurrentRh = asDoubleOrNull(current?.relative_humidity_2m)
  state.wxCurrentDewPoint = asDoubleOrNull(current?.dew_point_2m)

  List times = (hourly?.time ?: []) as List
  state.wxHourEpochs = times.collect { t -> parseOpenMeteoTime(t as String) }
  state.wxTemp = (hourly?.temperature_2m ?: []) as List
  state.wxGhi = (hourly?.shortwave_radiation ?: []) as List
  state.wxDni = (hourly?.direct_normal_irradiance ?: []) as List
  state.wxDhi = (hourly?.diffuse_radiation ?: []) as List
  state.wxCloud = (hourly?.cloud_cover ?: []) as List
  state.weatherUpdatedAt = now()
  logInfo("Weather updated: ${state.wxTemp?.size() ?: 0} hourly periods, current ${state.wxCurrentTemp} deg${getTempScale()}")
}

Long parseOpenMeteoTime(String t) {
  if (t == null) { return 0L }
  try {
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm")
    if (location?.timeZone) { fmt.setTimeZone(location.timeZone) }
    return fmt.parse(t).getTime()
  } catch (e) {
    return 0L
  }
}

Integer hourlyIndexFor(Long epoch) {
  List epochs = (state.wxHourEpochs ?: []) as List
  if (epochs.isEmpty()) { return -1 }
  Integer best = 0
  for (Integer i = 0; i < epochs.size(); i++) {
    if ((epochs[i] as Long) <= epoch) { best = i }
  }
  return best
}

// =============================================================================
// Sun position (inlined; time-parameterized so we can forecast across the day)
// =============================================================================

Map sunPositionAt(Long epoch) {
  BigDecimal lat = (settings.latitude ?: location.latitude) as BigDecimal
  BigDecimal lng = (settings.longitude ?: location.longitude) as BigDecimal
  return getPositionForDays(lat, lng, daysSinceJ2000(epoch))
}

@CompileStatic
double daysSinceJ2000(Long epochMillis) {
  // (epochMillis -> days since 1970) + (Unix->Julian offset) - (Julian date of J2000)
  return (epochMillis.doubleValue() / 86400000.0d) + 2440587.5d - 2451545.0d
}

@CompileStatic
Map<String, BigDecimal> getPositionForDays(BigDecimal lat, BigDecimal lng, double d) {
  double lw = DEG2RAD * -lng
  double phi = DEG2RAD * lat
  Map<String, BigDecimal> c = sunCoords(d)
  double H = siderealTime(d, lw) - c.ra
  double az = sunAzimuth(H, phi, c.dec as double)
  az = (az * 180 / Math.PI) + 180
  double al = sunAltitude(H, phi, c.dec as double)
  al = al * 180 / Math.PI
  return [azimuth: new BigDecimal(az), altitude: new BigDecimal(al)]
}

@CompileStatic
Map<String, BigDecimal> sunCoords(double d) {
  double M = solarMeanAnomaly(d)
  double L = eclipticLongitude(M)
  return [dec: sunDeclination(L, 0.0d), ra: sunRightAscension(L, 0.0d)]
}

@CompileStatic
BigDecimal solarMeanAnomaly(double d) { return DEG2RAD * (357.5291 + 0.98560028 * d) }

@CompileStatic
BigDecimal eclipticLongitude(double M) {
  double C = DEG2RAD * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M))
  double P = DEG2RAD * 102.9372
  return M + C + P + Math.PI
}

@CompileStatic
BigDecimal sunRightAscension(double l, double b) {
  return Math.atan2(Math.sin(l) * Math.cos(OBLIQUITY) - Math.tan(b) * Math.sin(OBLIQUITY), Math.cos(l))
}

@CompileStatic
BigDecimal sunDeclination(double l, double b) {
  return Math.asin(Math.sin(b) * Math.cos(OBLIQUITY) + Math.cos(b) * Math.sin(OBLIQUITY) * Math.sin(l))
}

@CompileStatic
BigDecimal siderealTime(double d, double lw) { return DEG2RAD * (280.16 + 360.9856235 * d) - lw }

@CompileStatic
BigDecimal sunAzimuth(double H, double phi, double dec) {
  return Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi))
}

@CompileStatic
BigDecimal sunAltitude(double H, double phi, double dec) {
  return Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H))
}

// =============================================================================
// Per-wall solar load
// =============================================================================

Double wallEffectiveLoadAt(Integer wallIndex, Long epoch, Integer wxIndex) {
  List dniList = (state.wxDni ?: []) as List
  List dhiList = (state.wxDhi ?: []) as List
  if (wxIndex < 0 || wxIndex >= dniList.size()) { return 0.0d }
  double dni = asDouble(dniList[wxIndex])
  double dhi = asDouble(dhiList[wxIndex])
  Map sun = sunPositionAt(epoch)
  double alt = (sun.altitude) as double
  double az = (sun.azimuth) as double
  double wallAz = asDouble(settings."wall${wallIndex}Azimuth")
  double rawLoad = normalizedSolarLoad(dni, dhi, alt, az, wallAz)
  double occ = wallOcclusionFraction(wallIndex, alt, az, wallAz)
  return effectiveSolarLoad(rawLoad, occ)
}

double wallOcclusionFraction(Integer wallIndex, double alt, double az, double wallAz) {
  String mode = settings."wall${wallIndex}Occlusion" ?: 'none'
  if (mode == 'shaded') {
    return shadedOcclusionFraction(alt, az, wallAz,
      asDouble(settings."wall${wallIndex}StructHeight"),
      asDouble(settings."wall${wallIndex}StructDistance"),
      asDouble(settings."wall${wallIndex}StructWidth"),
      asDouble(settings."wall${wallIndex}WallWidth"),
      asDouble(settings."wall${wallIndex}WallHeight"))
  }
  return fixedOcclusionFraction(mode, asDouble(settings."wall${wallIndex}OcclusionPct"))
}

// =============================================================================
// Adaptive thermal model (learned in the "Solar Shade Thermal Model" child device)
// =============================================================================

Double averageTemp(devices) {
  if (!devices) { return null }
  List vals = devices.collect { d -> asDoubleOrNull(d.currentValue('temperature')) }.findAll { it != null }
  if (vals.isEmpty()) { return null }
  return (vals.sum() as double) / vals.size()
}

Double averageHumidity(devices) {
  if (!devices) { return null }
  List vals = devices.collect { d -> asDoubleOrNull(d.currentValue('humidity')) }.findAll { it != null }
  if (vals.isEmpty()) { return null }
  return (vals.sum() as double) / vals.size()
}

// Best current outdoor temperature: local sensors when configured, else Open-Meteo.
Double currentOutdoorTemp() {
  Double sensor = averageTemp(settings.outdoorTempSensors)
  if (sensor != null) { return sensor }
  return asDoubleOrNull(state.wxCurrentTemp)
}

// Dew point in hub-scale degrees from a hub-scale temperature and RH percent.
Double dewPointHubScale(Double temp, Double rh) {
  if (temp == null || rh == null) { return null }
  if (getTempScale() == 'F') { return cToF(dewPointC(fToC(temp as double), rh as double)) }
  return dewPointC(temp as double, rh as double)
}

// Outdoor dew point (hub scale): local RH sensors (with the best available outdoor
// temperature) win; else Open-Meteo's current dew point; else null - and the
// humidity comfort gate disengages entirely.
Double outdoorDewPoint() {
  Double rh = averageHumidity(settings.outdoorHumiditySensors)
  if (rh != null) {
    Double t = currentOutdoorTemp()
    if (t != null) { return dewPointHubScale(t, rh) }
  }
  return asDoubleOrNull(state.wxCurrentDewPoint)
}

// Indoor dew point (hub scale) from the indoor humidity + temperature sensors, or
// null when either is unavailable.
Double indoorDewPoint() {
  Double rh = averageHumidity(settings.indoorHumiditySensors)
  if (rh == null) { return null }
  Double t = averageTemp(settings.tempSensors)
  if (t == null) { return null }
  return dewPointHubScale(t, rh)
}

void recordAndEvaluate() {
  pushThermalSample()
  evaluate()
}

// Feed one sample to the thermal-model child device, which accumulates the
// natural (HVAC-off) temperature response segmented by window state and fits the
// regression the windows-open forecast reads back.
void pushThermalSample() {
  if (settings.learningEnabled == false) { return }
  com.hubitat.app.ChildDeviceWrapper stats = getStatsDevice()
  if (stats == null) { return }
  Double indoor = averageTemp(settings.tempSensors)
  Double tOut = currentOutdoorTemp()
  if (indoor == null || tOut == null) { return }
  Long t = now()
  double solar = maxWallLoadAt(t, hourlyIndexFor(t))
  boolean idle = (hvacStateValue() == 0.0d)
  boolean open = anyWindowOpen()
  stats.logSample(indoor as BigDecimal, tOut as BigDecimal, (solar as BigDecimal),
                  open ? 'true' : 'false', idle ? 'true' : 'false')
}

// =============================================================================
// Forecasting + decision
// =============================================================================

double hvacStateValue() {
  String op = settings.thermostat?.currentValue('thermostatOperatingState')
  if (op == 'cooling') { return -1.0d }
  if (op == 'heating') { return 1.0d }
  return 0.0d
}

Boolean coolingDominant(double indoorTemp, double heatSp, double coolSp) {
  String mode = settings.thermostat?.currentValue('thermostatMode')
  if (mode == 'cool') { return true }
  if (mode == 'heat') { return false }
  double mid = (heatSp + coolSp) / 2.0d
  return indoorTemp >= mid
}

double forecastOutdoorTemp(Integer wx) {
  List temps = (state.wxTemp ?: []) as List
  if (wx >= 0 && wx < temps.size()) { return asDouble(temps[wx]) }
  return state.wxCurrentTemp != null ? asDouble(state.wxCurrentTemp) : 20.0d
}

// Highest effective solar load across all walls at a given time - the most
// sun-exposed side, which dominates heat gain when the windows are open.
double maxWallLoadAt(Long epoch, Integer wx) {
  Integer count = (settings.wallCount ?: 0) as Integer
  double mx = 0.0d
  for (Integer i = 1; i <= count; i++) {
    double l = wallEffectiveLoadAt(i, epoch, wx)
    if (l > mx) { mx = l }
  }
  return mx
}

// Physics forecast of the WINDOWS-OPEN scenario: starting from the current
// indoor temperature, integrate natural coupling to the (forecast) outdoor
// temperature plus solar gain on the most-exposed wall. Returns [min, max, end]
// over the horizon. Deliberately does NOT use the learned model, which was
// trained with HVAC holding the house steady and cannot represent open windows.
double[] forecastWindowsOpen(double startTin, Integer horizonHours, Integer leadHours) {
  Integer stepMin = WINDOW_FORECAST_STEP_MIN
  Integer steps = (horizonHours * 60) / stepMin
  if (steps < 1) { steps = 1 }
  Integer leadSteps = (leadHours * 60) / stepMin
  if (leadSteps < 1) { leadSteps = 1 }
  if (leadSteps > steps) { leadSteps = steps }
  double[] tOutSeq = new double[steps]
  double[] solarSeq = new double[steps]
  // Bias-correct the hourly curve toward observed reality: on a rising morning the
  // hourly forecast lags the actual outdoor temperature, which made the projection
  // start too cool and recommend opening while it was already warmer outside. The
  // observed-minus-forecast offset decays to zero over BIAS_DECAY_MIN minutes.
  double bias = 0.0d
  Double obsOut = currentOutdoorTemp()
  if (obsOut != null) { bias = (obsOut as double) - forecastOutdoorTemp(hourlyIndexFor(now())) }
  for (Integer i = 0; i < steps; i++) {
    Long epoch = now() + ((i + 1) * stepMin * 60000L)
    Integer wx = hourlyIndexFor(epoch)
    tOutSeq[i] = forecastOutdoorTemp(wx) + decayedBias(bias, ((i + 1) * stepMin) as double, BIAS_DECAY_MIN as double)
    solarSeq[i] = maxWallLoadAt(epoch, wx)
  }
  // Coefficients: start from the physics defaults, then use the LEARNED windows-open
  // model from the child device once it has enough good data. rate = b0 + b1*(Tout-Tin) + b2*solar
  double b0 = 0.0d
  double b1 = 1.0d / ((settings.windowOpenTauMin ?: 45) as double)
  double b2 = COLD_K_SOLAR
  com.hubitat.app.ChildDeviceWrapper stats = getStatsDevice()
  if (stats != null) {
    Double n = asDoubleOrNull(stats.currentValue('openSamples'))
    Double cpl = asDoubleOrNull(stats.currentValue('openCoupling'))
    Double r2 = asDoubleOrNull(stats.currentValue('openRSquared'))
    if (n != null && n >= LEARN_MIN_SAMPLES && cpl != null && cpl > 0.0d && r2 != null && r2 >= LEARN_MIN_R2) {
      b0 = asDouble(stats.currentValue('openIntercept'))
      b1 = Math.min(cpl as double, COUPLING_MAX)  // cap for Euler stability
      b2 = Math.max(0.0d, asDouble(stats.currentValue('openSolarGain')))
      logDebug("Using LEARNED windows-open model: b0=${fmt(b0)} b1=${fmt(b1)} b2=${fmt(b2)} (n=${fmt(n)}, R2=${fmt(r2)})")
    } else {
      logDebug("Using physics-default windows-open model (learned not ready: n=${fmt(n)}, R2=${fmt(r2)})")
    }
  }
  return forecastWindowsOpenTrajectory(startTin, tOutSeq, solarSeq, stepMin as double, b0, b1, b2, leadSteps as int)
}

// Builds the human-facing advisory. Notifications are driven by the actionable
// window change (see maybeNotify): they fire only when the windows are NOT already
// in the recommended state, so we never tell you to close windows that are already
// closed (or vice-versa). Messages are phrased as the action to take.
Map buildAdvisory(boolean windowsShouldClose, boolean shadesShouldDraw, boolean humidityVeto = false, String dewPointText = null) {
  String winRec = windowsShouldClose ? 'closed' : 'open'
  String shadeRec = shadesShouldDraw ? 'draw' : 'open'
  String key = "win:${winRec}|shade:${shadeRec}"
  String msg
  if (windowsShouldClose && humidityVeto) {
    String dp = dewPointText ? " (dew point ${dewPointText})" : ''
    msg = "Keep the windows closed - outdoor air is too humid${dp} for the small temperature benefit."
    if (shadesShouldDraw) { msg = "${msg} Draw shades on the sun-exposed side(s)." }
  } else if (windowsShouldClose && shadesShouldDraw) {
    msg = 'Close the windows and draw shades on the sun-exposed sides - opening up would overheat the house soon; let HVAC handle it.'
  } else if (windowsShouldClose) {
    msg = 'Close the windows so HVAC runs efficiently - opening up would overheat the house soon. Shades can stay open (low solar load).'
  } else if (shadesShouldDraw) {
    msg = 'Open the windows for fresh air, but draw shades on the sun-exposed side(s) to limit heat gain.'
  } else {
    msg = 'Open the windows and shades - it will stay comfortable with fresh air and natural light.'
  }
  return [key: key, windowRec: winRec, shadeRec: shadeRec, message: msg]
}

void evaluate() {
  def thermostat = settings.thermostat
  if (thermostat == null) { logWarn('No thermostat configured; cannot evaluate.'); return }

  Double coolSp = asDoubleOrNull(thermostat.currentValue('coolingSetpoint'))
  Double heatSp = asDoubleOrNull(thermostat.currentValue('heatingSetpoint'))
  Double indoorNow = averageTemp(settings.tempSensors)
  if (coolSp == null || heatSp == null || indoorNow == null) {
    logWarn('Missing setpoints or indoor temperature; cannot evaluate yet.')
    return
  }
  double maxDevHot = (settings.maxDevHot ?: 3.0) as double
  double maxDevCold = (settings.maxDevCold ?: 10.0) as double
  Integer hours = (settings.forecastHorizonHours ?: 3) as Integer
  Integer leadHours = (settings.closeLeadHours ?: 1) as Integer

  // What WOULD happen to indoor temperature if we opened the windows right now?
  // traj = [min, max, end, nearTerm], nearTerm = projection at +leadHours.
  double[] traj = forecastWindowsOpen(indoorNow as double, hours, leadHours)
  double projMin = traj[0]
  double projMax = traj[1]
  double projEnd = traj[2]
  double projNear = traj[3]

  // Asymmetric, two-horizon decision: close for heat only when it ends hot AND is
  // already hot in the near term (so we stay open through a warm spell forecast to
  // cool, and close at the last sensible moment as it actually warms). Hysteresis
  // makes it slightly harder to RE-open once we've said close.
  double reopenMargin = (state.lastWindowRec == 'closed') ? WINDOW_HYST : 0.0d
  boolean thermalClose = shouldCloseWindows(projNear, projEnd,
      coolSp as double, heatSp as double, maxDevHot, maxDevCold, reopenMargin)

  // Humidity comfort gate: when outdoor air is muggier than the comfort threshold,
  // opening is vetoed unless it is enough cooler outside to be worth it. Evaluates
  // CURRENT conditions only - the thermal forecast knows nothing about humidity.
  Double dpOut = outdoorDewPoint()
  Double dpIn = indoorDewPoint()
  Double outTempNow = currentOutdoorTemp()
  boolean humidityVeto = false
  if (dpOut != null && outTempNow != null) {
    double threshold = (settings.dewPointComfort ?: (getTempScale() == 'C' ? 15.5 : 60.0)) as double
    if (dpIn != null && (dpIn as double) > threshold) { threshold = dpIn as double }  // muggy house relaxes the gate
    double slope = (settings.dewPointSlope ?: 1.0) as double
    double clearMargin = (state.lastHumidityVeto == true) ? (HUMIDITY_HYST as double) : 0.0d
    humidityVeto = humidityVetoActive(indoorNow as double, outTempNow as double, dpOut as double,
        threshold, slope, clearMargin)
  }
  state.lastHumidityVeto = humidityVeto

  boolean windowsShouldClose = thermalClose || humidityVeto

  boolean cooling = coolingDominant(indoorNow as double, heatSp as double, coolSp as double)

  if (settings.debugLogEnable != false) {
    Map sunNow = sunPositionAt(now())
    Integer wxNow = hourlyIndexFor(now())
    List dniL = (state.wxDni ?: []) as List
    List dhiL = (state.wxDhi ?: []) as List
    String dni = (wxNow >= 0 && wxNow < dniL.size()) ? fmt(dniL[wxNow]) : 'n/a'
    String dhi = (wxNow >= 0 && wxNow < dhiL.size()) ? fmt(dhiL[wxNow]) : 'n/a'
    logDebug("Sun alt=${fmt(sunNow.altitude)} az=${fmt(sunNow.azimuth)}; DNI=${dni} DHI=${dhi} (wxIdx=${wxNow})")
  }

  Map wallRecs = computeWallShadeRecommendations(cooling)
  boolean shadesShouldDraw = wallRecs.values().any { it == 'draw' }
  boolean windowsOpen = anyWindowOpen()

  String dpText = dpOut != null ? "${round1(dpOut)} deg${getTempScale()}" : null
  Map advisory = buildAdvisory(windowsShouldClose, shadesShouldDraw, humidityVeto, dpText)

  // The ACTION to actually take, given the current window state. 'none' when the
  // windows already match the recommendation, so we never nag about a no-op (e.g.
  // "close the windows" when they are already closed).
  String windowAction = 'none'
  if (!windowsShouldClose && !windowsOpen) { windowAction = 'open' }
  else if (windowsShouldClose && windowsOpen) { windowAction = 'close' }

  double hotCeiling = (coolSp as double) + maxDevHot
  logInfo("Advisory [${advisory.key}] | windows-open proj: now ${fmt(indoorNow)} -> +${leadHours}h ${fmt(projNear)} -> +${hours}h ${fmt(projEnd)} (min ${fmt(projMin)}/max ${fmt(projMax)}) deg${getTempScale()}, hotCeiling ${fmt(hotCeiling)}; dewPoint ${fmt(dpOut)}, humidityVeto=${humidityVeto}, shouldClose=${windowsShouldClose}, windowsOpen=${windowsOpen}, action=${windowAction}")

  updateStatusDevice(advisory, projMax, projMin, wallRecs, dpOut, dpIn, humidityVeto)
  maybeNotify(advisory, windowAction)
  state.lastWindowRec = advisory.windowRec
  state.lastEvaluation = nowFormatted()
}

Map computeWallShadeRecommendations(boolean cooling) {
  Map recs = [:]
  Integer count = (settings.wallCount ?: 0) as Integer
  Long t = now()
  Integer wx = hourlyIndexFor(t)
  double threshold = (settings.drawThreshold ?: 60) as double
  double hys = (settings.shadeHysteresis ?: 10) as double
  Map prev = (state.wallDrawn ?: [:]) as Map
  Map nextDrawn = [:]
  for (Integer i = 1; i <= count; i++) {
    String name = settings."wall${i}Name" ?: "Wall ${i}"
    double wallAz = asDouble(settings."wall${i}Azimuth")
    double load = wallEffectiveLoadAt(i, t, wx)
    boolean wasDrawn = (prev["wall${i}"] ?: false) as boolean
    boolean draw = shadeStateWithHysteresis(wasDrawn, load, threshold, hys, cooling)
    nextDrawn["wall${i}"] = draw
    recs[name] = draw ? 'draw' : 'open'
    logDebug("Wall ${i} '${name}': azimuth=${fmt(wallAz)}, effLoad=${fmt(load)}/100, draw=${draw} (cooling=${cooling}, threshold=${fmt(threshold)})")
  }
  state.wallDrawn = nextDrawn
  return recs
}

Boolean anyWindowOpen() {
  if (settings.windowContacts?.any { c -> c.currentValue('contact') == 'open' }) { return true }
  Integer count = (settings.wallCount ?: 0) as Integer
  for (Integer i = 1; i <= count; i++) {
    if (settings."wall${i}Contacts"?.any { c -> c.currentValue('contact') == 'open' }) { return true }
  }
  return false
}

// =============================================================================
// Output: status device + notifications
// =============================================================================

void updateStatusDevice(Map advisory, Double projHigh, Double projLow, Map wallRecs,
                        Double outdoorDp, Double indoorDp, boolean humidityVeto) {
  com.hubitat.app.ChildDeviceWrapper dev = getStatusDevice()
  if (dev == null) { return }
  dev.sendEvent(name: 'advisoryState', value: advisory.key)
  dev.sendEvent(name: 'advisoryMessage', value: advisory.message)
  dev.sendEvent(name: 'windowRecommendation', value: advisory.windowRec)
  dev.sendEvent(name: 'shadeRecommendation', value: advisory.shadeRec)
  List drawList = wallRecs.findAll { k, v -> v == 'draw' }.collect { k, v -> k }
  dev.sendEvent(name: 'shadesToDraw', value: drawList ? drawList.join(', ') : 'none')
  dev.sendEvent(name: 'predictedIndoorHigh', value: round1(projHigh), unit: getTempScale())
  dev.sendEvent(name: 'predictedIndoorLow', value: round1(projLow), unit: getTempScale())
  dev.sendEvent(name: 'humidityVeto', value: humidityVeto ? 'active' : 'inactive')
  if (outdoorDp != null) { dev.sendEvent(name: 'outdoorDewPoint', value: round1(outdoorDp), unit: getTempScale()) }
  if (indoorDp != null) { dev.sendEvent(name: 'indoorDewPoint', value: round1(indoorDp), unit: getTempScale()) }
  // The learned thermal-model stats live on the separate "Solar Shade Thermal Model"
  // child device; mirror its learned time-constant + sample count here for convenience.
  com.hubitat.app.ChildDeviceWrapper stats = getStatsDevice()
  if (stats != null) {
    dev.sendEvent(name: 'modelTimeConstantMin', value: stats.currentValue('openTauMinutes'))
    dev.sendEvent(name: 'modelSamples', value: stats.currentValue('openSamples'))
  }
  dev.sendEvent(name: 'lastEvaluation', value: nowFormatted())
}

void maybeNotify(Map advisory, String windowAction) {
  // 'none' means the windows already match the recommendation - nothing to do, and
  // we clear the tracker so the NEXT genuine action notifies even if it's the same
  // verb as last time.
  if (windowAction == 'none') { state.lastNotifiedAction = 'none'; return }
  if (settings.alertingEnabled == false) { return }
  String reason = pauseReason()
  if (reason) { logDebug("Advisories paused: ${reason}"); return }
  if (!settings.notifiers) { logDebug('No notification devices selected.'); return }

  // Alert ONCE per actionable change: only when the action (open/close) differs from
  // the last one we sent. We never tell you to close already-closed windows (that is
  // 'none' above) and never re-nag the same pending action.
  if (windowAction == state.lastNotifiedAction) {
    logDebug("Window action '${windowAction}' already notified; not repeating.")
    return
  }
  String text = "Solar Shade Advisor: ${advisory.message}"
  settings.notifiers.each { n -> n.deviceNotification(text) }
  state.lastNotifiedAction = windowAction
  logInfo("Notified [action=${windowAction}]: ${text}")
}

// =============================================================================
// Inlined logging / lifecycle helpers (from UtilitiesAndLoggingLibrary)
// =============================================================================

void logError(String message) { if (settings.logEnable != false) { log.error("${app?.label ?: 'Solar Shade Advisor'}: ${message}") } }
void logWarn(String message) { if (settings.logEnable != false) { log.warn("${app?.label ?: 'Solar Shade Advisor'}: ${message}") } }
void logInfo(String message) { if (settings.logEnable != false) { log.info("${app?.label ?: 'Solar Shade Advisor'}: ${message}") } }
void logDebug(String message) { if (settings.logEnable != false && settings.debugLogEnable != false) { log.debug("${app?.label ?: 'Solar Shade Advisor'}: ${message}") } }

void logsOff() {
  logWarn("Logging disabled for ${app?.label}")
  app.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

void debugLogsOff() {
  logWarn("Debug logging disabled for ${app?.label}")
  app.updateSetting('debugLogEnable', [value: 'false', type: 'bool'])
}

String nowFormatted() {
  if (location.timeZone) { return new Date().format('yyyy-MMM-dd h:mm:ss a', location.timeZone) }
  return new Date().format('yyyy-MMM-dd h:mm:ss a')
}

String runEveryCustomMinutes(Integer minutes) {
  String currentSecond = new Date().format('ss')
  String currentMinute = new Date().format('mm')
  return "${currentSecond} ${currentMinute}/${minutes} * * * ?"
}

// =============================================================================
// Inlined async-HTTP retry helpers (from UtilitiesAndLoggingLibrary)
// =============================================================================

Integer getHttpStatusCode(response) {
  if (response == null) { return null }
  def s = response.status
  if (s == null) { return null }
  if (s instanceof Number) { return ((Number) s).intValue() }
  try { return Integer.parseInt(s.toString().trim()) } catch (e) { return null }
}

Boolean isHttpResponseFailure(response) {
  Integer code = getHttpStatusCode(response)
  return response?.hasError() || code == null || code != 200
}

void resetHttpRetryCounter(String stateKey = 'httpRetryAttemptCount') { state[stateKey] = 0 }

Boolean handleAsyncHttpFailureWithRetry(response, String retryMethodName, String stateKey = 'httpRetryAttemptCount') {
  Integer count = (state[stateKey] ?: 0) as Integer
  logError("HTTP request failed (attempt ${count + 1} of ${HTTP_MAX_RETRIES + 1})")
  if (count < HTTP_MAX_RETRIES) {
    Integer delay = HTTP_RETRY_DELAYS[count]
    state[stateKey] = count + 1
    logWarn("Scheduling retry ${count + 1} of ${HTTP_MAX_RETRIES} in ${delay}s")
    runIn(delay, retryMethodName)
    return true
  }
  logError("All ${HTTP_MAX_RETRIES} HTTP retries failed; will retry at next scheduled refresh.")
  state[stateKey] = 0
  return false
}

void executeHttpRetryGet(String callbackMethodName, Map httpParams, String retryMethodName, String stateKey = 'httpRetryAttemptCount') {
  asynchttpGet(callbackMethodName, httpParams)
}

// =============================================================================
// Small conversion / formatting helpers
// =============================================================================

String getTempScale() { return (location?.temperatureScale ?: 'F') as String }

double asDouble(Object v) {
  if (v == null) { return 0.0d }
  if (v instanceof Number) { return ((Number) v).doubleValue() }
  try { return Double.parseDouble(v.toString()) } catch (e) { return 0.0d }
}

Double asDoubleOrNull(Object v) {
  if (v == null) { return null }
  if (v instanceof Number) { return ((Number) v).doubleValue() }
  try { return Double.parseDouble(v.toString()) } catch (e) { return null }
}

String fmt(Object v) {
  if (v == null) { return 'n/a' }
  return String.format('%.2f', asDouble(v))
}

BigDecimal round1(Object v) {
  if (v == null) { return 0.0 as BigDecimal }
  return (Math.round(asDouble(v) * 10.0d) / 10.0d) as BigDecimal
}

// =============================================================================
// Inlined solar-load / occlusion / decision math (pure @CompileStatic)
// =============================================================================
// Note: the online-learning regression now lives in the "Solar Shade Thermal
// Model" child device (Drivers/Component/ThermalModelStatistics.groovy).
// =============================================================================

@CompileStatic
double clampDouble(double value, double lo, double hi) {
  return Math.max(lo, Math.min(hi, value))
}

@CompileStatic
double fToC(double f) { return (f - 32.0d) / 1.8d }

@CompileStatic
double cToF(double c) { return (c * 1.8d) + 32.0d }

// Magnus-formula dew point (Celsius in, Celsius out; valid roughly -45..60C).
// RH is clamped to [1,100] so sensor glitches cannot produce -Infinity.
@CompileStatic
double dewPointC(double tempC, double rhPct) {
  double rh = clampDouble(rhPct, 1.0d, 100.0d)
  double g = Math.log(rh / 100.0d) + ((17.62d * tempC) / (243.12d + tempC))
  return (243.12d * g) / (17.62d - g)
}

@CompileStatic
double solarIncidenceCos(double altitudeDeg, double sunAzimuthDeg, double wallAzimuthDeg) {
  if (altitudeDeg <= 0.0d) { return 0.0d }
  double altRad = Math.toRadians(altitudeDeg)
  double azDiffRad = Math.toRadians(sunAzimuthDeg - wallAzimuthDeg)
  double cosInc = Math.cos(altRad) * Math.cos(azDiffRad)
  return cosInc > 0.0d ? cosInc : 0.0d
}

@CompileStatic
double irradianceOnWall(double dni, double dhi, double altitudeDeg, double sunAzimuthDeg, double wallAzimuthDeg) {
  double beam = dni * solarIncidenceCos(altitudeDeg, sunAzimuthDeg, wallAzimuthDeg)
  double diffuse = DIFFUSE_VERTICAL_FACTOR * dhi
  return beam + diffuse
}

@CompileStatic
double normalizedSolarLoad(double dni, double dhi, double altitudeDeg, double sunAzimuthDeg, double wallAzimuthDeg) {
  double irr = irradianceOnWall(dni, dhi, altitudeDeg, sunAzimuthDeg, wallAzimuthDeg)
  return clampDouble((irr / SOLAR_REF_MAX) * 100.0d, 0.0d, 100.0d)
}

@CompileStatic
double fixedOcclusionFraction(String mode, double partialPercent) {
  if (mode == 'full') { return 1.0d }
  if (mode == 'partial') { return clampDouble(partialPercent / 100.0d, 0.0d, 1.0d) }
  return 0.0d
}

@CompileStatic
double shadowLengthMeters(double structureHeight, double altitudeDeg) {
  if (altitudeDeg < MIN_SUN_ALTITUDE_DEG) { return Double.MAX_VALUE }
  return structureHeight / Math.tan(Math.toRadians(altitudeDeg))
}

@CompileStatic
double shadedOcclusionFraction(double altitudeDeg, double sunAzimuthDeg, double wallAzimuthDeg,
                               double structureHeight, double structureDistance,
                               double structureWidth, double wallWidth, double wallHeight) {
  if (altitudeDeg < MIN_SUN_ALTITUDE_DEG) { return 0.0d }
  double cosAz = Math.cos(Math.toRadians(sunAzimuthDeg - wallAzimuthDeg))
  if (cosAz <= 0.0d) { return 0.0d }
  double d = Math.max(structureDistance, MIN_STRUCTURE_DISTANCE_M)
  double slantDistance = d / cosAz
  double tanAlt = Math.tan(Math.toRadians(altitudeDeg))
  double shadowEdgeHeight = structureHeight - (slantDistance * tanAlt)
  if (shadowEdgeHeight <= 0.0d) { return 0.0d }
  double heightFraction = clampDouble(shadowEdgeHeight / wallHeight, 0.0d, 1.0d)
  double widthFraction = wallWidth > 0.0d ? clampDouble(structureWidth / wallWidth, 0.0d, 1.0d) : 1.0d
  return clampDouble(heightFraction * widthFraction, 0.0d, 1.0d)
}

@CompileStatic
double effectiveSolarLoad(double rawLoad, double occlusionFraction) {
  return rawLoad * (1.0d - clampDouble(occlusionFraction, 0.0d, 1.0d))
}

// Linear decay of the observed-vs-forecast outdoor temperature bias: full at
// minutesAhead=0, zero at/after decayMinutes.
@CompileStatic
double decayedBias(double bias, double minutesAhead, double decayMinutes) {
  if (decayMinutes <= 0.0d || minutesAhead >= decayMinutes) { return 0.0d }
  if (minutesAhead <= 0.0d) { return bias }
  return bias * (1.0d - (minutesAhead / decayMinutes))
}

// Integrates the windows-open response trajectory and returns [min, max, end] over
// the horizon: rate (deg/min) = b0 + b1*(Tout - Tin) + b2*solar. b0/b1/b2 come from
// the learned windows-open model (or physics defaults). A small step relative to
// 1/b1 keeps the explicit Euler integration stable.
// Returns [min, max, end, nearTerm] over the horizon, where nearTerm is the
// projected temperature at step `leadSteps` (the near-term lookahead used to decide
// when it is finally time to close). rate (deg/min) = b0 + b1*(Tout - Tin) + b2*solar.
@CompileStatic
double[] forecastWindowsOpenTrajectory(double tStart, double[] tOutSeq, double[] solarSeq,
                                       double stepMinutes, double b0, double b1, double b2, int leadSteps) {
  double t = tStart
  double mn = t
  double mx = t
  double nearTerm = t
  int n = tOutSeq.length
  for (int i = 0; i < n; i++) {
    double rate = b0 + b1 * (tOutSeq[i] - t) + b2 * solarSeq[i]
    t = t + rate * stepMinutes
    if (t < mn) { mn = t }
    if (t > mx) { mx = t }
    if ((i + 1) == leadSteps) { nearTerm = t }
  }
  if (leadSteps >= n) { nearTerm = t }   // lead beyond horizon -> use the end
  return [mn, mx, t, nearTerm] as double[]
}

// Asymmetric, two-horizon window decision that maximizes open time.
//   hotCeiling = coolSetpoint + maxDevHot (minus a re-open hysteresis margin)
//   coldFloor  = heatSetpoint - maxDevCold (loose; cold is cheap)
// CLOSE for heat only when the projection ENDS hot AND the NEAR-TERM projection
// already breaches the ceiling - so we keep windows open through a temporary warm
// spell that is forecast to cool (evening), and close only at the last sensible
// moment as things actually warm (morning). CLOSE for cold only if the end is below
// the loose floor.
@CompileStatic
boolean shouldCloseWindows(double projNearTerm, double projEnd,
                           double coolSetpoint, double heatSetpoint,
                           double maxDevHot, double maxDevCold, double reopenMargin) {
  double hotCeiling = coolSetpoint + maxDevHot - reopenMargin
  double coldFloor = heatSetpoint - maxDevCold
  boolean tooHot = (projEnd > hotCeiling) && (projNearTerm > hotCeiling)
  boolean tooCold = projEnd < coldFloor
  return tooHot || tooCold
}

// Humidity comfort gate: when outdoor dew point exceeds the comfort threshold,
// opening is vetoed unless the indoor-outdoor temperature advantage covers the
// excess (slope degrees of advantage per degree of excess). Dry air (excess <= 0)
// NEVER vetoes - the thermal forecast stays the only authority there. clearMargin
// is HUMIDITY_HYST while the veto is already active, making it harder to clear
// (same hysteresis pattern as the window reopen margin).
@CompileStatic
boolean humidityVetoActive(double indoorTemp, double outdoorTemp, double outdoorDp,
                           double threshold, double slope, double clearMargin) {
  double excess = outdoorDp - threshold
  if (excess <= 0.0d) { return false }
  double required = slope * excess
  return (indoorTemp - outdoorTemp) < (required + clearMargin)
}

@CompileStatic
boolean shadeStateWithHysteresis(boolean currentlyDrawn, double effectiveLoad, double drawThreshold,
                                 double hysteresis, boolean coolingDominant) {
  if (!coolingDominant) { return false }
  if (currentlyDrawn) { return effectiveLoad > (drawThreshold - hysteresis) }
  return effectiveLoad >= drawThreshold
}
