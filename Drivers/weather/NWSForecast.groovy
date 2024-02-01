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
 **/

#include dwinks.UtilitiesAndLoggingLibrary

@Field static final String baseUrl = "https://api.weather.gov"

metadata {
  definition(name: "NWS Forecast And Alerts", namespace: "dwinks", author: "Daniel Winks", importUrl: "https://raw.githubusercontent.com/DanielWinks/Hubitat/main/drivers/weather/NWSForecast.groovy", singleThreaded: true) {
    capability "Sensor"
    capability "Refresh"

    attribute "temperature", "NUMBER"
    attribute "temperatureHi", "NUMBER"
    attribute "temperatureLo", "NUMBER"
    attribute "probabilityOfPrecipitation", "NUMBER"
    attribute "relativeHumidity", "NUMBER"
    attribute "dewpoint", "NUMBER"
    attribute "detailedForecast", "STRING"
    attribute "windSpeed", "STRING"
    attribute "windDirection", "STRING"
    attribute "generatedAt", "DATE"

    attribute "forecast1h", "JSON_OBJECT"
    attribute "forecast2h", "JSON_OBJECT"
    attribute "forecast3h", "JSON_OBJECT"
    attribute "forecast4h", "JSON_OBJECT"
    attribute "forecast5h", "JSON_OBJECT"
    attribute "forecast6h", "JSON_OBJECT"
    attribute "forecast7h", "JSON_OBJECT"
    attribute "forecast8h", "JSON_OBJECT"
    attribute "forecast9h", "JSON_OBJECT"
    attribute "forecast10h", "JSON_OBJECT"
    attribute "forecast11h", "JSON_OBJECT"
    attribute "forecast12h", "JSON_OBJECT"
    attribute "forecast13h", "JSON_OBJECT"
    attribute "forecast14h", "JSON_OBJECT"
    attribute "forecast15h", "JSON_OBJECT"
    attribute "forecast16h", "JSON_OBJECT"
    attribute "forecast17h", "JSON_OBJECT"
    attribute "forecast18h", "JSON_OBJECT"
    attribute "forecast19h", "JSON_OBJECT"
    attribute "forecast20h", "JSON_OBJECT"
    attribute "forecast21h", "JSON_OBJECT"
    attribute "forecast22h", "JSON_OBJECT"
    attribute "forecast23h", "JSON_OBJECT"
    attribute "forecast24h", "JSON_OBJECT"
    attribute "forecast25h", "JSON_OBJECT"
    attribute "forecast26h", "JSON_OBJECT"
    attribute "forecast27h", "JSON_OBJECT"
    attribute "forecast28h", "JSON_OBJECT"
    attribute "forecast29h", "JSON_OBJECT"
    attribute "forecast30h", "JSON_OBJECT"
    attribute "forecast31h", "JSON_OBJECT"
    attribute "forecast32h", "JSON_OBJECT"
    attribute "forecast33h", "JSON_OBJECT"
    attribute "forecast34h", "JSON_OBJECT"
    attribute "forecast35h", "JSON_OBJECT"
    attribute "forecast36h", "JSON_OBJECT"

    command "initialize"
    command "refreshDetailedForecast"
    command "refreshHourlyForecast"
  }

  section() {
    input name: "latitude", type: "text", title: "Latitude", description: "", required:true, defaultValue: location.latitude
    input name: "longitude", type: "text", title: "Longitude", description: "", required:true, defaultValue: location.longitude
    input name: "updateTime", type: "enum", title: "Scheduled Update Time", description: "", required:true,
      options: [[1:"1:00 AM"],[2:"2:00 AM"],[3:"3:00 AM"],[4:"4:00 AM"],[5:"5:00 AM"],[6:"6:00 AM"]], defaultValue: 4
    input name: "hourlyForecastHours", type: "enum", title: "Hours of hourly forecast to retrieve", description: "", required:true,
      options: [[0:"None"],[2:"2 Hours"],[4:"4 Hours"],[6:"6 Hours"],[8:"8 Hours"],[12:"12 Hours"],[18:"18 Hours"],[18:"18 Hours"],[24:"24 Hours"],[36:"36 Hours"]], defaultValue: 0
  }
}


void initialize() {configure()}
void configure(){
  unschedule()
  clearStates()
  getUri()
  refresh()
  scheduleRefresh()
}

void refresh() {
  refreshDetailedForecast()
  if ((settings.hourlyForecastHours as int) > 0) {
    refreshHourlyForecast()
  }
}

void refreshDetailedForecast(){
  getDetailedForecast()
  getHiAndLo()
}

void refreshHourlyForecast(){
  getHourlyForecast()
}

void scheduleRefresh() {
  scheduledRefresh = "0 0 ${settings.updateTime} ? * *"
  unschedule()
  schedule(scheduledRefresh, refresh)
  if ((settings.hourlyForecastHours as int) > 0) {
    hourlyRefresh = "55 55 * ? * *"
    schedule(hourlyRefresh, "getHourlyForecast")
  }
}

