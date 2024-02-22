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
  definition (name: 'Virtual Auto Off Switch', namespace: 'dwinks', author: 'Daniel Winks', importUrl: '') {
    capability 'Switch'

  }
  preferences() {
    section(){
      input 'autoOffTime', 'enum', title: 'Duration Before Auto-Off', required: true, defaultValue: 1800, options:
      [
        15:'15 Seconds',
        30:'30 Seconds',
        45:'45 Seconds',
        60:'1 Minute',
        120:'2 Minutes',
        180:'3 Minutes',
        240:'4 Minutes',
        300:'5 Minutes',
        600:'10 Minutes',
        900:'15 Minutes',
        1200:'20 Minutes',
        1800:'30 Minutes',
        2700:'45 Minutes',
        3600:'60 Minutes',
        5400:'90 Minutes',
        7200:'2 Hours',
        14400:'4 Hours',
        21600:'6 Hours',
        28800:'8 Hours',
        43200:'12 Hours',
        57600:'16 Hours',
        86400:'24 Hours'
      ]
    }
  }
}

void initialize() { }
void configure() {
  if(this.device.currentValue('switch', true) == 'on') {scheduleAutoOff()}
}
void scheduleAutoOff() {
  Long offset = now() + ((autoOffTime as Long) * 1000L)
  Date date = new java.util.Date(offset)
  runOnce(date, 'autoOff', [overwrite: true])
}

void on() {
  scheduleAutoOff()
  sendEvent(name: 'switch', value: 'on')
}

void off() {
  unschedule('autoOff')
  sendEvent(name: 'switch', value: 'off')
}

void autoOff() {
  off()
}