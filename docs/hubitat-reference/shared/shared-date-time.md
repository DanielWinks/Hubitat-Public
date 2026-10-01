# Date and time

- **ID:** `shared-date-time`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.executor.BaseExecutor`

> Use these methods directly in app or driver code; the hub supplies this execution context.

## Methods

| Name | Signature | Description |
|---|---|---|
| `celsiusToFahrenheit` | `BigDecimal celsiusToFahrenheit(BigDecimal val)` | Converts a Celsius temperature to Fahrenheit. Parameters: val - Temperature in degrees Celsius. Returns: Temperature in degrees Fahrenheit. |
| `convertTemperatureIfNeeded` | `String convertTemperatureIfNeeded(BigDecimal value, String scale, Integer precision)` | Converts and rounds a temperature to the hub's configured scale when needed. Parameters: value - Temperature value in the scale named by scale. scale - Source scale, C or F, case-i |
| `fahrenheitToCelsius` | `BigDecimal fahrenheitToCelsius(BigDecimal val)` | Converts a Fahrenheit temperature to Celsius. Parameters: val - Temperature in degrees Fahrenheit. Returns: Temperature in degrees Celsius. |
| `formatActivityDateTime` | `String formatActivityDateTime(def date)` | Formats an activity timestamp in the location time zone. Parameters: date - Date to format. Returns: Activity timestamp in the location's selected date and time format. Examples: y |
| `formatActivityDateTimeShort` | `String formatActivityDateTimeShort(def date)` | Formats a short activity timestamp in the location time zone. Parameters: date - Date to format. Returns: Short activity timestamp in the location's selected date and time format.  |
| `formatDate` | `String formatDate(Date date)` | Formats a date in the location time zone. Parameters: date - Date to format. Returns: Date in the location's selected date format. Examples: M/d/yyyy, d/M/yyyy, or yyyy/M/d, depend |
| `formatShortDate` | `String formatShortDate(Date date)` | Formats a short date in the location time zone. Parameters: date - Date to format. Returns: Short date in the location's selected date format. Examples: d/M, M/d, or M/d, depending |
| `formatTimeHourMinute` | `String formatTimeHourMinute(Date date)` | Formats a time with hours and minutes in the location time zone. Parameters: date - Date to format. Returns: Time in the location's selected 12-hour or 24-hour format. Examples: h: |
| `formatTimeHourMinuteSecond` | `String formatTimeHourMinuteSecond(Date date)` | Formats a time with hours, minutes, and seconds in the location time zone. Parameters: date - Date to format. Returns: Time in the location's selected 12-hour or 24-hour format. Ex |
| `formatTimeHourMinuteSecondMillis` | `String formatTimeHourMinuteSecondMillis(Date date)` | Formats a time with hours, minutes, seconds, and milliseconds in the location time zone. Parameters: date - Date to format. Returns: Time with milliseconds in the location's select |
| `getNextSunrise` | `Date getNextSunrise(TimeZone tz = null, int offsetMinutes = 0)` | Finds the next sunrise that occurs after the current instant. Parameters: tz - Time zone used to advance the calendar; the hub location's time zone is used for calculation. offsetM |
| `getNextSunset` | `Date getNextSunset(TimeZone tz = null, int offsetMinutes = 0)` | Finds the next sunset that occurs after the current instant. Parameters: tz - Time zone used to advance the calendar; the hub location's time zone is used for calculation. offsetMi |
| `getSunriseAndSunset` | `Map getSunriseAndSunset(Map options = null)` | Calculates sunrise and sunset for the hub location and requested date. Parameters: options - Optional calculation settings. date: Date, optional; calculation date, defaults to the  |
| `getTemperatureScale` | `String getTemperatureScale()` | Returns the temperature scale configured for the hub location. Returns: Location temperature scale, such as C or F. |
| `getTodaysSunrise` | `Date getTodaysSunrise(TimeZone tz = null)` | Calculates sunrise for today's date at the hub location. Parameters: tz - Time zone argument; the current implementation does not apply it to the calculation calendar. Returns: Sun |
| `getTodaysSunset` | `Date getTodaysSunset(TimeZone tz = null)` | Calculates sunset for today's date at the hub location. Parameters: tz - Time zone argument; the current implementation does not apply it to the calculation calendar. Returns: Suns |
| `getTomorrowsSunrise` | `Date getTomorrowsSunrise(TimeZone tz = null)` | Calculates sunrise for tomorrow at the hub location. Parameters: tz - Time zone used to set the calculation calendar; the hub location's time zone is used by the sunrise calculator |
| `getTomorrowsSunset` | `Date getTomorrowsSunset(TimeZone tz = null)` | Calculates sunset for tomorrow at the hub location. Parameters: tz - Time zone used to set the calculation calendar; the hub location's time zone is used by the sunset calculator.  |
| `isTimeStringValid` | `boolean isTimeStringValid(String timeValue)` | Tests whether a string uses the platform's accepted time format. Parameters: timeValue - Time string to validate. Returns: True when the value is a valid time string. |
| `now` | `long now()` | Returns the current system time. Returns: Current time in milliseconds since the Unix epoch. |
| `timeOfDayIsBetween` | `boolean timeOfDayIsBetween(Date start, Date stop, Date value, TimeZone timeZone = null)` | Tests whether the value instant falls inclusively between the start and stop instants. The time zone is applied to the calendars, but comparison uses the complete Date instants. Pa |
| `timeOffset` | `Long timeOffset(Number minutes)` | Converts a number of minutes to milliseconds. Parameters: minutes - Offset in minutes. Returns: Offset in milliseconds. |
| `timeOffset` | `Long timeOffset(String hoursAndMinutesString)` | Converts a signed hours:minutes string to milliseconds. Parameters: hoursAndMinutesString - Offset in signed hours:minutes form. Returns: Offset in milliseconds, or zero when the s |
| `timeToday` | `Date timeToday(String timeString, TimeZone timeZone = null)` | Builds a Date for a time on the current day in the selected time zone. Parameters: timeString - Empty for the current time, HH:mm, or an ISO date-time string whose date portion is  |
| `timeTodayAfter` | `Date timeTodayAfter(String startTimeString, String timeString, TimeZone timeZone = null)` | Returns a local time on today or tomorrow that does not precede the start time. Parameters: startTimeString - Start time on the current date. timeString - Desired time on the curre |
| `toDateTime` | `Date toDateTime(String dateTimeString)` | Parses a platform date-time string. Parameters: dateTimeString - Date-time text accepted by HeUtils.toDateTime. Returns: Parsed Date. |