void getDetailedForecast() {
  logDebug "Updating detailed forecast..."
  Map params = [:]
  params.uri = state.forecastUri
  httpGet(params) { resp ->
    if (resp?.success){
      jsonData = parseJson("${resp.getData()}".toString())
      state.units = jsonData?.properties?.units
      periods = jsonData?.properties?.periods
      period = periods?.findAll{it.number == 1}

      eventName = "generatedAt"
      eventValue = toDateTime(jsonData?.properties?.generatedAt)
      sendEvent(name: "${eventName}", value: eventValue, descriptionText: "Updated ${eventName} from NWS")

      eventName = "detailedForecast"
      eventValue = period[eventName][0]
      sendEvent(name: "${eventName}", value: eventValue, descriptionText: "Updated ${eventName} from NWS")

      eventName = "windSpeed"
      eventValue = period[eventName][0]
      sendEvent(name: "${eventName}", value: eventValue, descriptionText: "Updated ${eventName} from NWS")

      eventName = "windDirection"
      eventValue = period[eventName][0]
      sendEvent(name: "${eventName}", value: eventValue, descriptionText: "Updated ${eventName} from NWS")

      eventName = "temperature"
      eventValue = period[eventName][0]
      eventUnit = period.temperatureUnit[0]
      sendEvent(name: "${eventName}", value: eventValue, unit: "°" + eventUnit, descriptionText: "Updated ${eventName} from NWS")

      eventName = "relativeHumidity"
      eventData = period[eventName][0]
      eventValue = eventData.value ?: 0
      sendEvent(name: "${eventName}", value: eventValue, unit: "%", descriptionText: "Updated ${eventName} from NWS")

      eventName = "probabilityOfPrecipitation"
      eventData = period[eventName][0]
      eventValue = eventData.value ?: 0
      sendEvent(name: "${eventName}", value: eventValue, unit: "%", descriptionText: "Updated ${eventName} from NWS")

      eventName = "dewpoint"
      eventData = period[eventName][0]
      eventValue = getDewpoint(period).value
      eventUnit = getDewpoint(period).value
      sendEvent(name: "${eventName}", value: eventValue, unit: eventUnit, descriptionText: "Updated ${eventName} from NWS")
    } else {
      logWarn "Error: ${resp?.status}"
    }
  }
}

void getHourlyForecast() {
  logDebug "Updating hourly forecast..."
  Map params = [:]
  params.uri = state.forecastHourlyUri
  httpGet(params) { resp ->
    if (resp?.success){
      jsonData = parseJson("${resp.getData()}".toString())
      state.hourlyUnits = jsonData?.properties?.units
      periods = jsonData?.properties?.periods

      (1..(settings.hourlyForecastHours as int)).each{ it ->
      Map forecast = [:]
      period = periods?.findAll{p -> p.number == it}
      forecast.startTime = period["startTime"][0]
      forecast.temperature = period["temperature"][0]
      forecast.temperatureUnit = period["temperatureUnit"][0]
      forecast.windSpeed = period["windSpeed"][0]
      forecast.windDirection = period["windDirection"][0]
      forecast.shortForecast = period["shortForecast"][0]
      forecast.probabilityOfPrecipitation = period["probabilityOfPrecipitation"][0]?.value
      forecast.relativeHumidity = period["relativeHumidity"][0]?.value
      forecast.dewpoint = getDewpoint(period)?.value

      sendEvent(name: "forecast${it}h", value: forecast, descriptionText: "Updated forecast${it}h from NWS")
      }
      def temps = periods.findAll{it -> it.number < 13}.collectEntries() { [it.temperature] }.keySet()
      eventSend("temperatureHi", temps.max())
      eventSend("temperatureLo", temps.min())
    } else {
      logWarn "Error: ${resp?.status}"
    }
  }
}

void getHiAndLo( ){
  logDebug "Getting Hi and Lo..."
  Map params = [:]
  params.uri = state.forecastHourlyUri
  httpGet(params) { resp ->
    if (resp?.success){
      jsonData = parseJson("${resp.getData()}".toString())
      state.hourlyUnits = jsonData?.properties?.units
      periods = jsonData?.properties?.periods
      def temps = periods.findAll{it -> it.number < 13}.collectEntries() { [it.temperature] }.keySet()
      eventSend("temperatureHi", temps.max())
      eventSend("temperatureLo", temps.min())
    } else {
      logWarn "Error: ${resp?.status}"
    }
  }
}

Map getDewpoint(period) {
  def eventData = period["dewpoint"][0]
  String eventUnit = period["dewpoint"][0]?.unitCode == "wmoUnit:degC" ? "°C" : "°F"
  Integer eventValue = period["dewpoint"][0]?.value ?: 0
  if (state.units == "us" && eventData?.unitCode == "wmoUnit:degC" && eventData?.value != null) {
    eventValue = (eventValue * 1.8 + 32).intValue()
  } else {
    eventValue = eventValue?.intValue()
  }
  return [value: eventValue, unit: eventUnit]
}

private String getUri() {
  Map params = [:]
  params.uri = "${baseUrl}/points/${settings.latitude},${settings.longitude}"
  httpGet(params) { resp ->
    if (resp?.success){
      jsonData = parseJson("${resp.getData()}".toString())
      state.forecastUri = jsonData?.properties?.forecast
      state.forecastHourlyUri = jsonData?.properties?.forecastHourly
    } else {
      logWarn "Error: ${resp?.status}"
    }
  }
}
