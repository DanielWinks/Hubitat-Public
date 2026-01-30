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

/**
 * ==============================================================================
 * iCal Events Driver for Hubitat
 * ==============================================================================
 *
 * OVERVIEW:
 * This driver connects to an iCal (ICS) calendar URL and retrieves upcoming
 * calendar events. It parses the iCal format, handles recurring events, and
 * exposes event information as device attributes that can be used in Hubitat
 * automations, dashboards, and rules.
 *
 * KEY FEATURES:
 * - Fetches events from any publicly accessible iCal URL (Google Calendar, etc.)
 * - Supports recurring events (daily, weekly, monthly, yearly)
 * - Switch capability turns ON when an event is within the configured alert window
 * - Special "Garbage Collection Mode" for trash/recycling reminders
 * - Human-readable friendly strings for TTS announcements
 * - JSON attributes for advanced rule machine integration
 *
 * HOW IT WORKS:
 * 1. User configures an iCal URL in preferences
 * 2. Driver fetches the iCal file on a scheduled interval
 * 3. iCal data is parsed to extract individual events (VEVENT blocks)
 * 4. Events are filtered to show only upcoming ones within the configured window
 * 5. Device attributes are updated with event details
 * 6. Switch turns ON if next event is within the "alert" window (e.g., today/tomorrow)
 *
 * ==============================================================================
 */

// =============================================================================
// IMPORTS
// =============================================================================
// Java time libraries for date/time manipulation
import java.time.Instant              // Represents a point in time (epoch milliseconds)
import java.time.ZoneId               // Time zone identifier (e.g., "America/New_York")
import java.time.ZonedDateTime        // Date-time with timezone awareness
import java.time.format.DateTimeFormatter  // Formats dates/times into strings

// Hubitat-specific imports
import com.hubitat.app.DeviceWrapper  // Represents a Hubitat device

// Include shared utilities library (provides logging helpers: logDebug, logInfo, etc.)
#include dwinks.UtilitiesAndLoggingLibrary


// =============================================================================
// DRIVER METADATA DEFINITION
// =============================================================================
// The metadata block defines the driver's identity, capabilities, and attributes
// that will be visible in the Hubitat UI and available for automations.
metadata {
  definition(
    name: "iCal Events",           // Display name shown in Hubitat
    namespace: "dwinks",           // Namespace prevents conflicts with other drivers
    author: "Daniel Winks",        // Author information
    importUrl: "",                 // URL for importing updates (HPM)
    singleThreaded: true           // Ensures only one thread executes at a time (prevents race conditions)
  ) {
    // ---------------------------------------------------------------------------
    // CAPABILITIES
    // ---------------------------------------------------------------------------
    // Capabilities define what standard interfaces this driver implements.
    // Each capability brings predefined commands and/or attributes.

    capability "Actuator"                // Indicates this device can perform actions
    capability "Sensor"                  // Indicates this device can sense/report values
    capability "Configuration"           // Adds configure() command for setup
    capability "Initialize"              // Adds initialize() command called at hub startup
    capability "Refresh"                 // Adds refresh() command to manually update data
    capability "Switch"                  // Adds on/off switch (used to indicate event proximity)
    capability "EstimatedTimeOfArrival"  // Standard ETA capability for time-based info

    // ---------------------------------------------------------------------------
    // CUSTOM ATTRIBUTES
    // ---------------------------------------------------------------------------
    // These attributes store calendar and event data that can be used in
    // dashboards, rules, and other automations.

    // Calendar metadata
    attribute "calendarName", "STRING"              // The name of the calendar (from X-WR-CALNAME)

    // Next upcoming event details (simple attributes)
    attribute "nextEventStart", "DATE"              // Start date/time of the next event
    attribute "nextEventEnd", "DATE"                // End date/time of the next event
    attribute "nextEventSummary", "STRING"          // Title/summary of the next event
    attribute "nextEventFriendlyString", "STRING"   // Human-readable announcement string (for TTS)
    attribute "startDateFriendly", "STRING"         // Formatted date like "Friday, January 30, 2026"

    // JSON objects containing full event details (for advanced use cases)
    // These allow Rule Machine and other apps to access all event properties
    attribute "nextEventJson", "JSON_OBJECT"        // Full details of the next event
    attribute "event0Json", "JSON_OBJECT"           // First event in the list
    attribute "event1Json", "JSON_OBJECT"           // Second event in the list
    attribute "event2Json", "JSON_OBJECT"           // Third event in the list
    attribute "event3Json", "JSON_OBJECT"           // Fourth event in the list
    attribute "event4Json", "JSON_OBJECT"           // Fifth event in the list
  }
}


