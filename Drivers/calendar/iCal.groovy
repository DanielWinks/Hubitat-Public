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

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.hubitat.app.DeviceWrapper

#include dwinks.UtilitiesAndLoggingLibrary

metadata {
  definition (name: "iCal Events",namespace: "dwinks", author: "Daniel Winks", importUrl:"", singleThreaded: true) {
    capability "Actuator"
    capability "Sensor"
    capability "Configuration"
    capability "Initialize"
    capability "Refresh"
    capability "Switch"
    capability "EstimatedTimeOfArrival"

    attribute "calendarName", "STRING"
    attribute "nextEventStart", "DATE"
    attribute "nextEventEnd", "DATE"
    attribute "nextEventSummary", "STRING"
    attribute "nextEventFriendlyString", "STRING"
    attribute "startDateFriendly", "STRING"

    attribute "nextEventJson", "JSON_OBJECT"
    attribute "event0Json", "JSON_OBJECT"
    attribute "event1Json", "JSON_OBJECT"
    attribute "event2Json", "JSON_OBJECT"
    attribute "event3Json", "JSON_OBJECT"
    attribute "event4Json", "JSON_OBJECT"
  }
}

preferences {
  input("iCalUri", "string", title: "iCal Uri")
  input name: 'garbageCollectionSchedule', type: 'bool', title: 'Gabarge Collection Mode </br>(Special Handling for Trash/Recycling entries)', required: false, defaultValue: false
  input name: 'updateInterval', title: 'Refresh update interval', type: 'enum', required: true, defaultValue: 1,
    options: [5:'5 Minutes', 10: '10 Minutes', 15:'15 Minutes', 30: '30 Minutes', 60:'1 hour', 120:'2 Hours', 180:'3 Hours', 240:'4 Hours', 360:'6 Hours']
  input name: 'numEvents', title: 'Number of events to show', type: 'enum', required: true, defaultValue: 1,
    options: [1:'1', 2:'2', 3:'3', 5:'5']
  input name: 'onDuration', title: 'Number of days to alert prior to event ', type: 'enum', required: true, defaultValue: 1,
    options: [1:'Today Only', 2:'Today and Tomorrow', 3:'Within 3 days', 5:'Within 5 days', 7: "This Week", 14: "Two Weeks"]
  input name: 'daysToGet', title: 'Number of days to get events within', type: 'enum', required: true, defaultValue: 1,
    options: [1:'1', 2:'2', 3:'3', 5:'5', 7:'1 Week', 14:'2 Weeks', 28:'4 Weeks', 31:'1 Month', 56:'8 Weeks', 91:'13 Weeks', 182:'26 Weeks', 365:'1 Year']
}

@Field static final DateTimeFormatter friendlyDateFormat = DateTimeFormatter.ofPattern("EEEE, MMMM dd, YYYY")
@Field static final DateTimeFormatter friendlyDateTimeFormat = DateTimeFormatter.ofPattern("EEEE, MMMM dd, YYYY 'at' hh:mm a")
@Field static final DateTimeFormatter friendlyTimeFormat = DateTimeFormatter.ofPattern("hh:mm a")

void processEvents(AsyncResponse response, Map data) {
  processEventsStatic(response, data, settings)
}


