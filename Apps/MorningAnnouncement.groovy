// =============================================================================
// MORNING ANNOUNCEMENT APP
// =============================================================================
// Author: Daniel Winks
// Description: Generates personalized morning announcements by combining
//              weather reports, weather alerts, and calendar events, then
//              using Google Gemini AI to create natural, engaging announcements.
//
// Features:
//   - Device-based inputs (weather, alerts, calendar)
//   - Integration with Gemini Text Rewriter app
//   - Scheduled generation (configurable time)
//   - Manual generation button
//   - HTTP webhook endpoint for external triggering
//   - Global variable storage for Rule Machine access
//   - HTTP endpoint to retrieve latest announcement
// =============================================================================

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
 **/

// Include the utilities and logging library
#include dwinks.UtilitiesAndLoggingLibrary

// Import required classes
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field
import com.hubitat.app.DeviceWrapper
import com.hubitat.hub.domain.Event
import java.text.SimpleDateFormat
import java.util.Calendar

// =============================================================================
// GLOBAL CONSTANTS
// =============================================================================

@Field static final String GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models'

@Field static final Map GEMINI_MODELS = [
  'gemini-3-pro-preview': 'Gemini 3 Pro (Most Powerful, Reasoning)',
  'gemini-3-flash-preview': 'Gemini 3 Flash (Fast, High Scale)',
  'gemini-2.5-flash': 'Gemini 2.5 Flash (Fast, Balanced)',
  'gemini-2.5-pro': 'Gemini 2.5 Pro (Reasoning, Complex Tasks)',
  'gemini-2.5-flash-lite': 'Gemini 2.5 Flash-Lite (Fastest, Cost-Efficient)',
  'gemini-2.0-flash': 'Gemini 2.0 Flash (Legacy)'
]

@Field static final Map AI_MODES = [
  'off': 'Off (use plain concatenated text)',
  'singlePass': 'Single Pass (legacy: send to Gemini Text Rewriter app via location event)',
  'multiStage': 'Multi-Stage Chain (recommended: focused weather + calendar + weave passes via direct API)'
]

@Field static final String DEFAULT_WEATHER_PROMPT = '''You are a friendly local weather presenter writing a single short paragraph for a morning text-to-speech announcement.

Summarize the weather forecast clearly and naturally. If weather alerts are present, lead with them and make them stand out — do not omit critical information. Use spoken-friendly phrasing: say "75 degrees" not "75", "8 AM" not "08:00", spell out abbreviations like "NWS" as "National Weather Service".

Output rules:
- Return only the paragraph. No preamble, no headers, no commentary.
- One paragraph, ideally 2-4 sentences (longer if there is an active weather alert).
- Do not mention calendar events; another stage handles those.
- Do not greet the listener; another stage handles greetings.'''

@Field static final String DEFAULT_CALENDAR_PROMPT = '''You are summarizing today's calendar events for a morning text-to-speech announcement.

Use the DATE ANCHOR provided above as your absolute reference for what "today" and "tomorrow" mean. For every event, resolve its date to a relative phrase using the anchor:
- Same date as TODAY -> "today"
- Same date as TOMORROW -> "tomorrow"
- Within THIS WEEK -> "this <weekday>" (e.g., "this Friday")
- Within NEXT WEEK -> "next <weekday>" (e.g., "next Monday")
- Further out -> "<weekday>, <month> <day>" (e.g., "Saturday, May ninth")

CRITICAL: Do not assume an event is today just because it appears in the list. Read each event's actual date carefully and compare it to the DATE ANCHOR. Do not confuse today's date with event dates.

Spoken-friendly formatting:
- Times: "2 PM" not "14:00", "8:30 AM" not "08:30"
- Dates spoken out: "May ninth" not "5/9", "January first" not "01/01"
- List events in chronological order

Output rules:
- Return only the calendar summary. No preamble, no headers, no commentary.
- If there are no events for today, say so in one short sentence and stop.
- Do not mention weather; another stage handles that.
- Do not greet the listener; another stage handles greetings.'''

@Field static final String DEFAULT_WEAVER_PROMPT = '''You are a warm, upbeat morning radio host weaving together pre-written segments into a single text-to-speech announcement.

You will receive a WEATHER SEGMENT and a CALENDAR SEGMENT, each already correctly worded. Your job is to:
1. Open with a brief, cheerful greeting (e.g., "Good morning!"). Do not state the date explicitly unless reading a calendar event date.
2. Smoothly present the weather segment, then transition into the calendar segment.
3. Close with a short positive thought or encouragement.

CRITICAL RULES:
- Do NOT change facts, numbers, dates, times, temperatures, or event names from the segments. Only adjust transitions and connective phrasing.
- Do NOT re-resolve dates. The CALENDAR SEGMENT has already correctly resolved dates relative to today; preserve its phrasing.
- Keep the result under 2 minutes spoken (roughly 250-300 words max).
- If a segment is missing or empty, gracefully omit it and adjust transitions.
- Return only the final announcement text. No headers, no commentary, no markdown.'''

/**
 * definition() - Defines app metadata for Hubitat
 */
