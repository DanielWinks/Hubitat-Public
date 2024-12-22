
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
#include dwinks.httpLibrary

metadata {
  definition (name: 'HTTP Air Quality Sensor',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    command 'restartESP'
    capability 'Refresh'
    capability 'Sensor'
    command 'initialize'


    capability "RelativeHumidityMeasurement" // attribute humidity - NUMBER, unit:%rh
    capability "PressureMeasurement" // attribute pressure - NUMBER, unit: Pa || psi
    capability "AirQuality" // attribute airQualityIndex - NUMBER, range:0..500
    capability 'TemperatureMeasurement' // attribute 'temperature', 'NUMBER'

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
    attribute 'dewPointTemperatureF', 'NUMBER'
    attribute 'absoluteHumidity', 'NUMBER'
    attribute 'airQualityIndex', 'ENUM', ['Good','Moderate','Unhealthy for Sensitive Individuals', 'Unhealthy', 'Very Unhealthy', 'Hazardous']
    attribute 'pm25', 'NUMBER'
  }

    preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
    }
  }
}

@Field static final String DEWPOINT = 'sensor-dew_point_temperature'
@Field static final String SEALEVELPRESSURE = 'sensor-sea_level_pressure'
@Field static final String HUMIDITY = 'sensor-humidity'
@Field static final String PM25 = 'sensor-particulate_matter_2_5_m_concentration'
@Field static final String TEMPERATURE = 'sensor-temperature'

@Field static final String DEWPOINT_STATE = '/sensor/dew_point_temperature'
@Field static final String SEALEVELPRESSURE_STATE = '/sensor/sea_level_pressure'
@Field static final String HUMIDITY_STATE = '/sensor/humidity'
@Field static final String PM25_STATE = '/sensor/particulate_matter_2_5_m_concentration'
@Field static final String TEMPERATURE_STATE = '/sensor/temperature'

@Field static final String REFRESH_STATE = '/button/refresh/press'


void refresh() {
  sendQueryAsync(REFRESH_STATE, 'refreshCallback', null)
}

void refreshCallback(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }
}

void parse(message) {
  Map parsedMessage = parseLanMessage(message)
  Map jsonData = parseJson(parsedMessage.body)
  if(jsonData != null) { processJson(jsonData) }
}

void processJson(Map jsonData) {
  logDebug(prettyJson(jsonData))
  if(jsonData?.dew_point_temperature != null) {
    sendEvent(name:'dewPointTemperatureF', value:jsonData?.dewPointTemperatureF, descriptionText:"Dew point temperature is now ${jsonData?.dewPointTemperatureF} °F", isStateChange:true)
  }

  if(jsonData?.pressure != null) {
    sendEvent(name:'pressure', value:jsonData?.pressure, descriptionText:"Sea level pressure is now ${jsonData?.pressure} Pa", isStateChange:true)
  }

  if(jsonData?.humidity != null) {
    sendEvent(name:'humidity', value:jsonData?.humidity, descriptionText:"Humidity is now ${jsonData?.humidity}%", isStateChange:true)
  }

  if(jsonData?.absolute_humidity != null) {
    sendEvent(name:'absoluteHumidity', value:jsonData?.absolute_humidity, descriptionText:"Absolute is now ${jsonData?.absolute_humidity} grams/m^3", isStateChange:true)
  }

  if(jsonData?.pm2 != null) {
    sendEvent(name:'pm25', value:jsonData?.pm2, descriptionText:"Particulate Matter 2.5µm Concentration is now ${jsonData?.pm2}", isStateChange:true)
  }

  if(jsonData?.air_quality != null) {
    sendEvent(name:'airQualityIndex', value:jsonData?.air_quality, descriptionText:"AQI is now ${jsonData?.air_quality}", isStateChange:true)
  }

  if(jsonData?.temperature != null) {
    BigDecimal temp = (jsonData?.temperature * 1.8 + 32).setScale(0, BigDecimal.ROUND_HALF_UP)
    sendEvent(name:'temperature', value:temp, descriptionText:"Temperature is now ${temp} °F", isStateChange:true)
  }
}