// =============================================================================
// USER PREFERENCES (Configuration Options)
// =============================================================================
// These settings appear in the device configuration page and allow users
// to customize how the driver behaves.
preferences {
  // The URL to the iCal/ICS file (required)
  // Users can get this from Google Calendar, Outlook, etc.
  input(
    "iCalUri",
    "string",
    title: "iCal URL",
    description: "The URL to your iCal (.ics) calendar file",
    required: true
  )

  // Special mode for garbage/recycling collection schedules
  // When enabled, generates more specific messages like "Trash needs taken out tonight"
  input(
    name: 'garbageCollectionScheduleEnabled',
    type: 'bool',
    title: 'Garbage Collection Mode<br/><small>(Special handling for Trash/Recycling calendar entries)</small>',
    required: false,
    defaultValue: false
  )

  // How often to refresh the calendar data from the URL
  input(
    name: 'refreshIntervalMinutes',
    title: 'Refresh Interval',
    description: 'How often to fetch new events from the calendar',
    type: 'enum',
    required: true,
    defaultValue: 60,
    options: [
      5:   '5 Minutes',
      10:  '10 Minutes',
      15:  '15 Minutes',
      30:  '30 Minutes',
      60:  '1 Hour',
      120: '2 Hours',
      180: '3 Hours',
      240: '4 Hours',
      360: '6 Hours'
    ]
  )

  // How many events to expose as individual JSON attributes
  input(
    name: 'numberOfEventsToShow',
    title: 'Number of Events to Display',
    description: 'How many upcoming events to store in device attributes',
    type: 'enum',
    required: true,
    defaultValue: 1,
    options: [
      1: '1 Event',
      2: '2 Events',
      3: '3 Events',
      5: '5 Events'
    ]
  )

  // How far in advance should the switch turn ON to alert about upcoming events
  // This is the "alert window" - events within this window trigger the switch ON
  input(
    name: 'alertWindowDays',
    title: 'Alert Window (Switch ON Duration)',
    description: 'Turn switch ON when an event is within this time window',
    type: 'enum',
    required: true,
    defaultValue: 1,
    options: [
      1:  'Today Only',
      2:  'Today and Tomorrow',
      3:  'Within 3 Days',
      5:  'Within 5 Days',
      7:  'Within 1 Week',
      14: 'Within 2 Weeks'
    ]
  )

  // How far into the future to look for events
  // Events beyond this window are ignored
  input(
    name: 'eventLookAheadDays',
    title: 'Event Look-Ahead Window',
    description: 'How far in the future to look for calendar events',
    type: 'enum',
    required: true,
    defaultValue: 7,
    options: [
      1:   '1 Day',
      2:   '2 Days',
      3:   '3 Days',
      5:   '5 Days',
      7:   '1 Week',
      14:  '2 Weeks',
      28:  '4 Weeks',
      31:  '1 Month',
      56:  '8 Weeks',
      91:  '13 Weeks (3 Months)',
      182: '26 Weeks (6 Months)',
      365: '1 Year'
    ]
  )
}


// =============================================================================
// CONSTANTS - Date/Time Formatters
// =============================================================================
// These formatters define how dates and times are converted to human-readable strings.
// The @Field annotation makes them static (shared across all instances) for efficiency.

// Format: "Friday, January 30, 2026"
@Field static final DateTimeFormatter FRIENDLY_DATE_FORMATTER =
    DateTimeFormatter.ofPattern("EEEE, MMMM dd, YYYY")

// Format: "Friday, January 30, 2026 at 02:30 PM"
@Field static final DateTimeFormatter FRIENDLY_DATETIME_FORMATTER =
    DateTimeFormatter.ofPattern("EEEE, MMMM dd, YYYY 'at' hh:mm a")

// Format: "02:30 PM"
@Field static final DateTimeFormatter FRIENDLY_TIME_FORMATTER =
    DateTimeFormatter.ofPattern("hh:mm a")


// =============================================================================
// HUBITAT LIFECYCLE METHODS
// =============================================================================
// These methods are called automatically by Hubitat at specific points in the
// driver's lifecycle (install, update, initialize, etc.)

/**
 * Called when the driver is first installed on a device.
 * Delegates to configure() to set up the device.
 * Note: The installed() method is provided by UtilitiesAndLoggingLibrary.
 */

/**
 * Called when the device needs to be initialized (e.g., after hub restart).
 * Hubitat calls this automatically when the hub boots up.
 */
void initialize() {
  // Delegate to configure() which handles all setup tasks
  configure()
}

/**
 * Configures the device by clearing old data and setting up scheduled refreshes.
 * Called during installation, updates, and initialization.
 */