definition(
  name: 'Morning Announcement',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Generate personalized morning announcements from weather, alerts, and calendar events using AI',
  category: 'Utility',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  singleThreaded: false,
  oauth: true,
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/MorningAnnouncement.groovy'
)

// =============================================================================
// PREFERENCES / CONFIGURATION UI
// =============================================================================

preferences {
  page(name: 'mainPage')
  page(name: 'generateNowPage')
}

Map mainPage() {
  dynamicPage(name: 'mainPage', title: 'Morning Announcement Configuration', install: true, uninstall: true) {
    section('<b>Input Devices</b>') {
      paragraph 'Select devices and their attributes that contain the information for your morning announcement.'

      input 'weatherReportDevice', 'capability.sensor', title: '<b>Weather Report Device</b>', required: false, multiple: false

      input 'weatherAlertsDevice', 'capability.sensor', title: '<b>Weather Alerts Device</b>', required: false, multiple: false

      input 'calendarEventsDevice', 'capability.sensor', title: '<b>Calendar Events Device</b>', required: false, multiple: false
    }

    section('<b>AI Enhancement Mode</b>') {
      input 'aiMode', 'enum',
        title: 'AI Mode',
        options: AI_MODES,
        required: true,
        defaultValue: 'multiStage',
        submitOnChange: true,
        description: 'How (or whether) to use Gemini to enhance the announcement.'
      paragraph '''<small><b>Multi-Stage Chain</b> (recommended): three focused Gemini calls — one for weather, one for calendar, one to weave them together. Calendar dates are anchored to today's date to prevent date confusion. Requires a Gemini API key configured below.<br><br><b>Single Pass</b> (legacy): sends one combined prompt to the Gemini Text Rewriter app via location event. Requires the Gemini Text Rewriter app installed.<br><br><b>Off</b>: returns plain concatenated text with no AI enhancement.</small>'''
    }

    if (settings.aiMode == 'multiStage') {
      section('<b>Direct Gemini API Configuration</b> (Multi-Stage Chain)') {
        paragraph 'Get your API key from: <a href="https://aistudio.google.com/app/apikey" target="_blank">Google AI Studio</a>'
        input 'geminiApiKey', 'text',
          title: 'Gemini API Key',
          required: true,
          description: 'API key used directly by this app for the multi-stage chain'
        input 'geminiModel', 'enum',
          title: 'Gemini Model',
          options: GEMINI_MODELS,
          required: true,
          defaultValue: 'gemini-2.5-flash'
        input 'temperature', 'decimal',
          title: 'Temperature (0.0-1.0)',
          required: false,
          defaultValue: 0.6,
          range: '0.0..1.0',
          description: 'Lower = more focused, higher = more creative. 0.6 is a good balance for announcements.'
        input 'maxTokensPerStage', 'number',
          title: 'Max Tokens per Stage',
          required: false,
          defaultValue: 800,
          range: '100..4096',
          description: 'Token cap for each individual stage call (weather, calendar, weaver).'
        input 'stageDelaySeconds', 'number',
          title: 'Delay Between Stages (seconds)',
          required: false,
          defaultValue: 3,
          range: '0..30',
          description: 'Spacing between Gemini calls to stay under rate limits (free tier ~15 RPM).'
      }

      section('<b>Stage Prompts</b> (Multi-Stage Chain)') {
        paragraph 'Each stage has a focused prompt. Defaults are tuned for TTS announcements; edit only if you know what you are doing.'
        input 'weatherStagePrompt', 'text',
          title: 'Stage A: Weather Prompt',
          required: false,
          defaultValue: DEFAULT_WEATHER_PROMPT,
          description: 'Instructions for summarizing weather + alerts.'
        input 'calendarStagePrompt', 'text',
          title: 'Stage B: Calendar Prompt',
          required: false,
          defaultValue: DEFAULT_CALENDAR_PROMPT,
          description: 'Instructions for summarizing calendar events. The DATE ANCHOR block is prepended automatically.'
        input 'weaverStagePrompt', 'text',
          title: 'Stage C: Weaver Prompt',
          required: false,
          defaultValue: DEFAULT_WEAVER_PROMPT,
          description: 'Instructions for combining the weather and calendar segments into the final announcement.'
      }
    }

    if (settings.aiMode == 'singlePass') {
      section('<b>Single Pass Configuration</b> (Legacy)') {
        input 'maxTokens', 'number', title: 'Max Tokens for AI Response', required: false, defaultValue: 2048, description: 'Maximum number of tokens to allow for the AI-generated announcement (default: 2048)'
        paragraph '''<small>Single Pass mode communicates with your Gemini Text Rewriter app via location events. Make sure you have the Gemini Text Rewriter app installed and configured.</small>'''
        input 'customInstructions', 'text',
          title: 'Instructions for AI (single combined prompt)',
          required: true,
          defaultValue: '''
            Create a warm, friendly morning announcement from the following information.
            Keep it natural and conversational, formatted like a news caster would be announcing.
            Include:
            1. A cheerful greeting appropriate for the time of day
            2. Today's weather forecast in a brief, easy-to-understand way
            3. Any weather alerts (if present) with appropriate emphasis
            4. Today's calendar events (if any) in a helpful reminder format
            5. A positive closing thought or encouragement
            Keep the tone upbeat and informative.
            Make it feel personal and engaging, not robotic.
            The input text contains sections, named "WEATHER FORECAST:", "WEATHER ALERTS:", and "UPCOMING CALENDAR EVENTS:"...
            do not leave these in verbatim. Reword the announcement so it flows together nicely as if it were being announced by a news caster.
            Pay special attention to weather forecasts, as these will be read aloud, so ensure temperature units and conditions are clear.
            For things like temperature, include the word "degrees" after the number for clarity, such as "75 degrees" rather than just "75".
            For times, reformat them for spoken TTS (e.g., "8 AM" rather than "08:00").
            For dates, reformat them for spoken TTS (e.g., "January First" rather than "01/01", or January thirteenth rather than "13th").
            If any section is missing or empty, omit it gracefully from the announcement.
            There are calendar events at the end; summarize them briefly and clearly.
            Do not assume the date of the calendar event is today—read the event details carefully.
            You will be provided with today's date implicitly (formatted like "Today's date is..."); use it to contextualize calendar events.
            Do not announce the date explicitly unless it is part of a calendar event, except to say "today is..."
            Make sure not to confuse today's date with any dates mentioned in calendar events.
            Again, you will be provided with today's date implicitly. Do not confuse it with dates mentioned in calendar events. This is extremely important.
            Ensure the entire announcement is concise, ideally under 2 minutes when spoken aloud.
            This will be a text-to-speech announcement, so clarity and natural phrasing are key, as well as spelling out any acronyms, numbers, or abbreviations for proper pronunciation.
            If there is a weather alert, make sure it stands out in the announcement and is clearly communicated, as this is critical information for the listener.
            Do not omit weather alerts if they are present, even if they are long. Summarize them as best as possible while ensuring the critical information is conveyed.
            ''',
          description: 'This prompt guides the AI on how to structure and present your morning announcement.'
      }
    }

    section('<b>Scheduling</b>') {
      input 'enableSchedule', 'bool', title: 'Enable Automatic Generation', defaultValue: true, submitOnChange: true
      if (enableSchedule) {
        input 'scheduledTime', 'time', title: 'Generation Time', required: true, defaultValue: '07:00'
      }
    }

    section('<b>Output Settings</b>') {
      input 'globalVariableName', 'text',
        title: 'Global Variable Name',
        required: true,
        defaultValue: 'morningAnnouncement',
        description: 'The global variable name for Rule Machine access (e.g., %globalVars.morningAnnouncement%)'
    }

    section('<b>Testing & Manual Control</b>') {
      if (state.lastAnnouncement) {
        paragraph "<b>Last Generated:</b> ${state.lastGenerated ?: 'Never'}<br><b>Last Announcement:</b><br>${state.lastAnnouncement}"
      }
      href(name: 'generateNow', title: 'Generate Announcement Now', description: 'Click to test generation', page: 'generateNowPage')
    }

    section('<b>Webhook Access</b>') {
      if (state.accessToken) {
        paragraph """
          <b>Local URL:</b><br>
          <small>${getLocalUri()}</small><br><br>
          <b>Cloud URL:</b><br>
          <small>${getCloudUri()}</small>
        """
      } else {
        paragraph 'Webhook URLs will appear here after saving.'
      }
    }

    section {
      input 'logEnable', 'bool', title: 'Enable Logging', defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable Debug Logging', defaultValue: false
    }
  }
}