//@groovy.transform.CompileStatic
void processEventsStatic(AsyncResponse response, Map data, LinkedHashMap setting) {
  if (response.hasError()) {
    logError "post request error: ${response.getErrorMessage()}"
    return
  } else if (response.status != 200) {
    logError "post request returned HTTP status ${response.status}"
    return
  } else {
    // logDebug "postResponse: ${response.data}"
  }

  List<Map> events = []
  List<String> lines = response.data.readLines()
  // logDebug "Lines: ${lines.size()}"
  List<Number> eventBeginIdx = lines.findIndexValues{ it == "BEGIN:VEVENT" }
  List<Number> eventEndIdx = lines.findIndexValues{ it == "END:VEVENT" }

  // Process header data
  List<String> headerLines = lines.subList(0, (eventBeginIdx[0] as Integer))
  headerLines.each {
    String[] line = it.split(':')
    switch(line[0]) {
      case 'X-WR-CALNAME':
        // logDebug "setting name"
        sendEvent(name: "calendarName", value: line[1].replace('\\',''))
    }
  }

  // Process events
  for (Integer i = 0;i<eventBeginIdx.size();i++) {
    Map event = [:]
    Boolean isDateTime = false
    String tz = "America/New_York"
    List<String> eventLines = lines.subList((eventBeginIdx[i] as Integer) + 1,eventEndIdx[i] as Integer)
    eventLines.each {
      String[] line = it.split(':')
      switch(line[0]) {
        case 'DESCRIPTION':
          if (line.size() > 1) event.description = line[1]
          break
        case 'DTSTART;VALUE=DATE':
          if (line.size() > 1) event.startDate = getDateFromCalEvent(line[1])
          break
        case 'DTEND;VALUE=DATE':
          if (line.size() > 1) event.endDate = getDateFromCalEvent(line[1])
          break
        case 'SUMMARY':
          if (line.size() > 1) event.summary = line[1..-1].join(':')
          break
        case 'LOCATION':
          if (line.size() > 1) event.location = line[1]
          break
        case 'RRULE':
          if (line.size() > 1) event.repeatRule = line[1]
          break
      }

      if (line[0].startsWith("DTSTART;TZID")) {
        tz = line[0].split('=')[1]
        event.startDate = getDateFromCalEvent(line[1], tz)
        isDateTime = true
      }
    }

    if (event.repeatRule) {
//logDebug(getObjectClassName(event.startDate))
//logDebug(getObjectClassName(event.repeatRule))
// logDebug(setting.daysToGet)
      event.startDate = getNextOccurenceAfterNow(event.startDate as ZonedDateTime, event.repeatRule as String, tz, setting.daysToGet as Integer)
    }

    if (event.startDate)  {
      if (setting.garbageCollectionSchedule) {
        // event.startDate = event.startDate.minusDays(1)
      }

      event.endDate = event.startDate ? event.startDate.plusDays(1) : null
      event.startMillis = event.startDate.toInstant().toEpochMilli()
      event.endMillis = event.endDate.toInstant().toEpochMilli()
      event.startDateString = event.startDate.format(DateTimeFormatter.RFC_1123_DATE_TIME)
      event.endDateString = event.endDate.format(DateTimeFormatter.RFC_1123_DATE_TIME)

      if (isDateTime) {
        event.startTime = event.startDate.format(friendlyTimeFormat)
      }
      event.startDateFriendly = event.startDate.format(friendlyDateFormat)

      if (afterToday(event.startDate)) {
        events.add(event)
      }
    }
  }

  events = events.sort{a,b-> b.startMillis<=>a.startMillis}
  events = events.reverse()

  numEvents = setting.numEvents as Integer


  List<Map> eventsForSummary = []
  events.each {
    if (eventOnDay(events[0].startDate, it.startDate)) {
      eventsForSummary.add(it)
    }
  }
  String nefs = ""
  // logDebug "Today Events: ${eventsForSummary}"
  if (eventsForSummary.size() > 1) {
    // logDebug "Multiple Events"
    nefs = "There are upcoming events for ${eventsForSummary[0].summary}"
    for (Integer i = 1;i<eventsForSummary.size();i++) {
      nefs = nefs + ", and " + eventsForSummary[i].summary
    }
  } else {
    if (events[0]) {
      nefs = "There is an upcoming event for ${events[0].summary}"
    }
  }

  if (setting.garbageCollectionSchedule) {
    if (events[0].summary?.contains("Trash")) {
      nefs = "Trash needs taken out"
    }
    if (events[0].summary?.contains("Recycling")) {
      nefs = "Recycling needs taken out"
    }
    if (events[0].summary?.contains("Yard")) {
      nefs = "Recycling and Yard Waste needs taken out"
    }
    if (eventOnToday(events[0]?.startDate)) {
      nefs = nefs + " this morning"
    } else if (eventOnTomorrow(events[0]?.startDate)) {
      nefs = nefs + " tonight"
    } else {
      if (events[0] && eventsForSummary.size() > 1) {
        nefs = nefs + " on ${events[0]?.startDateFriendly} at ${events[0]?.startTime}"
        for (Integer i = 1;i<eventsForSummary.size();i++) {
          nefs = nefs + ", and " + events[i]?.startTime
        }
      } else {
        if (events[0]?.startDate) {
          nefs = nefs + " on ${events[0]?.startDateFriendly}"
        }
      }
    }

  } else if (eventOnToday(events[0]?.startDate)) {
    nefs = nefs + " today"
  } else if (eventOnTomorrow(events[0]?.startDate)) {
    nefs = nefs + " tomorrow"
  } else {
    if (events[0] && eventsForSummary.size() > 1) {
      nefs = nefs + " on ${events[0]?.startDateFriendly} at ${events[0]?.startTime}"
      for (Integer i = 1;i<eventsForSummary.size();i++) {
        nefs = nefs + ", and " + events[i]?.startTime
      }
    } else {
      if (events[0]?.startDate) {
        nefs = nefs + " on ${events[0]?.startDateFriendly}"
      }
    }
  }
  if (events[0] && withinDays(events[0].startDate, setting.daysToGet as Integer)) {
    sendEvent(name: "nextEventFriendlyString", value: nefs)
    sendEvent(name: "nextEventJson", value: events[0])
    sendEvent(name: "nextEventStart", value: events[0].startDate)
    sendEvent(name: "nextEventEnd", value: events[0].endDate)
    sendEvent(name: "nextEventSummary", value: events[0].summary)
    sendEvent(name: "startDateFriendly", value: events[0].startDateFriendly)

    for(Integer i = 0;i< numEvents;i++) {
      sendEvent(name: "event${i}Json", value: events[i])
    }

    if (withinDays(events[0].startDate, setting.onDuration as Integer)) {
      sendEvent(name: "switch", value: "on")
    } else {
      sendEvent(name: "switch", value: "off")
    }
  } else {
    nefs = "."
    sendEvent(name: "nextEventFriendlyString", value: nefs)
    sendEvent(name: "switch", value: "off")
  }
}

