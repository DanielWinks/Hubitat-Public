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

#include dwinks.UtilitiesAndLogging
#include dwinks.SunPosition

metadata {
  definition (name: 'Sun Posistion', namespace: 'dwinks', author: 'Daniel Winks') {
    capability 'Sensor'
    capability 'Refresh'
    capability 'Configuration'

    attribute 'altitude', 'NUMBER'
    attribute 'azimuth', 'NUMBER'
    attribute 'lastCalculated', 'STRING'
  }
  preferences() {
    section('Automatic Calculations'){
      input name: 'latitude', type: 'text', title: 'Latitude', description: '', required:true, defaultValue: location.latitude
      input name: 'longitude', type: 'text', title: 'Longitude', description: '', required:true, defaultValue: location.longitude
      input 'autoUpdate', 'bool', title: 'Refresh peridocially?', required: true, defaultValue: true
      input 'updateInterval', 'enum', title: 'Sun Position Update Interval', required: true, defaultValue: 1, options:
      [
        1:'1 Minute',
        2:'2 Minutes',
        3:'3 Minutes',
        4:'4 Minutes',
        5:'5 Minutes',
        10:'10 Minutes',
        15:'15 Minutes',
        20:'20 Minutes',
        30:'30 Minutes'
      ]
    }
  }
}

void configure() {
  unschedule()
  refresh()
  if(autoUpdate) {
    schedule(runEveryCustomMinutes(updateInterval as Integer), 'refresh')
  }
}

void refresh() {
  Map position = getPosition(settings.latitude, settings.longitude)
  sendEvent(name: 'altitude', value: position.altitude.setScale(1, BigDecimal.ROUND_HALF_UP))
  sendEvent(name: 'azimuth', value: position.azimuth.setScale(1, BigDecimal.ROUND_HALF_UP))
  sendEvent(name: 'lastCalculated', value: nowFormatted())
}