Map generateNowPage() {
  dynamicPage(name: 'generateNowPage', title: 'Generate Announcement', nextPage: 'mainPage') {
    section {
      paragraph 'Generating announcement...'
      generateAnnouncement()
      if (state.lastAnnouncement) {
        paragraph "<b>Generated at ${state.lastGenerated}</b><br><br>${state.lastAnnouncement}"
      } else {
        paragraph 'Generation failed. Check logs for details.'
      }
    }
  }
}

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================

void configure() {
  logInfo('Configuring Morning Announcement app')
  unsubscribe()
  unschedule()

  // Subscribe to location events for Gemini responses
  subscribe(location, 'geminiRewriteResponse', 'handleGeminiResponseEvent')

  // Subscribe to location events for all completed Gemini text rewrites
  subscribe(location, 'geminiTextRewritten', 'handleGeminiTextRewrittenEvent')

  initialize()
}

void initialize() {
  logInfo('Initializing Morning Announcement app')

  // Create access token for webhooks
  tryCreateAccessToken()

  // Set up schedule if enabled
  if (settings.enableSchedule && settings.scheduledTime) {
    schedule(settings.scheduledTime, 'generateAnnouncement')
    logInfo("Scheduled announcement generation at ${settings.scheduledTime}")
  }

  // Initialize state
  if (state.lastAnnouncement == null) {
    state.lastAnnouncement = ''
    state.lastGenerated = 'Never'
  }
}