void configure() {
  // Clear any stale state data and device attributes
  clearAllStates()

  // Validate that the user has provided a calendar URL
  if (!iCalUri) {
    logWarn "iCal URL not provided. Please configure the calendar URL in device settings."
    return
  }

  // Perform an immediate refresh to get current events
  refresh()

  // Set up the recurring schedule to fetch new events
  setupAutomaticRefreshSchedule()
}


// =============================================================================
// REFRESH & SCHEDULING METHODS
// =============================================================================
// These methods handle fetching calendar data and setting up automatic updates.

/**
 * Manually triggers a refresh of calendar events.
 * This is called by the Refresh capability and can be triggered from dashboards/rules.
 */
void refresh() {
  fetchCalendarEventsFromUrl()
}

/**
 * Fetches the iCal data from the configured URL.
 * Uses asynchronous HTTP to avoid blocking the hub.
 */
void fetchCalendarEventsFromUrl() {
  // Build the HTTP request parameters
  Map httpRequestParams = [
    uri: settings.iCalUri,                         // The calendar URL from settings
    contentType: 'text/html; charset=UTF-8',       // Accept UTF-8 text content
    requestContentType: 'text/html; charset=UTF-8' // Request type
  ]

  // Clear existing state before fetching new data
  clearAllStates()

  // Make an asynchronous HTTP GET request
  // When complete, 'handleCalendarDataResponse' will be called with the response
  asynchttpGet('handleCalendarDataResponse', httpRequestParams)
}

/**
 * Sets up the automatic refresh schedule based on user preferences.
 * Creates a cron schedule to periodically fetch new calendar data.
 */
void setupAutomaticRefreshSchedule() {
  // Get the refresh interval from settings (in minutes)
  Integer intervalMinutes = settings.refreshIntervalMinutes as Integer

  // Remove any existing schedules to prevent duplicates
  unschedule()

  // Build the cron expression based on the interval
  String cronExpression

  if (intervalMinutes < 60) {
    // For intervals less than 1 hour, schedule every N minutes
    // Cron format: seconds minutes hours dayOfMonth month dayOfWeek
    // "0 */15 * ? * *" means: at second 0, every 15 minutes, every hour, any day
    cronExpression = "0 */${intervalMinutes} * ? * *"
  } else {
    // For intervals of 1 hour or more, schedule at minute 55 of every Nth hour
    // This runs slightly before the hour to have fresh data available
    Integer intervalHours = intervalMinutes / 60
    cronExpression = "0 55 */${intervalHours} ? * *"
  }

  // Schedule the refresh method to run on this cron schedule
  schedule(cronExpression, refresh)

  logDebug "Scheduled automatic refresh with cron: ${cronExpression}"
}


// =============================================================================
// SWITCH CAPABILITY METHODS
// =============================================================================
// The switch capability is used to indicate whether an event is upcoming.
// The driver automatically controls the switch - manual control is disabled.

/**
 * Switch ON command - intentionally empty.
 * The switch state is controlled automatically based on upcoming events.
 */
void on() {
  // Intentionally empty - switch is controlled by event proximity
}

/**
 * Switch OFF command - intentionally empty.
 * The switch state is controlled automatically based on upcoming events.
 */
void off() {
  // Intentionally empty - switch is controlled by event proximity
}


// =============================================================================
// ICAL RESPONSE HANDLING (Main Processing Logic)
// =============================================================================
// These methods handle the response from the iCal URL and process the calendar data.

/**
 * Callback method for the async HTTP response.
 * This is called when the calendar data has been fetched from the URL.
 *
 * @param response The HTTP response object containing the iCal data
 * @param data     Additional data passed to the callback (unused here)
 */
void handleCalendarDataResponse(AsyncResponse response, Map data) {
  // Delegate to the main processing method, passing current settings
  processCalendarData(response, data, settings)
}

/**
 * Main method that processes the iCal data and updates device attributes.
 * This parses the iCal format, extracts events, and generates friendly strings.
 *
 * @param httpResponse   The HTTP response containing the raw iCal text
 * @param callbackData   Additional callback data (unused)
 * @param userSettings   The current device settings (preferences)
 */