void getEvents() {
  Map params = [:]
  params.uri = settings.iCalUri
  params.contentType = 'text/html; charset=UTF-8'
  params.requestContentType = 'text/html; charset=UTF-8'

  clearAllStates()
  asynchttpGet('processEvents', params)
}
void initialize() {configure()}
void configure(){
  clearAllStates()
  if (!iCalUri){
    logWarn "iCal Uri not provided"
    return
  }
  refresh()
  scheduleRefresh()
}

void refresh() {
  getEvents()
}

void scheduleRefresh() {
  interval = settings.updateInterval as Integer
  unschedule()
  if (interval < 60) {
    scheduledRefresh = "0 */${interval} * ? * *"
    schedule(scheduledRefresh, refresh)
  } else if (interval >= 60) {
    scheduledRefresh = "0 55 */${interval/60} ? * *"
    schedule(scheduledRefresh, refresh)
  }
}

void on() {}
void off() {}


@groovy.transform.CompileStatic
ZonedDateTime getDateFromCalEvent(String dateToParse, String tz = "America/New_York") {
  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss VV")
  dateToParse = dateToParse.size() > 8 ? dateToParse : dateToParse + "T000000"
  ZonedDateTime zdt = ZonedDateTime.parse(dateToParse + " " + tz, formatter)
  return zdt
}

@groovy.transform.CompileStatic
Date zonedDateTimeToDate(ZonedDateTime zdt) {
  return Date.from(zdt.toInstant())
}

@groovy.transform.CompileStatic
ZonedDateTime getNextOccurenceAfterNow(ZonedDateTime startDate, String repeatFreq, String tz = "America/New_York", Integer daysToGet = 1) {
  ZonedDateTime nextOccurence = startDate
  switch(repeatFreq) {
    case "FREQ=DAILY":
      while(beforeToday(nextOccurence)) {
        nextOccurence = nextOccurence.plusDays(1)
      }
      break
    case "FREQ=WEEKLY":
      while(beforeToday(nextOccurence)) {
        nextOccurence = nextOccurence.plusDays(7)
      }
      break
    case "FREQ=MONTHLY":
      while(beforeToday(nextOccurence)) {
        nextOccurence = nextOccurence.plusMonths(1)
      }
      break
    case "FREQ=YEARLY":
      while(beforeToday(nextOccurence)) {
        nextOccurence = nextOccurence.plusYears(1)
      }
      break
  }
  return withinDays(nextOccurence, daysToGet) ? nextOccurence : null
}

@groovy.transform.CompileStatic
Boolean withinDays(ZonedDateTime zdt, Integer days) {
  ZonedDateTime zdtNow = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).minusNanos(1)
  ZonedDateTime zdtEnd = zdtNow.plusDays(days)
  return zdt >= zdtNow && zdt <= zdtEnd
}

@groovy.transform.CompileStatic
Boolean moreThan7Days(ZonedDateTime zdt) {
  return zdt.isAfter(ZonedDateTime.now().plusWeeks(1))
}

@groovy.transform.CompileStatic
Boolean afterToday(ZonedDateTime zdt) {
  return zdt.isAfter(ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).minusNanos(1))
}

@groovy.transform.CompileStatic
Boolean beforeToday(ZonedDateTime zdt) {
  return zdt.isBefore(ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0))
}

@groovy.transform.CompileStatic
Boolean lessThan7Days(ZonedDateTime zdt) {
  return zdt.isBefore(ZonedDateTime.now().plusWeeks(1))
}

@groovy.transform.CompileStatic
Boolean eventOnToday(ZonedDateTime zdt) {
  ZonedDateTime zdtDayStart = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).minusNanos(1)
  ZonedDateTime zdtDayEnd = zdtDayStart.plusDays(1).minusNanos(1)
  return zdt >= zdtDayStart && zdt <= zdtDayEnd
}

@groovy.transform.CompileStatic
Boolean eventOnTomorrow(ZonedDateTime zdt) {
  ZonedDateTime zdtDayStart = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).minusNanos(1).plusDays(1)
  ZonedDateTime zdtDayEnd = zdtDayStart.plusDays(1).minusNanos(1)
  return zdt >= zdtDayStart && zdt <= zdtDayEnd
}

@groovy.transform.CompileStatic
Boolean eventOnDay(ZonedDateTime zdtDayForEvents, ZonedDateTime zdtEventDayToCheck) {
  ZonedDateTime zdtDayStart = zdtDayForEvents.withHour(0).withMinute(0).withSecond(0).withNano(0).minusNanos(1)
  ZonedDateTime zdtDayEnd = zdtDayStart.plusDays(1).minusNanos(1)
  return zdtEventDayToCheck >= zdtDayStart && zdtEventDayToCheck <= zdtDayEnd
}

// // @groovy.transform.CompileStatic
// String prettyJsonEvents(events) {
//   logDebug(getObjectClassName(events))
//   events.each {
//     it.startDate = it.startDate.format(friendlyDateFormat)
//     it.endDate = it.endDate.format(friendlyDateFormat)
//   }
//   return JsonOutput.prettyPrint(JsonOutput.toJson(events))
// }