// =============================================================================
// WEBHOOK / HTTP ENDPOINT MAPPINGS
// =============================================================================

mappings {
  path('/generate') {
    action: [
      GET: 'generateWebhook',
      POST: 'generateWebhook'
    ]
  }
  path('/getAnnouncement') {
    action: [
      GET: 'getAnnouncementWebhook'
    ]
  }
}

/**
 * generateWebhook() - HTTP endpoint to trigger announcement generation
 */
Map generateWebhook() {
  try {
    logDebug('Generate webhook triggered')
    generateAnnouncement()

    return render(
      contentType: 'application/json',
      data: JsonOutput.toJson([
        success: true,
        announcement: state.lastAnnouncement,
        generated: state.lastGenerated
      ]),
      status: 200
    )
  } catch (Exception e) {
    logError("Webhook error: ${e.message}")
    return render(
      contentType: 'application/json',
      data: JsonOutput.toJson([success: false, error: e.message]),
      status: 500
    )
  }
}

/**
 * getAnnouncementWebhook() - HTTP endpoint to retrieve last announcement
 */
Map getAnnouncementWebhook() {
  try {
    return render(
      contentType: 'application/json',
      data: JsonOutput.toJson([
        announcement: state.lastAnnouncement ?: '',
        generated: state.lastGenerated ?: 'Never'
      ]),
      status: 200
    )
  } catch (Exception e) {
    logError("Webhook error: ${e.message}")
    return render(
      contentType: 'application/json',
      data: JsonOutput.toJson([error: e.message]),
      status: 500
    )
  }
}

// =============================================================================
// CORE FUNCTIONALITY
// =============================================================================

/**
 * generateAnnouncement() - Main entry point for generating a morning announcement.
 * Dispatches to one of three flows based on settings.aiMode:
 *   - 'multiStage' (default): focused weather + calendar + weave Gemini calls (direct API)
 *   - 'singlePass'         : legacy single-prompt path via Gemini Text Rewriter (location event)
 *   - 'off'                : store the plain concatenated text as-is
 * Backwards compat: if aiMode is unset and the legacy useGeminiRewriter flag is true,
 * defaults to 'singlePass' to preserve existing installations.
 */
void generateAnnouncement() {
  logInfo('Generating morning announcement...')

  try {
    String mode = resolveAiMode()
    logDebug("Resolved aiMode: ${mode}")

    // Gather input data from devices (shared across all modes)
    String todayDate = "Today is ${new Date().format('MMMM dd, yyyy')}."
    String weatherReport = getDeviceAttributeValue(settings.weatherReportDevice, 'detailedForecast')
    String weatherAlerts = getDeviceAttributeValue(settings.weatherAlertsDevice, 'alertsFriendlyText')
    String calendarEvents = getDeviceAttributeValue(settings.calendarEventsDevice, 'nextEventFriendlyString')

    // Always build the plain-text fallback - used by every mode if AI fails
    String fallbackText = buildContentOnly(todayDate, weatherReport, weatherAlerts, calendarEvents)
    logDebug("Fallback text: ${fallbackText}")

    switch (mode) {
      case 'multiStage':
        startMultiStageChain(weatherReport, weatherAlerts, calendarEvents, fallbackText)
        break

      case 'singlePass':
        String combinedInput = buildCombinedInput(todayDate, weatherReport, weatherAlerts, calendarEvents)
        logDebug("Combined input (single pass): ${combinedInput}")
        sendGeminiRequest(combinedInput, fallbackText)
        logInfo('Sent announcement to Gemini Text Rewriter for single-pass enhancement - awaiting response event')
        break

      case 'off':
      default:
        storeAnnouncement(fallbackText)
        logInfo('Stored announcement without AI enhancement')
        break
    }

  } catch (Exception e) {
    logError("Failed to generate announcement: ${e.message}")
    state.lastAnnouncement = "Error: ${e.message}"
    state.lastGenerated = new Date().format('yyyy-MM-dd HH:mm:ss')
  }
}

/**
 * resolveAiMode() - Resolve effective AI mode, honoring legacy useGeminiRewriter setting
 * for users who upgrade without revisiting preferences.
 */
private String resolveAiMode() {
  String mode = settings.aiMode
  if (mode) { return mode }
  // Legacy fallback: useGeminiRewriter=true -> singlePass; else off.
  if (settings.useGeminiRewriter == true) { return 'singlePass' }
  if (settings.useGeminiRewriter == false) { return 'off' }
  return 'multiStage'
}

/**
 * getDeviceAttributeValue() - Safely retrieve attribute value from device
 */
private String getDeviceAttributeValue(DeviceWrapper device, String attributeName) {
  if (!device || !attributeName) {
    return ''
  }

  try {
    def value = device.currentValue(attributeName)
    return value ? value.toString() : ''
  } catch (Exception e) {
    logWarn("Failed to read ${attributeName} from ${device.displayName}: ${e.message}")
    return ''
  }
}

/**
 * buildCombinedInput() - Combine all inputs with instructions for AI
 * Parameters: todayDate, weatherReport, weatherAlerts, calendarEvents
 */
