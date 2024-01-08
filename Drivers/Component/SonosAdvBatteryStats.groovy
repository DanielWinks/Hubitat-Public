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

#include dwinks.UtilitiesAndLoggingLibrary

metadata {
  definition(
    name: 'Sonos Advanced Battery Status',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvBatteryStats.groovy'
  ) {
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'PowerSource' //powerSource - ENUM ["battery", "dc", "mains", "unknown"]
    command 'updateBatteryStatus'

    attribute 'health', 'string'
    attribute 'temperature', 'string'
  }
  preferences() {
    section(){
      input 'autoUpdate', 'bool', title: 'Refresh peridocially?', required: true, defaultValue: true
      input 'updateInterval', 'enum', title: 'Update Interval', required: true, defaultValue: 10, options:
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

void initialize() { configure() }
void configure() {
  unschedule()
  if(settings.autoUpdate) {
    schedule(runEveryCustomSeconds(updateInterval as Integer), 'updateBatteryStatus')
  }
  updateBatteryStatus()
}
void updateBatteryStatus() { parent?.componentUpdateBatteryStatus() }