void processCalendarData(AsyncResponse httpResponse, Map callbackData, LinkedHashMap userSettings) {

  // ---------------------------------------------------------------------------
  // STEP 1: Validate the HTTP response
  // ---------------------------------------------------------------------------
  if (httpResponse.hasError()) {
    logError "HTTP request error: ${httpResponse.getErrorMessage()}"
    return
  }

  if (httpResponse.status != 200) {
    logError "HTTP request returned status ${httpResponse.status} (expected 200 OK)"
    return
  }

  // ---------------------------------------------------------------------------
  // STEP 2: Parse the iCal structure to find event boundaries
  // ---------------------------------------------------------------------------
  // iCal files are plain text with sections delimited by BEGIN/END markers.
  // Each event is wrapped in BEGIN:VEVENT and END:VEVENT tags.

  // Split the response into individual lines for processing
  List<String> allCalendarLines = httpResponse.data.readLines()

  // Find the line numbers where each VEVENT begins and ends
  // This gives us the boundaries of each event block
  List<Number> eventStartLineNumbers = allCalendarLines.findIndexValues { it == "BEGIN:VEVENT" }
  List<Number> eventEndLineNumbers = allCalendarLines.findIndexValues { it == "END:VEVENT" }

  // ---------------------------------------------------------------------------
  // STEP 3: Extract calendar metadata from the header
  // ---------------------------------------------------------------------------
  // The header (lines before the first event) contains calendar-level info
  // like the calendar name (X-WR-CALNAME property).

  if (eventStartLineNumbers.size() > 0) {
    List<String> headerLines = allCalendarLines.subList(0, (eventStartLineNumbers[0] as Integer))

    headerLines.each { String headerLine ->
      // Split each line on ':' to separate property name from value
      String[] lineParts = headerLine.split(':')

      switch (lineParts[0]) {
        case 'X-WR-CALNAME':
          // Found the calendar name - update the device attribute
          // Remove any backslash escape characters
          String calendarName = lineParts[1].replace('\\', '')
          sendEvent(name: "calendarName", value: calendarName)
          logDebug "Calendar name: ${calendarName}"
          break
      }
    }
  }

  // ---------------------------------------------------------------------------
  // STEP 4: Parse each VEVENT block into a structured event map
  // ---------------------------------------------------------------------------
  List<Map> parsedEvents = []

  for (Integer eventIndex = 0; eventIndex < eventStartLineNumbers.size(); eventIndex++) {
    // Create a map to hold this event's properties
    Map eventData = [:]

    // Flag to track if this event has a specific time (vs all-day event)
    Boolean eventHasSpecificTime = false

    // Default timezone if none specified
    String eventTimezone = "America/New_York"

    // Extract just the lines for this event (between BEGIN:VEVENT and END:VEVENT)
    Integer eventStartLine = (eventStartLineNumbers[eventIndex] as Integer) + 1
    Integer eventEndLine = eventEndLineNumbers[eventIndex] as Integer
    List<String> eventLines = allCalendarLines.subList(eventStartLine, eventEndLine)

    // Parse each property line within the event
    eventLines.each { String propertyLine ->
      // Split on ':' to get property name and value
      String[] propertyParts = propertyLine.split(':')
      String propertyName = propertyParts[0]

      switch (propertyName) {
        case 'DESCRIPTION':
          // Event description/notes
          if (propertyParts.size() > 1) {
            eventData.description = propertyParts[1]
          }
          break

        case 'DTSTART;VALUE=DATE':
          // All-day event start date (no time component)
          // Format: DTSTART;VALUE=DATE:20260130
          if (propertyParts.size() > 1) {
            eventData.startDate = parseICalDateString(propertyParts[1])
          }
          break

        case 'DTEND;VALUE=DATE':
          // All-day event end date (no time component)
          if (propertyParts.size() > 1) {
            eventData.endDate = parseICalDateString(propertyParts[1])
          }
          break

        case 'SUMMARY':
          // Event title/summary - may contain colons, so rejoin remaining parts
          if (propertyParts.size() > 1) {
            eventData.summary = propertyParts[1..-1].join(':')
          }
          break

        case 'LOCATION':
          // Event location
          if (propertyParts.size() > 1) {
            eventData.location = propertyParts[1]
          }
          break

        case 'RRULE':
          // Recurrence rule for repeating events
          // Format: RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR
          if (propertyParts.size() > 1) {
            eventData.repeatRule = propertyParts[1]
          }
          break
      }

      // Handle DTSTART with timezone (timed events, not all-day)
      // Format: DTSTART;TZID=America/New_York:20260130T090000
      if (propertyName.startsWith("DTSTART;TZID")) {
        // Extract timezone from the property name
        eventTimezone = propertyName.split('=')[1]
        // Parse the date/time value with the timezone
        eventData.startDate = parseICalDateString(propertyParts[1], eventTimezone)
        eventHasSpecificTime = true
      }
    }

    // -------------------------------------------------------------------------
    // STEP 4a: Handle recurring events
    // -------------------------------------------------------------------------
    // For recurring events, calculate the next occurrence after today
    if (eventData.repeatRule) {
      eventData.startDate = calculateNextRecurrence(
        eventData.startDate as ZonedDateTime,
        eventData.repeatRule as String,
        eventTimezone,
        userSettings.eventLookAheadDays as Integer
      )
    }

    // -------------------------------------------------------------------------
    // STEP 4b: Calculate derived properties if we have a valid start date
    // -------------------------------------------------------------------------
    if (eventData.startDate) {
      // Set end date to next day if not specified (common for all-day events)
      eventData.endDate = eventData.startDate ? eventData.startDate.plusDays(1) : null

      // Convert to epoch milliseconds for sorting
      eventData.startMillis = eventData.startDate.toInstant().toEpochMilli()
      eventData.endMillis = eventData.endDate.toInstant().toEpochMilli()

      // Generate RFC 1123 formatted date strings (standard format)
      eventData.startDateString = eventData.startDate.format(DateTimeFormatter.RFC_1123_DATE_TIME)
      eventData.endDateString = eventData.endDate.format(DateTimeFormatter.RFC_1123_DATE_TIME)

      // Generate time string only if event has a specific time (not all-day)
      if (eventHasSpecificTime) {
        eventData.startTime = eventData.startDate.format(FRIENDLY_TIME_FORMATTER)
      }

      // Generate friendly date string (e.g., "Friday, January 30, 2026")
      eventData.startDateFriendly = eventData.startDate.format(FRIENDLY_DATE_FORMATTER)

      // Only include events that are today or in the future
      if (isDateTodayOrLater(eventData.startDate)) {
        parsedEvents.add(eventData)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // STEP 5: Sort events by start date (earliest first)
  // ---------------------------------------------------------------------------
  // Sort by startMillis in ascending order (soonest events first)
  parsedEvents = parsedEvents.sort { a, b -> a.startMillis <=> b.startMillis }

  // Get the configured number of events to display
  Integer numberOfEventsToDisplay = userSettings.numberOfEventsToShow as Integer

  // ---------------------------------------------------------------------------
  // STEP 6: Build a summary of events occurring on the same day as the first event
  // ---------------------------------------------------------------------------
  // This is used to create announcements like "There are events for A, B, and C today"
  List<Map> eventsOnFirstEventDay = []

  if (parsedEvents.size() > 0) {
    parsedEvents.each { Map event ->
      // Check if this event is on the same day as the first (soonest) event
      if (areDatesOnSameDay(parsedEvents[0].startDate, event.startDate)) {
        eventsOnFirstEventDay.add(event)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // STEP 7: Generate the human-readable announcement string
  // ---------------------------------------------------------------------------
  // This creates a string suitable for TTS announcements or dashboard display
  String friendlyAnnouncementString = generateFriendlyAnnouncementString(
    parsedEvents,
    eventsOnFirstEventDay,
    userSettings.garbageCollectionScheduleEnabled
  )

  // ---------------------------------------------------------------------------
  // STEP 8: Update device attributes with event data
  // ---------------------------------------------------------------------------
  if (parsedEvents.size() > 0 && isDateWithinDays(parsedEvents[0].startDate, userSettings.eventLookAheadDays as Integer)) {
    // We have upcoming events within the look-ahead window

    // Update the friendly announcement string
    sendEvent(name: "nextEventFriendlyString", value: friendlyAnnouncementString)

    // Update next event details
    sendEvent(name: "nextEventJson", value: parsedEvents[0])
    sendEvent(name: "nextEventStart", value: parsedEvents[0].startDate)
    sendEvent(name: "nextEventEnd", value: parsedEvents[0].endDate)
    sendEvent(name: "nextEventSummary", value: parsedEvents[0].summary)
    sendEvent(name: "startDateFriendly", value: parsedEvents[0].startDateFriendly)

    // Update individual event JSON attributes (event0Json, event1Json, etc.)
    for (Integer i = 0; i < numberOfEventsToDisplay; i++) {
      if (i < parsedEvents.size()) {
        sendEvent(name: "event${i}Json", value: parsedEvents[i])
      }
    }

    // Turn the switch ON if the next event is within the alert window
    if (isDateWithinDays(parsedEvents[0].startDate, userSettings.alertWindowDays as Integer)) {
      sendEvent(name: "switch", value: "on")
    } else {
      sendEvent(name: "switch", value: "off")
    }
  } else {
    // No upcoming events within the look-ahead window
    sendEvent(name: "nextEventFriendlyString", value: "No upcoming events")
    sendEvent(name: "switch", value: "off")
  }

  logDebug "Processed ${parsedEvents.size()} upcoming events"
}


// =============================================================================
// FRIENDLY STRING GENERATION
// =============================================================================
// These methods create human-readable announcement strings for TTS and display.

/**
 * Generates a human-readable announcement string describing upcoming events.
 * The output varies based on:
 * - How many events are on the same day
 * - Whether garbage collection mode is enabled
 * - Whether the event is today, tomorrow, or further out
 *
 * @param allEvents              List of all parsed events (sorted by date)
 * @param eventsOnSameDay        Events occurring on the same day as the first event
 * @param isGarbageCollectionMode Whether garbage collection mode is enabled
 * @return A human-readable string suitable for TTS or display
 */
String generateFriendlyAnnouncementString(
    List<Map> allEvents,
    List<Map> eventsOnSameDay,
    Boolean isGarbageCollectionMode
) {
  // Handle case where there are no events
  if (allEvents.size() == 0 || allEvents[0] == null) {
    return "No upcoming events"
  }

  // Get the first (soonest) event
  Map nextEvent = allEvents[0]

  // Build the base announcement string
  String announcement = buildBaseAnnouncementString(eventsOnSameDay)

  // Apply garbage collection mode modifications if enabled
  if (isGarbageCollectionMode) {
    announcement = applyGarbageCollectionFormatting(announcement, nextEvent, eventsOnSameDay)
  } else {
    // Add the timing suffix (today, tomorrow, or specific date)
    announcement = addTimingSuffix(announcement, nextEvent, eventsOnSameDay)
  }

  return announcement
}

/**
 * Builds the base announcement string listing the event summaries.
 *
 * @param eventsOnSameDay Events occurring on the same day
 * @return Base string like "There is an upcoming event for X" or "There are events for X, and Y"
 */
String buildBaseAnnouncementString(List<Map> eventsOnSameDay) {
  if (eventsOnSameDay.size() > 1) {
    // Multiple events on the same day
    String announcement = "There are upcoming events for ${eventsOnSameDay[0].summary}"
    for (Integer i = 1; i < eventsOnSameDay.size(); i++) {
      announcement = announcement + ", and ${eventsOnSameDay[i].summary}"
    }
    return announcement
  } else if (eventsOnSameDay.size() == 1) {
    // Single event
    return "There is an upcoming event for ${eventsOnSameDay[0].summary}"
  }
  return ""
}

/**
 * Applies special formatting for garbage collection reminders.
 * Converts generic event announcements to specific garbage/recycling messages.
 *
 * @param baseAnnouncement The base announcement string
 * @param nextEvent        The next upcoming event
 * @param eventsOnSameDay  Events on the same day
 * @return Modified announcement string for garbage collection
 */
String applyGarbageCollectionFormatting(String baseAnnouncement, Map nextEvent, List<Map> eventsOnSameDay) {
  String announcement = baseAnnouncement

  // Override the announcement based on the event type
  if (nextEvent.summary?.contains("Trash")) {
    announcement = "Trash needs taken out"
  }
  if (nextEvent.summary?.contains("Recycling")) {
    announcement = "Recycling needs taken out"
  }
  if (nextEvent.summary?.contains("Yard")) {
    announcement = "Recycling and Yard Waste needs taken out"
  }

  // Add timing context
  if (isDateToday(nextEvent?.startDate)) {
    // Event is today - needs to go out this morning
    announcement = announcement + " this morning"
  } else if (isDateTomorrow(nextEvent?.startDate)) {
    // Event is tomorrow - put it out tonight
    announcement = announcement + " tonight"
  } else {
    // Event is further out - include the specific date
    announcement = addDateAndTimeSuffix(announcement, nextEvent, eventsOnSameDay)
  }

  return announcement
}

/**
 * Adds timing suffix for non-garbage-collection events.
 *
 * @param baseAnnouncement The base announcement string
 * @param nextEvent        The next upcoming event
 * @param eventsOnSameDay  Events on the same day
 * @return Announcement with timing suffix added
 */
String addTimingSuffix(String baseAnnouncement, Map nextEvent, List<Map> eventsOnSameDay) {
  if (isDateToday(nextEvent?.startDate)) {
    return baseAnnouncement + " today"
  } else if (isDateTomorrow(nextEvent?.startDate)) {
    return baseAnnouncement + " tomorrow"
  } else {
    return addDateAndTimeSuffix(baseAnnouncement, nextEvent, eventsOnSameDay)
  }
}

/**
 * Adds specific date and time information to the announcement.
 *
 * @param baseAnnouncement The base announcement string
 * @param nextEvent        The next upcoming event
 * @param eventsOnSameDay  Events on the same day
 * @return Announcement with date/time suffix
 */
String addDateAndTimeSuffix(String baseAnnouncement, Map nextEvent, List<Map> eventsOnSameDay) {
  if (nextEvent == null || nextEvent.startDate == null) {
    return baseAnnouncement
  }

  String announcement = baseAnnouncement

  if (eventsOnSameDay.size() > 1) {
    // Multiple events - list the times
    announcement = announcement + " on ${nextEvent.startDateFriendly}"
    if (nextEvent.startTime) {
      announcement = announcement + " at ${nextEvent.startTime}"
    }
    for (Integer i = 1; i < eventsOnSameDay.size(); i++) {
      if (eventsOnSameDay[i]?.startTime) {
        announcement = announcement + ", and ${eventsOnSameDay[i].startTime}"
      }
    }
  } else {
    // Single event - just show the date
    announcement = announcement + " on ${nextEvent.startDateFriendly}"
  }

  return announcement
}


// =============================================================================
// ICAL DATE PARSING UTILITIES
// =============================================================================
// These methods convert iCal date strings to Java ZonedDateTime objects.

/**
 * Parses an iCal date or datetime string into a ZonedDateTime object.
 *
 * iCal uses two date formats:
 * - Date only (all-day events): "20260130" (YYYYMMDD)
 * - Date with time: "20260130T090000" (YYYYMMDD'T'HHMMSS)
 *
 * @param iCalDateString The date string from the iCal file
 * @param timezone       The timezone to use (default: America/New_York)
 * @return ZonedDateTime representing the parsed date/time
 */
@groovy.transform.CompileStatic
ZonedDateTime parseICalDateString(String iCalDateString, String timezone = "America/New_York") {
  // Define the expected format: YYYYMMDD'T'HHMMSS followed by timezone ID
  DateTimeFormatter iCalDateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss VV")

  // If the string is just a date (8 chars), append midnight time
  // Date-only format: "20260130" -> "20260130T000000"
  if (iCalDateString.size() <= 8) {
    iCalDateString = iCalDateString + "T000000"
  }

  // Append the timezone and parse
  // Result format: "20260130T090000 America/New_York"
  ZonedDateTime parsedDateTime = ZonedDateTime.parse(iCalDateString + " " + timezone, iCalDateTimeFormatter)

  return parsedDateTime
}

/**
 * Converts a ZonedDateTime to a legacy Java Date object.
 * Useful for compatibility with older Hubitat methods.
 *
 * @param zonedDateTime The ZonedDateTime to convert
 * @return Legacy Date object
 */
@groovy.transform.CompileStatic
Date convertZonedDateTimeToDate(ZonedDateTime zonedDateTime) {
  return Date.from(zonedDateTime.toInstant())
}


// =============================================================================
// RECURRING EVENT CALCULATION
// =============================================================================
// These methods handle iCal RRULE (recurrence rules) to find the next occurrence.

/**
 * Calculates the next occurrence of a recurring event that is today or later.
 *
 * For repeating events, the iCal file contains the original start date and a
 * recurrence rule (RRULE). This method advances the date forward until we find
 * an occurrence that is today or in the future.
 *
 * Supported recurrence frequencies:
 * - FREQ=DAILY   - Event repeats every day
 * - FREQ=WEEKLY  - Event repeats every week
 * - FREQ=MONTHLY - Event repeats every month
 * - FREQ=YEARLY  - Event repeats every year
 *
 * @param originalStartDate   The original start date from the event
 * @param recurrenceFrequency The RRULE frequency string (e.g., "FREQ=WEEKLY")
 * @param timezone            The event's timezone
 * @param lookAheadDays       Maximum days to look ahead
 * @return The next occurrence date, or null if beyond the look-ahead window
 */
@groovy.transform.CompileStatic
ZonedDateTime calculateNextRecurrence(
    ZonedDateTime originalStartDate,
    String recurrenceFrequency,
    String timezone = "America/New_York",
    Integer lookAheadDays = 1
) {
  ZonedDateTime nextOccurrence = originalStartDate

  // Advance the date based on the recurrence frequency until we reach today or later
  switch (recurrenceFrequency) {
    case "FREQ=DAILY":
      // Keep adding days until we reach today or later
      while (isDateBeforeToday(nextOccurrence)) {
        nextOccurrence = nextOccurrence.plusDays(1)
      }
      break

    case "FREQ=WEEKLY":
      // Keep adding weeks until we reach today or later
      while (isDateBeforeToday(nextOccurrence)) {
        nextOccurrence = nextOccurrence.plusDays(7)
      }
      break

    case "FREQ=MONTHLY":
      // Keep adding months until we reach today or later
      while (isDateBeforeToday(nextOccurrence)) {
        nextOccurrence = nextOccurrence.plusMonths(1)
      }
      break

    case "FREQ=YEARLY":
      // Keep adding years until we reach today or later
      while (isDateBeforeToday(nextOccurrence)) {
        nextOccurrence = nextOccurrence.plusYears(1)
      }
      break
  }

  // Return the occurrence only if it's within our look-ahead window
  return isDateWithinDays(nextOccurrence, lookAheadDays) ? nextOccurrence : null
}


// =============================================================================
// DATE COMPARISON UTILITY METHODS
// =============================================================================
// These helper methods compare dates to determine relative timing.
// All methods are CompileStatic for performance optimization.

/**
 * Returns the start of today (midnight) as a ZonedDateTime.
 * This is a helper used by multiple comparison methods.
 *
 * @return ZonedDateTime representing the very start of today
 */
@groovy.transform.CompileStatic
private ZonedDateTime getStartOfToday() {
  return ZonedDateTime.now()
    .withHour(0)
    .withMinute(0)
    .withSecond(0)
    .withNano(0)
}

/**
 * Checks if the given date falls within a specified number of days from now.
 *
 * @param dateToCheck The date to check
 * @param numberOfDays The number of days in the window
 * @return true if the date is between now and (now + numberOfDays)
 */
@groovy.transform.CompileStatic
Boolean isDateWithinDays(ZonedDateTime dateToCheck, Integer numberOfDays) {
  // Calculate the window boundaries
  ZonedDateTime windowStart = getStartOfToday().minusNanos(1)  // Just before midnight
  ZonedDateTime windowEnd = windowStart.plusDays(numberOfDays)

  // Check if date falls within the window (inclusive)
  return dateToCheck >= windowStart && dateToCheck <= windowEnd
}

/**
 * Checks if the given date is more than 7 days in the future.
 *
 * @param dateToCheck The date to check
 * @return true if the date is more than a week away
 */
@groovy.transform.CompileStatic
Boolean isDateMoreThanOneWeekAway(ZonedDateTime dateToCheck) {
  return dateToCheck.isAfter(ZonedDateTime.now().plusWeeks(1))
}

/**
 * Checks if the given date is today or in the future.
 * Used to filter out past events.
 *
 * @param dateToCheck The date to check
 * @return true if the date is today or later
 */
@groovy.transform.CompileStatic
Boolean isDateTodayOrLater(ZonedDateTime dateToCheck) {
  ZonedDateTime startOfToday = getStartOfToday().minusNanos(1)
  return dateToCheck.isAfter(startOfToday)
}

/**
 * Checks if the given date is before today (in the past).
 * Used when calculating recurring event occurrences.
 *
 * @param dateToCheck The date to check
 * @return true if the date is before the start of today
 */
@groovy.transform.CompileStatic
Boolean isDateBeforeToday(ZonedDateTime dateToCheck) {
  return dateToCheck.isBefore(getStartOfToday())
}

/**
 * Checks if the given date is less than 7 days in the future.
 *
 * @param dateToCheck The date to check
 * @return true if the date is within the next week
 */
@groovy.transform.CompileStatic
Boolean isDateWithinOneWeek(ZonedDateTime dateToCheck) {
  return dateToCheck.isBefore(ZonedDateTime.now().plusWeeks(1))
}

/**
 * Checks if the given date falls on today's date.
 *
 * @param dateToCheck The date to check
 * @return true if the date is today
 */
@groovy.transform.CompileStatic
Boolean isDateToday(ZonedDateTime dateToCheck) {
  if (dateToCheck == null) return false

  ZonedDateTime todayStart = getStartOfToday().minusNanos(1)
  ZonedDateTime todayEnd = todayStart.plusDays(1).minusNanos(1)

  return dateToCheck >= todayStart && dateToCheck <= todayEnd
}

/**
 * Checks if the given date falls on tomorrow's date.
 *
 * @param dateToCheck The date to check
 * @return true if the date is tomorrow
 */
@groovy.transform.CompileStatic
Boolean isDateTomorrow(ZonedDateTime dateToCheck) {
  if (dateToCheck == null) return false

  ZonedDateTime tomorrowStart = getStartOfToday().plusDays(1).minusNanos(1)
  ZonedDateTime tomorrowEnd = tomorrowStart.plusDays(1).minusNanos(1)

  return dateToCheck >= tomorrowStart && dateToCheck <= tomorrowEnd
}

/**
 * Checks if two dates fall on the same calendar day.
 * Used to group events by day for announcements.
 *
 * @param referenceDate The reference date (e.g., the first event's date)
 * @param dateToCompare The date to compare against the reference
 * @return true if both dates are on the same day
 */
@groovy.transform.CompileStatic
Boolean areDatesOnSameDay(ZonedDateTime referenceDate, ZonedDateTime dateToCompare) {
  if (referenceDate == null || dateToCompare == null) return false

  ZonedDateTime dayStart = referenceDate.withHour(0).withMinute(0).withSecond(0).withNano(0).minusNanos(1)
  ZonedDateTime dayEnd = dayStart.plusDays(1).minusNanos(1)

  return dateToCompare >= dayStart && dateToCompare <= dayEnd
}