private String buildCombinedInput(String todayDate, String weatherReport, String weatherAlerts, String calendarEvents) {
  StringBuilder input = new StringBuilder()

  // Add custom instructions
  input.append(settings.customInstructions ?: 'Create a morning announcement from the following:')
  input.append('\n\n')

  // Add today's date (provided implicitly)
  if (todayDate) {
    input.append(todayDate)
    input.append('\n\n')
  }

  // Add weather report
  if (weatherReport) {
    input.append('WEATHER FORECAST:\n')
    input.append(weatherReport)
    input.append('\n\n')
  }

  // Add weather alerts
  if (weatherAlerts) {
    input.append('WEATHER ALERTS:\n')
    input.append(weatherAlerts)
    input.append('\n\n')
  }

  // Add calendar events
  if (calendarEvents) {
    input.append('UPCOMING CALENDAR EVENTS:\n')
    input.append(calendarEvents)
    input.append('\n\n')
  }

  // If nothing to announce
  if (!weatherReport && !weatherAlerts && !calendarEvents) {
    return 'Create a brief, cheerful good morning message. No weather or calendar information is available today.'
  }

  return input.toString()
}

/**
 * buildContentOnly() - Build announcement content without instructions
 * Parameters: todayDate, weatherReport, weatherAlerts, calendarEvents
 * This creates a simple concatenation of the data for fallback use
 */
private String buildContentOnly(String todayDate, String weatherReport, String weatherAlerts, String calendarEvents) {
  StringBuilder content = new StringBuilder()

  // Add today's date
  if (todayDate) {
    content.append(todayDate)
    content.append(' ')
  }

  // Add weather report
  if (weatherReport) {
    content.append('Weather Forecast: ')
    content.append(weatherReport)
    content.append('. ')
  }

  // Add weather alerts
  if (weatherAlerts) {
    content.append('Weather Alerts: ')
    content.append(weatherAlerts)
    content.append('. ')
  }

  // Add calendar events
  if (calendarEvents) {
    content.append('Today\'s Calendar: ')
    content.append(calendarEvents)
    content.append('. ')
  }

  // If nothing to announce
  if (!weatherReport && !weatherAlerts && !calendarEvents) {
    if (todayDate) {
      return "Good morning! ${todayDate} No weather or calendar information is available today."
    }
    return 'Good morning! No weather or calendar information is available today.'
  }

  return content.toString().trim()
}

/**
 * sendGeminiRequest() - Send text to Gemini for enhancement via location events
 * This is a fire-and-forget operation; the response will be handled by the event listener
 */
private void sendGeminiRequest(String inputText, String fallbackText) {
  try {
    // Generate unique request ID
    String requestId = UUID.randomUUID().toString()

    logDebug("Sending rewrite request with ID: ${requestId}")

    // Store request details in state for response handler
    state.pendingGeminiRequest = [
      requestId: requestId,
      originalText: inputText,
      fallbackText: fallbackText,
      timestamp: now()
    ]

    // Prepare request data (include fallback text for error cases)
    Map requestData = [
      requestId: requestId,
      mode: 'custom',
      fallbackText: fallbackText
    ]

    // Add maxTokens if specified
    if (settings.maxTokens) {
      requestData.maxTokens = settings.maxTokens
    }

    // Send location event to Gemini Text Rewriter app
    sendLocationEvent(
      name: 'geminiRewriteRequest',
      value: inputText,
      data: JsonOutput.toJson(requestData)
    )

    logDebug("Sent rewrite request via location event")

  } catch (Exception e) {
    logError("Error sending Gemini request: ${e.message}")
    // Fall back to storing fallback text if send fails
    storeAnnouncement(fallbackText)
  }
}

/**
 * storeAnnouncement() - Store the final announcement text
 */
private void storeAnnouncement(String announcement) {
  try {
    // Store results
    state.lastAnnouncement = announcement
    state.lastGenerated = new Date().format('yyyy-MM-dd HH:mm:ss')

    // Store in global variable
    if (settings.globalVariableName) {
      setGlobalVar(settings.globalVariableName, announcement)
      logInfo("Stored announcement in global variable: ${settings.globalVariableName}")
    }

    logInfo('Morning announcement stored successfully')

  } catch (Exception e) {
    logError("Failed to store announcement: ${e.message}")
  }
}

/**
 * handleGeminiResponseEvent() - Process responses from Gemini Text Rewriter app
 * This event-driven handler completes the announcement workflow by storing the result
 */
