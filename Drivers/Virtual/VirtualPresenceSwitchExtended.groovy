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
  definition (name: 'Virtual Presence with Switch Extended', namespace: 'dwinks', author: 'Daniel Winks') {
    capability 'PresenceSensor'
    capability 'Switch'

    command 'arrived'
    command 'departed'
    command 'cancelSchedules'

    attribute 'justArrived', 'ENUM', ['true','false']
    attribute 'justDeparted', 'ENUM', ['true','false']
    attribute 'extendedPresent', 'ENUM', ['true','false']
    attribute 'extendedAway', 'ENUM', ['true','false']
    // attribute 'notRecentlyDeparted', 'BOOL'
    // attribute 'notRecentlyArrived', 'BOOL'
  }
  preferences() {
    section(){
      input 'arrivedDelay', 'enum', title: 'Time wait before processing "Arrived" events', required: true, defaultValue: 0, options:
      [
        0:'0 Seconds',
        5:'5 Seconds',
        15:'15 Seconds',
        30:'30 Seconds',
        60:'1 Minute',
        120:'2 Minutes',
        180:'3 Minutes',
        300:'5 Minutes'
      ]
      input 'departDelay', 'enum', title: 'Time wait before processing "Departed" events', required: true, defaultValue: 0, options:
      [
        0:'0 Seconds',
        5:'5 Seconds',
        15:'15 Seconds',
        30:'30 Seconds',
        60:'1 Minute',
        120:'2 Minutes',
        180:'3 Minutes',
        300:'5 Minutes'
      ]
      input 'justArrivedTime', 'enum', title: 'Time to consider "Just Arrived"', required: true, defaultValue: 180, options:
      [
        60:'1 Minute',
        120:'2 Minutes',
        180:'3 Minutes',
        240:'4 Minutes',
        300:'5 Minutes',
        600:'10 Minutes'
      ]
      input 'justDepartedTime', 'enum', title: 'Time to consider "Just Departed"', required: true, defaultValue: 180, options:
      [
        60:'1 Minute',
        120:'2 Minutes',
        180:'3 Minutes',
        240:'4 Minutes',
        300:'5 Minutes',
        600:'10 Minutes'
      ]
      input 'extendedPresentTime', 'enum', title: 'Time to consider "Extended Present"', required: true, defaultValue: 1800, options:
      [
        900:'15 Minutes',
        1200:'20 Minutes',
        1800:'30 Minutes',
        2700:'45 Minutes',
        3600:'60 Minutes',
        5400:'90 Minutes',
        7200:'120 Minutes',
        14400:'240 Minutes'
      ]
      input 'extendedAwayTime', 'enum', title: 'Time to consider "Extended Away"', required: true, defaultValue: 1800, options:
      [
        900:'15 Minutes',
        1200:'20 Minutes',
        1800:'30 Minutes',
        2700:'45 Minutes',
        3600:'60 Minutes',
        5400:'90 Minutes',
        7200:'120 Minutes',
        14400:'240 Minutes'
      ]
    }
  }
}

void initialize() { configure() }
void configure() { unschedule() }
void cancelSchedules() { unschedule() }

void arrived() { on() }
void departed() { off() }

void on() {
  unschedule('processDeparted')
  if ((settings.arrivedDelay as Long) > 0) {
    runIn(settings.arrivedDelay as Long, 'processArrived')
  } else { processArrived() }
}

void processArrived() {
  extendedOff()
  sendEvent(name: 'presence', value: 'present')
  sendEvent(name: 'switch', value: 'on')
  justArrivedOn()
  runIn(settings.extendedPresentTime as Long, 'extendedPresentOn')
}

void off() {
  unschedule('processArrived')
  if ((settings.departDelay as Long) > 0) {
    runIn(settings.departDelay as Long, 'processDeparted')
  } else { processDeparted() }
}

void processDeparted() {
  extendedOff()
  sendEvent(name: 'presence', value: 'not present')
  sendEvent(name: 'switch', value: 'off')
  justDepartedOn()
  runIn(settings.extendedAwayTime as Long, 'extendedAwayOn')
}

void justArrivedOff() { sendEvent(name: 'justArrived', value: 'false') }
void justArrivedOn() {
  sendEvent(name: 'justArrived', value: 'true')
  runIn(settings.justArrivedTime as Long, 'justArrivedOff')
}

void justDepartedOff() { sendEvent(name: 'justDeparted', value: 'false') }
void justDepartedOn() {
  sendEvent(name: 'justDeparted', value: 'true')
  runIn(settings.justDepartedTime as Long, 'justDepartedOff')
}

void extendedPresentOff() { sendEvent(name: 'extendedPresent', value: 'false') }
void extendedPresentOn() { sendEvent(name: 'extendedPresent', value: 'true') }

void extendedAwayOff() { sendEvent(name: 'extendedAway', value: 'false') }
void extendedAwayOn() { sendEvent(name: 'extendedAway', value: 'true') }

void extendedOff() {
  unschedule('extendedAwayOn')
  unschedule('extendedPresentOn')
  extendedAwayOff()
  extendedPresentOff()
}