void handleGeminiResponseEvent(Event evt) {
  try {
    logDebug("Received Gemini response event: ${evt.value?.take(50)}...")

    Map responseData = evt.data ? parseJson(evt.data) : [:]
    String requestId = responseData?.requestId

    // Check if this response matches our pending request
    if (state.pendingGeminiRequest?.requestId == requestId) {
      logDebug("Response matches pending request ID: ${requestId}")

      // Get the enhanced text or fall back to content without instructions
      String announcement = ''
      if (responseData.success && responseData.rewritten) {
        announcement = responseData.rewritten
        logInfo('Received AI-enhanced announcement')
      } else {
        // Use fallback text (content only, no instructions) if enhancement failed
        announcement = state.pendingGeminiRequest.fallbackText ?: state.pendingGeminiRequest.originalText
        logWarn("AI enhancement failed: ${responseData.error ?: 'Unknown error'}, using fallback text")
      }

      // Store the announcement
      storeAnnouncement(announcement)

      // Clean up pending request
      state.remove('pendingGeminiRequest')

    } else {
      logDebug("Response ID ${requestId} doesn't match pending request, ignoring")
    }

  } catch (Exception e) {
    logError("Error handling Gemini response event: ${e.message}")
    // Try to fall back to fallback text or original text if we have it
    if (state.pendingGeminiRequest?.fallbackText) {
      storeAnnouncement(state.pendingGeminiRequest.fallbackText)
      state.remove('pendingGeminiRequest')
    } else if (state.pendingGeminiRequest?.originalText) {
      storeAnnouncement(state.pendingGeminiRequest.originalText)
      state.remove('pendingGeminiRequest')
    }
  }
}

/**
 * handleGeminiTextRewrittenEvent() - Store any completed rewrite in global variable
 * This listener captures ALL rewrite completions from the Gemini app, regardless of source
 */
void handleGeminiTextRewrittenEvent(Event evt) {
  try {
    String rewrittenText = evt.value
    Map eventData = evt.data ? parseJson(evt.data) : [:]

    logDebug("Received geminiTextRewritten event: ${rewrittenText?.take(50)}...")
    logDebug("Event data - mode: ${eventData.mode}, success: ${eventData.success}")

    // Store in global variable if configured and rewrite was successful
    if (settings.globalVariableName && rewrittenText && eventData.success) {
      setGlobalVar(settings.globalVariableName, rewrittenText)
      logInfo("Stored rewritten text in global variable: ${settings.globalVariableName}")
      logDebug("Text preview: ${rewrittenText.take(100)}...")
    }

  } catch (Exception e) {
    logError("Error handling geminiTextRewritten event: ${e.message}")
  }
}

// =============================================================================
// MULTI-STAGE CHAIN (Stage A: Weather, Stage B: Calendar, Stage C: Weave)
// =============================================================================

/**
 * startMultiStageChain() - Initialize the chain state and kick off Stage A.
 * Stages run synchronously inside runIn()-scheduled methods so the hub does
 * not block for the duration of all three calls in a single thread.
 */
private void startMultiStageChain(String weatherReport, String weatherAlerts, String calendarEvents, String fallbackText) {
  if (!settings.geminiApiKey) {
    logWarn('Multi-stage chain selected but no Gemini API key configured; falling back to plain text')
    storeAnnouncement(fallbackText)
    return
  }

  String dateAnchor = buildDateAnchor()
  logDebug("Date anchor:\n${dateAnchor}")

  state.chain = [
    startedAt: now(),
    dateAnchor: dateAnchor,
    weatherInput: weatherReport ?: '',
    alertsInput: weatherAlerts ?: '',
    calendarInput: calendarEvents ?: '',
    weatherSummary: null,
    calendarSummary: null,
    fallbackText: fallbackText,
    failures: []
  ]

  // Kick off Stage A immediately. Subsequent stages are scheduled via runIn().
  runStageWeather()
}

/**
 * runStageWeather() - Stage A: condense the weather forecast and any alerts
 * into a single TTS-friendly paragraph.
 */
void runStageWeather() {
  Map chain = (state.chain as Map) ?: [:]
  try {
    String weather = chain.weatherInput ?: ''
    String alerts = chain.alertsInput ?: ''

    if (!weather && !alerts) {
      logDebug('Stage A skipped: no weather or alerts input')
      chain.weatherSummary = ''
      state.chain = chain
      scheduleNextStage('runStageCalendar')
      return
    }

    StringBuilder content = new StringBuilder()
    if (alerts) {
      content.append('WEATHER ALERTS:\n').append(alerts).append('\n\n')
    }
    if (weather) {
      content.append('WEATHER FORECAST:\n').append(weather)
    }

    String systemPrompt = settings.weatherStagePrompt ?: DEFAULT_WEATHER_PROMPT
    Map result = callGeminiDirect(systemPrompt, content.toString())

    if (result.success) {
      chain.weatherSummary = result.text
      logInfo('Stage A (weather) succeeded')
      logDebug("Stage A output: ${result.text}")
    } else {
      chain.weatherSummary = ''
      chain.failures << "weather: ${result.error}"
      logWarn("Stage A (weather) failed: ${result.error}")
    }
    state.chain = chain
    scheduleNextStage('runStageCalendar')

  } catch (Exception e) {
    logError("Stage A exception: ${e.message}")
    chain.weatherSummary = ''
    chain.failures << "weather exception: ${e.message}"
    state.chain = chain
    scheduleNextStage('runStageCalendar')
  }
}

/**
 * runStageCalendar() - Stage B: condense calendar events into a date-anchored
 * chronological summary. The DATE ANCHOR block is prepended so Gemini can
 * resolve absolute event dates to relative phrases (today/tomorrow/this Friday).
 */
void runStageCalendar() {
  Map chain = (state.chain as Map) ?: [:]
  try {
    String calendar = chain.calendarInput ?: ''
    String dateAnchor = chain.dateAnchor ?: buildDateAnchor()

    if (!calendar) {
      logDebug('Stage B skipped: no calendar input')
      chain.calendarSummary = ''
      state.chain = chain
      scheduleNextStage('runStageWeaver')
      return
    }

    String systemPrompt = (settings.calendarStagePrompt ?: DEFAULT_CALENDAR_PROMPT)
    // Date anchor goes ABOVE the prompt so it is the first thing Gemini sees.
    String fullSystemPrompt = "${dateAnchor}\n\n${systemPrompt}"
    String content = "CALENDAR EVENTS:\n${calendar}"

    Map result = callGeminiDirect(fullSystemPrompt, content)

    if (result.success) {
      chain.calendarSummary = result.text
      logInfo('Stage B (calendar) succeeded')
      logDebug("Stage B output: ${result.text}")
    } else {
      chain.calendarSummary = ''
      chain.failures << "calendar: ${result.error}"
      logWarn("Stage B (calendar) failed: ${result.error}")
    }
    state.chain = chain
    scheduleNextStage('runStageWeaver')

  } catch (Exception e) {
    logError("Stage B exception: ${e.message}")
    chain.calendarSummary = ''
    chain.failures << "calendar exception: ${e.message}"
    state.chain = chain
    scheduleNextStage('runStageWeaver')
  }
}

/**
 * runStageWeaver() - Stage C: combine the weather and calendar summaries into
 * a single natural-flowing morning announcement with greeting and closing.
 * If both prior stages failed, falls back directly to plain concatenated text.
 */
void runStageWeaver() {
  Map chain = (state.chain as Map) ?: [:]
  try {
    String weatherSummary = (chain.weatherSummary ?: '').toString().trim()
    String calendarSummary = (chain.calendarSummary ?: '').toString().trim()

    // If both summaries are empty, there's nothing for the weaver to do.
    if (!weatherSummary && !calendarSummary) {
      logWarn('Both stage summaries empty; using plain fallback text')
      storeAnnouncement(chain.fallbackText ?: 'Good morning!')
      cleanupChain()
      return
    }

    StringBuilder content = new StringBuilder()
    if (weatherSummary) {
      content.append('WEATHER SEGMENT:\n').append(weatherSummary).append('\n\n')
    }
    if (calendarSummary) {
      content.append('CALENDAR SEGMENT:\n').append(calendarSummary)
    }

    String systemPrompt = settings.weaverStagePrompt ?: DEFAULT_WEAVER_PROMPT
    Map result = callGeminiDirect(systemPrompt, content.toString())

    String finalText
    if (result.success) {
      finalText = result.text
      logInfo('Stage C (weaver) succeeded')
    } else {
      // Weaver failed - concatenate the partial summaries directly so the user
      // still gets the AI-improved sub-segments rather than a raw fallback.
      logWarn("Stage C (weaver) failed: ${result.error}; concatenating partial summaries")
      chain.failures << "weaver: ${result.error}"
      StringBuilder concat = new StringBuilder('Good morning! ')
      if (weatherSummary) { concat.append(weatherSummary).append(' ') }
      if (calendarSummary) { concat.append(calendarSummary) }
      finalText = concat.toString().trim()
    }

    if (chain.failures) {
      logWarn("Chain completed with failures: ${chain.failures}")
    }

    storeAnnouncement(finalText)
    cleanupChain()

  } catch (Exception e) {
    logError("Stage C exception: ${e.message}")
    storeAnnouncement(chain.fallbackText ?: 'Good morning!')
    cleanupChain()
  }
}

/**
 * scheduleNextStage() - Schedule the next stage with the configured inter-stage
 * delay. A delay of 0 runs immediately (still through runIn for state safety).
 */
private void scheduleNextStage(String methodName) {
  Integer delay = (settings.stageDelaySeconds ?: 3) as Integer
  if (delay < 0) { delay = 0 }
  logDebug("Scheduling ${methodName} in ${delay}s")
  runIn(delay, methodName, [overwrite: false])
}

/**
 * cleanupChain() - Remove transient chain state once the chain finishes.
 */
private void cleanupChain() {
  state.remove('chain')
}

// =============================================================================
// DATE ANCHOR (calendar-date grounding for Gemini)
// =============================================================================

/**
 * buildDateAnchor() - Return an explicit, unambiguous date context block that
 * Gemini can reference when resolving event dates. Includes today, tomorrow,
 * and the next 6 days mapped to weekday names. This is the single most
 * effective fix for "Gemini thinks the event is on the wrong day" issues.
 */
private String buildDateAnchor() {
  Date today = new Date()
  SimpleDateFormat fullFmt = new SimpleDateFormat('EEEE, MMMM d, yyyy')
  SimpleDateFormat dayFmt = new SimpleDateFormat('EEEE')
  SimpleDateFormat shortFmt = new SimpleDateFormat('EEEE, MMMM d')

  Calendar cal = Calendar.getInstance()
  cal.setTime(today)

  StringBuilder anchor = new StringBuilder()
  anchor.append('DATE ANCHOR (use this as the ground truth for resolving event dates):\n')
  anchor.append("TODAY: ${fullFmt.format(today)}\n")

  cal.add(Calendar.DAY_OF_MONTH, 1)
  anchor.append("TOMORROW: ${fullFmt.format(cal.time)}\n")
  anchor.append('UPCOMING DAYS:\n')

  // Reset to today, then list the next 7 days with weekday + date so Gemini
  // can disambiguate "this Friday" vs "next Friday".
  cal.setTime(today)
  for (int i = 2; i <= 7; i++) {
    cal.add(Calendar.DAY_OF_MONTH, 1)
    String relativeLabel = (i < 7) ? "this ${dayFmt.format(cal.time)}" : "next ${dayFmt.format(cal.time)}"
    anchor.append("  - ${shortFmt.format(cal.time)} = \"${relativeLabel}\"\n")
  }

  return anchor.toString().trim()
}

// =============================================================================
// DIRECT GEMINI API HELPERS
// (Adapted from GeminiTextRewriter.groovy:1121-1235; duplicated here so this
// app can call Gemini directly without a location-event round trip.)
// =============================================================================

/**
 * callGeminiDirect() - Synchronous Gemini API call. Returns a result map:
 *   [success: true, text: '...']
 *   [success: false, error: '...']
 */
private Map callGeminiDirect(String systemPrompt, String content) {
  try {
    if (!settings.geminiApiKey) {
      return [success: false, error: 'No Gemini API key configured']
    }

    String modelName = (settings.geminiModel ?: 'gemini-2.5-flash').trim()
    String apiKey = settings.geminiApiKey.trim()
    String apiUrl = "${GEMINI_API_BASE}/${modelName}:generateContent?key=${apiKey}"

    Map requestBody = buildGeminiRequest(systemPrompt, content)

    logDebug("Gemini call -> model=${modelName}, content len=${content?.length()}")

    Map params = [
      uri: apiUrl,
      contentType: 'application/json',
      requestContentType: 'application/json',
      body: JsonOutput.toJson(requestBody),
      timeout: 30
    ]

    def responseStatus = null
    def responseData = null
    httpPost(params) { response ->
      responseStatus = response.status
      responseData = response.data
    }

    if (responseStatus == 200 && responseData) {
      String text = extractTextFromResponse(responseData)
      if (text) {
        return [success: true, text: text]
      }
      return [success: false, error: 'Empty text in Gemini response']
    }
    return [success: false, error: "Gemini API returned status ${responseStatus}"]

  } catch (groovyx.net.http.HttpResponseException e) {
    String detail = "HTTP ${e.statusCode}"
    try {
      if (e.response?.data?.error?.message) {
        detail = "Gemini API Error: ${e.response.data.error.message}"
      }
    } catch (Exception ignore) { /* keep generic detail */ }
    return [success: false, error: detail]
  } catch (Exception e) {
    return [success: false, error: "HTTP exception: ${e.message}"]
  }
}

/**
 * buildGeminiRequest() - Construct the JSON body expected by Gemini's
 * generateContent endpoint.
 */
private Map buildGeminiRequest(String systemPrompt, String content) {
  return [
    contents: [
      [parts: [[text: "${systemPrompt}\n\n${content}"]]]
    ],
    generationConfig: [
      temperature: (settings.temperature ?: 0.6) as Double,
      maxOutputTokens: (settings.maxTokensPerStage ?: 800) as Integer,
      topP: 0.95,
      topK: 40
    ]
  ]
}

/**
 * extractTextFromResponse() - Pull the generated text out of the Gemini
 * response payload. Returns null if not found.
 */
private String extractTextFromResponse(def responseData) {
  try {
    if (responseData?.candidates && responseData.candidates.size() > 0) {
      def first = responseData.candidates[0]
      if (first.finishReason && first.finishReason != 'STOP') {
        logWarn("Gemini text may be truncated/filtered. finishReason=${first.finishReason}")
      }
      if (first?.content?.parts && first.content.parts.size() > 0) {
        return first.content.parts[0].text?.trim()
      }
    }
    logWarn("Gemini response missing candidates: ${responseData}")
    return null
  } catch (Exception e) {
    logError("Error parsing Gemini response: ${e.message}")
    return null
  }
}

// =============================================================================
// UTILITY METHODS
// =============================================================================

/**
 * getLocalUri() - Get local webhook URL
 */
String getLocalUri() {
  return state.accessToken ?
    "${getFullLocalApiServerUrl()}/generate?access_token=${state.accessToken}" :
    'Access token not available'
}

/**
 * getCloudUri() - Get cloud webhook URL
 */
String getCloudUri() {
  return state.accessToken ?
    "${getApiServerUrl()}/${hubUID}/apps/${app.id}/generate?access_token=${state.accessToken}" :
    'Access token not available'
}
