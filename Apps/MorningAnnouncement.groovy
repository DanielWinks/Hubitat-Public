// =============================================================================
// MORNING ANNOUNCEMENT APP
// =============================================================================
// Author: Daniel Winks
// Description: Generates personalized morning announcements by combining
//              weather reports, weather alerts, and calendar events, then
//              using the OpenRouter AI API to create natural, engaging
//              announcements.
//
// Features:
//   - Device-based inputs (weather, alerts, calendar)
//   - Direct OpenRouter API integration (multi-stage chain or single pass)
//   - Scheduled generation (configurable time)
//   - Manual generation button
//   - HTTP webhook endpoint for external triggering
//   - Global variable storage for Rule Machine access
//   - HTTP endpoint to retrieve latest announcement
//   - Standalone: no external library #include required
// =============================================================================

//
//  MIT License
//  Copyright 2026 Daniel Winks (daniel.winks@gmail.com)
//
//  Permission is hereby granted, free of charge, to any person obtaining a copy
//  of this software and associated documentation files (the "Software"), to deal
//  in the Software without restriction, including without limitation the rights
//  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//  copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions:
//
//  The above copyright notice and this permission notice shall be included in all
//  copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
//  SOFTWARE.
//

// This app is fully standalone - it intentionally does NOT #include any library.
// The small slice of helpers it needs (logging, lifecycle hooks, log auto-off,
// and OAuth token creation) are inlined at the bottom of this file. Everything
// else it calls (runIn, schedule, setGlobalVar, render, createAccessToken, now)
// is a Hubitat platform built-in.

// Import required classes. groovy.transform.Field must be imported explicitly
// here because, without a library #include, nothing else provides it and
// @Field static final would otherwise fail to parse on the hub.
import groovy.json.JsonOutput
import groovy.transform.Field
import com.hubitat.app.DeviceWrapper
import java.text.SimpleDateFormat
import java.util.Calendar

// =============================================================================
// GLOBAL CONSTANTS
// =============================================================================

@Field static final String OPENROUTER_API_URL = 'https://openrouter.ai/api/v1/chat/completions'

// Curated list of popular OpenRouter models. Any model not listed here can be
// used via the free-text "Custom Model Slug" override input.
@Field static final Map OPENROUTER_MODELS = [
  'openai/gpt-4o-mini': 'OpenAI GPT-4o mini (Fast, very cheap - Recommended Default)',
  'openai/gpt-4o': 'OpenAI GPT-4o (Higher quality, pricier)',
  'anthropic/claude-3.5-haiku': 'Anthropic Claude 3.5 Haiku (Fast, natural prose)',
  'anthropic/claude-3.5-sonnet': 'Anthropic Claude 3.5 Sonnet (High quality, pricier)',
  'google/gemini-2.5-flash': 'Google Gemini 2.5 Flash (Fast, balanced)',
  'meta-llama/llama-3.3-70b-instruct': 'Meta Llama 3.3 70B Instruct (Open model)',
  'meta-llama/llama-3.3-70b-instruct:free': 'Meta Llama 3.3 70B Instruct (FREE - rate-limited)',
  'deepseek/deepseek-chat': 'DeepSeek Chat (Cheap, capable)'
]

@Field static final String DEFAULT_OPENROUTER_MODEL = 'openai/gpt-4o-mini'

@Field static final Map AI_MODES = [
  'off': 'Off (use plain concatenated text)',
  'singlePass': 'Single Pass (one combined OpenRouter call)',
  'multiStage': 'Multi-Stage Chain (recommended: focused weather + calendar + weave passes via OpenRouter)'
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

@Field static final String DEFAULT_SINGLE_PASS_PROMPT = '''Create a warm, friendly morning announcement from the following information.
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
You will be provided with today's date implicitly (formatted like "Today is..."); use it to contextualize calendar events.
Do not announce the date explicitly unless it is part of a calendar event, except to say "today is..."
Make sure not to confuse today's date with any dates mentioned in calendar events.
Again, you will be provided with today's date implicitly. Do not confuse it with dates mentioned in calendar events. This is extremely important.
Ensure the entire announcement is concise, ideally under 2 minutes when spoken aloud.
This will be a text-to-speech announcement, so clarity and natural phrasing are key, as well as spelling out any acronyms, numbers, or abbreviations for proper pronunciation.
If there is a weather alert, make sure it stands out in the announcement and is clearly communicated, as this is critical information for the listener.
Do not omit weather alerts if they are present, even if they are long. Summarize them as best as possible while ensuring the critical information is conveyed.'''

/**
 * definition() - Defines app metadata for Hubitat
 */
definition(
  name: 'Morning Announcement',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Generate personalized morning announcements from weather, alerts, and calendar events using the OpenRouter AI API',
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
        description: 'How (or whether) to use OpenRouter to enhance the announcement.'
      paragraph '''<small><b>Multi-Stage Chain</b> (recommended): three focused OpenRouter calls — one for weather, one for calendar, one to weave them together. Calendar dates are anchored to today's date to prevent date confusion.<br><br><b>Single Pass</b>: sends one combined prompt directly to OpenRouter in a single call (cheaper, fewer requests).<br><br><b>Off</b>: returns plain concatenated text with no AI enhancement.<br><br>Both AI modes require an OpenRouter API key configured below.</small>'''
    }

    // Shared OpenRouter configuration - required by both AI modes.
    if (settings.aiMode == 'multiStage' || settings.aiMode == 'singlePass') {
      section('<b>OpenRouter API Configuration</b>') {
        paragraph 'Get your API key from: <a href="https://openrouter.ai/keys" target="_blank">OpenRouter Keys</a>'
        input 'openRouterApiKey', 'text',
          title: 'OpenRouter API Key',
          required: true,
          description: 'API key used directly by this app (sent as an Authorization Bearer token).'
        input 'openRouterModel', 'enum',
          title: 'Model',
          options: OPENROUTER_MODELS,
          required: true,
          defaultValue: DEFAULT_OPENROUTER_MODEL
        input 'openRouterCustomModel', 'text',
          title: 'Custom Model Slug (optional override)',
          required: false,
          description: 'Type any OpenRouter model slug to override the dropdown above, e.g. anthropic/claude-3.7-sonnet or x-ai/grok-2. Leave blank to use the dropdown selection.'
        input 'temperature', 'decimal',
          title: 'Temperature (0.0-1.0)',
          required: false,
          defaultValue: 0.6,
          range: '0.0..1.0',
          description: 'Lower = more focused, higher = more creative. 0.6 is a good balance for announcements.'
      }
    }

    if (settings.aiMode == 'multiStage') {
      section('<b>Multi-Stage Tuning</b>') {
        input 'maxTokensPerStage', 'number',
          title: 'Max Tokens per Stage',
          required: false,
          defaultValue: 800,
          range: '100..4096',
          description: 'Token cap for each individual stage call (weather, calendar, weaver).'
        input 'stageDelaySeconds', 'number',
          title: 'Delay Between Stages & Retries (seconds)',
          required: false,
          defaultValue: 7,
          range: '0..60',
          description: 'Spacing between OpenRouter calls, also applied before a retry. Helps stay under per-model rate limits. The default is comfortable for paid models; increase it if you select a rate-limited :free model.'

        Map timing = computeGenerationTime()
        paragraph("<b>Estimated Generation Time</b><br>" +
          "<b>Typical:</b> ~${timing.typical} seconds (no retries, fast API responses)<br>" +
          "<b>Maximum:</b> ~${timing.maxFormatted} if every stage must retry AND every call hits the 30s HTTP timeout (worst case; unlikely to ever occur).<br>" +
          "<small>Max = ${timing.maxCalls} calls × 30s timeout + ${timing.maxGaps} gaps × ${timing.delay}s delay = ${timing.maxSeconds}s. Each of the 3 stages (weather, calendar, weaver) runs at most twice thanks to the single-retry logic.</small>")
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
      section('<b>Single Pass Configuration</b>') {
        input 'maxTokens', 'number', title: 'Max Tokens for AI Response', required: false, defaultValue: 2048, description: 'Maximum number of tokens to allow for the AI-generated announcement (default: 2048)'
        paragraph '''<small>Single Pass mode sends one combined prompt directly to OpenRouter and stores the response. It uses the OpenRouter API key and model configured above.</small>'''
        input 'customInstructions', 'text',
          title: 'Instructions for AI (sent as the system message)',
          required: true,
          defaultValue: DEFAULT_SINGLE_PASS_PROMPT,
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
// installed()/updated()/uninstalled() were previously provided by
// UtilitiesAndLoggingLibrary. They are inlined here (app-only variants) so the
// app is fully standalone.

/**
 * installed() - Called once when the app is first installed.
 */
void installed() {
  logDebug('Installed...')
  try {
    initialize()
  } catch (e) {
    logWarn("initialize() resulted in error: ${e}")
  }
  if (settings.logEnable != false) { runIn(1800, 'logsOff') }
  if (settings.debugLogEnable != false) { runIn(1800, 'debugLogsOff') }
}

/**
 * updated() - Called whenever the user saves preferences.
 */
void updated() {
  logDebug('Updated...')
  try {
    configure()
  } catch (e) {
    logWarn("configure() resulted in error: ${e}")
  }
}

/**
 * uninstalled() - Called when the app is removed.
 */
void uninstalled() {
  logDebug('Uninstalled...')
  unschedule()
  unsubscribe()
}

/**
 * configure() - Re-establish schedules/subscriptions. Called from updated().
 */
void configure() {
  logInfo('Configuring Morning Announcement app')
  unsubscribe()
  unschedule()
  initialize()
}

/**
 * initialize() - Create the OAuth token and (re)install the daily schedule.
 */
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
 *   - 'multiStage' (default): focused weather + calendar + weave OpenRouter calls
 *   - 'singlePass'         : one combined prompt sent directly to OpenRouter
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
    String weatherReport = getDeviceAttributeValue(settings.weatherReportDevice, 'forecastSummary')
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
        runSinglePass(todayDate, weatherReport, weatherAlerts, calendarEvents, fallbackText)
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
 * buildSinglePassContent() - Build the data block (date + named sections) sent as
 * the user message in single-pass mode. The instructions live separately in the
 * system message (settings.customInstructions), so they are NOT prepended here.
 */
private String buildSinglePassContent(String todayDate, String weatherReport, String weatherAlerts, String calendarEvents) {
  // If nothing to announce, ask for a brief cheerful message instead.
  if (!weatherReport && !weatherAlerts && !calendarEvents) {
    StringBuilder empty = new StringBuilder()
    if (todayDate) { empty.append(todayDate).append('\n\n') }
    empty.append('No weather or calendar information is available today. Create a brief, cheerful good morning message.')
    return empty.toString()
  }

  StringBuilder input = new StringBuilder()

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

  return input.toString().trim()
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
 * runSinglePass() - Single Pass mode: send one combined prompt directly to
 * OpenRouter. The user-configured instructions become the system message; the
 * date + weather + alerts + calendar block becomes the user message. Falls back
 * to plain concatenated text if the API key is missing or the call fails.
 */
private void runSinglePass(String todayDate, String weatherReport, String weatherAlerts, String calendarEvents, String fallbackText) {
  if (!settings.openRouterApiKey) {
    logWarn('Single Pass selected but no OpenRouter API key configured; falling back to plain text')
    storeAnnouncement(fallbackText)
    return
  }

  String systemPrompt = settings.customInstructions ?: DEFAULT_SINGLE_PASS_PROMPT
  String content = buildSinglePassContent(todayDate, weatherReport, weatherAlerts, calendarEvents)
  logDebug("Single-pass content: ${content}")

  Integer maxTokens = (settings.maxTokens ?: 2048) as Integer
  Map result = callOpenRouterDirect(systemPrompt, content, maxTokens)

  if (result.success && result.text) {
    logInfo('Single-pass OpenRouter enhancement succeeded')
    storeAnnouncement(result.text as String)
  } else {
    logWarn("Single-pass OpenRouter enhancement failed: ${result.error}; using fallback text")
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

// =============================================================================
// MULTI-STAGE CHAIN (Stage A: Weather, Stage B: Calendar, Stage C: Weave)
// =============================================================================

/**
 * startMultiStageChain() - Initialize the chain state and kick off Stage A.
 * Stages run synchronously inside runIn()-scheduled methods so the hub does
 * not block for the duration of all three calls in a single thread.
 */
private void startMultiStageChain(String weatherReport, String weatherAlerts, String calendarEvents, String fallbackText) {
  if (!settings.openRouterApiKey) {
    logWarn('Multi-stage chain selected but no OpenRouter API key configured; falling back to plain text')
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
    failures: [],
    // Per-stage attempt counters. Each stage runs at most MAX_STAGE_ATTEMPTS
    // (twice: initial call + one retry) before being recorded as failed.
    attempts: [weather: 0, calendar: 0, weaver: 0]
  ]

  // Kick off Stage A immediately. Subsequent stages are scheduled via runIn().
  runStageWeather()
}

// Each stage runs at most this many times (initial attempt + retry). Hard-coded
// rather than exposed as a setting because "single retry" is the explicit policy.
@Field static final Integer MAX_STAGE_ATTEMPTS = 2

/**
 * outputDiffersFromInput() - Quality check for a stage output. Returns false if
 * the response is empty, whitespace-only, or identical (trimmed) to the content
 * we sent. This catches the failure mode where the model echoes the input back
 * instead of summarizing / rewriting it.
 */
private boolean outputDiffersFromInput(String output, String input) {
  if (!output) { return false }
  String o = output.trim()
  String i = (input ?: '').trim()
  if (!o) { return false }
  return o != i
}

/**
 * runStageWeather() - Stage A: condense the weather forecast and any alerts
 * into a single TTS-friendly paragraph. Retries once if the API fails or if
 * the output is empty or identical to the input.
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

    Integer attempt = ((chain.attempts?.weather ?: 0) as Integer) + 1
    chain.attempts.weather = attempt
    logDebug("Stage A (weather) attempt ${attempt}/${MAX_STAGE_ATTEMPTS}")

    StringBuilder sb = new StringBuilder()
    if (alerts) {
      sb.append('WEATHER ALERTS:\n').append(alerts).append('\n\n')
    }
    if (weather) {
      sb.append('WEATHER FORECAST:\n').append(weather)
    }
    String content = sb.toString()

    String systemPrompt = settings.weatherStagePrompt ?: DEFAULT_WEATHER_PROMPT
    Map result = callOpenRouterDirect(systemPrompt, content)

    boolean ok = result.success && outputDiffersFromInput(result.text, content)
    if (ok) {
      chain.weatherSummary = result.text
      state.chain = chain
      logInfo("Stage A (weather) succeeded on attempt ${attempt}")
      logDebug("Stage A output: ${result.text}")
      scheduleNextStage('runStageCalendar')
      return
    }

    // Quality / API failure path
    String why = result.success ? 'output identical to input or empty' : result.error
    if (attempt < MAX_STAGE_ATTEMPTS) {
      logWarn("Stage A (weather) attempt ${attempt} unacceptable (${why}); retrying")
      state.chain = chain
      scheduleNextStage('runStageWeather')
    } else {
      chain.weatherSummary = ''
      chain.failures << "weather: ${why} (gave up after ${attempt} attempts)"
      state.chain = chain
      logWarn("Stage A (weather) failed after ${attempt} attempts: ${why}")
      scheduleNextStage('runStageCalendar')
    }

  } catch (Exception e) {
    logError("Stage A exception: ${e.message}")
    chain.weatherSummary = ''
    (chain.failures as List) << "weather exception: ${e.message}"
    state.chain = chain
    scheduleNextStage('runStageCalendar')
  }
}

/**
 * runStageCalendar() - Stage B: condense calendar events into a date-anchored
 * chronological summary. The DATE ANCHOR block is prepended so the model can
 * resolve absolute event dates to relative phrases (today/tomorrow/this Friday).
 * Retries once if the API fails or if the output is empty or identical to input.
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

    Integer attempt = ((chain.attempts?.calendar ?: 0) as Integer) + 1
    chain.attempts.calendar = attempt
    logDebug("Stage B (calendar) attempt ${attempt}/${MAX_STAGE_ATTEMPTS}")

    String systemPrompt = settings.calendarStagePrompt ?: DEFAULT_CALENDAR_PROMPT
    // Date anchor goes ABOVE the prompt so it is the first thing the model sees.
    String fullSystemPrompt = "${dateAnchor}\n\n${systemPrompt}"
    String content = "CALENDAR EVENTS:\n${calendar}"

    Map result = callOpenRouterDirect(fullSystemPrompt, content)

    boolean ok = result.success && outputDiffersFromInput(result.text, content)
    if (ok) {
      chain.calendarSummary = result.text
      state.chain = chain
      logInfo("Stage B (calendar) succeeded on attempt ${attempt}")
      logDebug("Stage B output: ${result.text}")
      scheduleNextStage('runStageWeaver')
      return
    }

    String why = result.success ? 'output identical to input or empty' : result.error
    if (attempt < MAX_STAGE_ATTEMPTS) {
      logWarn("Stage B (calendar) attempt ${attempt} unacceptable (${why}); retrying")
      state.chain = chain
      scheduleNextStage('runStageCalendar')
    } else {
      chain.calendarSummary = ''
      chain.failures << "calendar: ${why} (gave up after ${attempt} attempts)"
      state.chain = chain
      logWarn("Stage B (calendar) failed after ${attempt} attempts: ${why}")
      scheduleNextStage('runStageWeaver')
    }

  } catch (Exception e) {
    logError("Stage B exception: ${e.message}")
    chain.calendarSummary = ''
    (chain.failures as List) << "calendar exception: ${e.message}"
    state.chain = chain
    scheduleNextStage('runStageWeaver')
  }
}

/**
 * runStageWeaver() - Stage C: combine the weather and calendar summaries into
 * a single natural-flowing morning announcement with greeting and closing.
 * Retries once if the API fails or the output is empty/unchanged; if the retry
 * also fails, concatenates the partial summaries directly. If both prior
 * stages also failed, falls back to plain concatenated text.
 */
void runStageWeaver() {
  Map chain = (state.chain as Map) ?: [:]
  try {
    String weatherSummary = (chain.weatherSummary ?: '').toString().trim()
    String calendarSummary = (chain.calendarSummary ?: '').toString().trim()

    // If both summaries are empty, there's nothing for the weaver to do.
    if (!weatherSummary && !calendarSummary) {
      logWarn('Both stage summaries empty; using plain fallback text')
      storeAnnouncement((chain.fallbackText ?: 'Good morning!') as String)
      cleanupChain()
      return
    }

    Integer attempt = ((chain.attempts?.weaver ?: 0) as Integer) + 1
    chain.attempts.weaver = attempt
    logDebug("Stage C (weaver) attempt ${attempt}/${MAX_STAGE_ATTEMPTS}")

    StringBuilder sb = new StringBuilder()
    if (weatherSummary) {
      sb.append('WEATHER SEGMENT:\n').append(weatherSummary).append('\n\n')
    }
    if (calendarSummary) {
      sb.append('CALENDAR SEGMENT:\n').append(calendarSummary)
    }
    String content = sb.toString()

    String systemPrompt = settings.weaverStagePrompt ?: DEFAULT_WEAVER_PROMPT
    Map result = callOpenRouterDirect(systemPrompt, content)

    boolean ok = result.success && outputDiffersFromInput(result.text, content)
    if (ok) {
      logInfo("Stage C (weaver) succeeded on attempt ${attempt}")
      if (chain.failures) {
        logWarn("Chain completed with earlier failures: ${chain.failures}")
      }
      storeAnnouncement(result.text as String)
      cleanupChain()
      return
    }

    String why = result.success ? 'output identical to input or empty' : result.error
    if (attempt < MAX_STAGE_ATTEMPTS) {
      logWarn("Stage C (weaver) attempt ${attempt} unacceptable (${why}); retrying")
      state.chain = chain
      scheduleNextStage('runStageWeaver')
      return
    }

    // Retry exhausted - concatenate partial AI-improved summaries rather than
    // dropping back to raw input text.
    logWarn("Stage C (weaver) failed after ${attempt} attempts: ${why}; concatenating partial summaries")
    chain.failures << "weaver: ${why} (gave up after ${attempt} attempts)"
    StringBuilder concat = new StringBuilder('Good morning! ')
    if (weatherSummary) { concat.append(weatherSummary).append(' ') }
    if (calendarSummary) { concat.append(calendarSummary) }
    String finalText = concat.toString().trim()

    if (chain.failures) {
      logWarn("Chain completed with failures: ${chain.failures}")
    }
    storeAnnouncement(finalText)
    cleanupChain()

  } catch (Exception e) {
    logError("Stage C exception: ${e.message}")
    storeAnnouncement((chain.fallbackText ?: 'Good morning!') as String)
    cleanupChain()
  }
}

/**
 * scheduleNextStage() - Schedule a stage method with the configured delay.
 * Also used to schedule a retry of the same stage (same spacing applies so
 * we stay under rate limits). A delay of 0 runs immediately.
 */
private void scheduleNextStage(String methodName) {
  Integer delay = (settings.stageDelaySeconds ?: 7) as Integer
  if (delay < 0) { delay = 0 }
  logDebug("Scheduling ${methodName} in ${delay}s")
  runIn(delay, methodName, [overwrite: false])
}

/**
 * computeGenerationTime() - Compute best-case and worst-case generation time
 * estimates based on current settings. Used by mainPage() to show the user
 * how long a full generation can take in the absolute worst case (every stage
 * retries and every call hits the 30s HTTP timeout).
 *
 * Worst case walkthrough:
 *   Stage A call 1 -> [delay] -> Stage A call 2 -> [delay] -> Stage B call 1
 *   -> [delay] -> Stage B call 2 -> [delay] -> Stage C call 1 -> [delay]
 *   -> Stage C call 2.
 *   = 6 calls + 5 delays.
 */
private Map computeGenerationTime() {
  Integer delay = (settings.stageDelaySeconds ?: 7) as Integer
  if (delay < 0) { delay = 0 }
  Integer httpTimeout = 30      // matches timeout used in callOpenRouterDirect
  Integer numStages = 3
  Integer maxCalls = numStages * MAX_STAGE_ATTEMPTS  // 6
  Integer maxGaps = maxCalls - 1                      // 5
  Integer maxSeconds = (maxCalls * httpTimeout) + (maxGaps * delay)

  // Typical case: one call per stage, each averaging 5s API time, with the
  // configured delay between successful stages.
  Integer typicalApiSeconds = 5
  Integer typicalSeconds = (numStages * typicalApiSeconds) + ((numStages - 1) * delay)

  Integer mins = (maxSeconds / 60) as Integer
  Integer secs = (maxSeconds % 60) as Integer
  String maxFormatted = mins > 0 ? "${mins} min ${secs} sec (${maxSeconds}s)" : "${maxSeconds} seconds"

  return [
    delay: delay,
    httpTimeout: httpTimeout,
    maxCalls: maxCalls,
    maxGaps: maxGaps,
    maxSeconds: maxSeconds,
    maxFormatted: maxFormatted,
    typical: typicalSeconds
  ]
}

/**
 * cleanupChain() - Remove transient chain state once the chain finishes.
 */
private void cleanupChain() {
  state.remove('chain')
}

// =============================================================================
// DATE ANCHOR (calendar-date grounding for the model)
// =============================================================================

/**
 * buildDateAnchor() - Return an explicit, unambiguous date context block that
 * the model can reference when resolving event dates. Includes today, tomorrow,
 * and the next 6 days mapped to weekday names. This is the single most
 * effective fix for "the model thinks the event is on the wrong day" issues.
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

  // Reset to today, then list the next 7 days with weekday + date so the model
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
// DIRECT OPENROUTER API HELPERS
// =============================================================================
// OpenRouter exposes an OpenAI-compatible chat completions endpoint. The system
// prompt and user content are sent as separate messages (roles), and the key is
// sent as an Authorization Bearer header rather than a URL query parameter.

/**
 * callOpenRouterDirect() - Synchronous OpenRouter chat-completions call.
 * Returns a result map:
 *   [success: true, text: '...']
 *   [success: false, error: '...']
 *
 * @param systemPrompt The system message (instructions)
 * @param content      The user message (data to act on)
 * @param maxTokens    Optional token cap; defaults to maxTokensPerStage when null
 */
private Map callOpenRouterDirect(String systemPrompt, String content, Integer maxTokens = null) {
  try {
    if (!settings.openRouterApiKey) {
      return [success: false, error: 'No OpenRouter API key configured']
    }

    String apiKey = settings.openRouterApiKey.trim()
    String model = resolveModel()

    Map requestBody = buildOpenRouterRequest(systemPrompt, content, maxTokens)

    logDebug("OpenRouter call -> model=${model}, content len=${content?.length()}")

    Map params = [
      uri: OPENROUTER_API_URL,
      headers: [
        // Concatenate (not GString-interpolate) so the header value is a plain
        // String, which the underlying http-builder expects.
        'Authorization': ('Bearer ' + apiKey),
        'HTTP-Referer': 'https://github.com/DanielWinks/Hubitat-Public',
        'X-Title': 'Hubitat Morning Announcement'
      ],
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
      return [success: false, error: 'Empty text in OpenRouter response']
    }
    return [success: false, error: "OpenRouter API returned status ${responseStatus}"]

  } catch (groovyx.net.http.HttpResponseException e) {
    String detail = "HTTP ${e.statusCode}"
    try {
      if (e.response?.data?.error?.message) {
        detail = "OpenRouter API Error: ${e.response.data.error.message}"
      }
    } catch (Exception ignore) { /* keep generic detail */ }
    return [success: false, error: detail]
  } catch (Exception e) {
    return [success: false, error: "HTTP exception: ${e.message}"]
  }
}

/**
 * resolveModel() - Effective model = custom override (if set) else the dropdown
 * selection else the default. Trimmed so stray whitespace never reaches the API.
 */
private String resolveModel() {
  String custom = settings.openRouterCustomModel?.toString()?.trim()
  if (custom) { return custom }
  String selected = settings.openRouterModel?.toString()?.trim()
  if (selected) { return selected }
  return DEFAULT_OPENROUTER_MODEL
}

/**
 * buildOpenRouterRequest() - Construct the JSON body expected by OpenRouter's
 * OpenAI-compatible /chat/completions endpoint. The system prompt and content
 * are sent as distinct role-tagged messages.
 */
private Map buildOpenRouterRequest(String systemPrompt, String content, Integer maxTokens = null) {
  Integer tokenCap = maxTokens ?: ((settings.maxTokensPerStage ?: 800) as Integer)
  return [
    model: resolveModel(),
    messages: [
      [role: 'system', content: systemPrompt],
      [role: 'user', content: content]
    ],
    temperature: (settings.temperature ?: 0.6) as Double,
    max_tokens: tokenCap,
    top_p: 0.95
  ]
}

/**
 * extractTextFromResponse() - Pull the generated text out of the OpenRouter
 * response payload (choices[0].message.content). Returns null if not found.
 */
private String extractTextFromResponse(def responseData) {
  try {
    if (responseData?.choices && responseData.choices.size() > 0) {
      def first = responseData.choices[0]
      String finishReason = first?.finish_reason
      if (finishReason && finishReason != 'stop') {
        logWarn("OpenRouter text may be truncated/filtered. finish_reason=${finishReason}")
      }
      String text = first?.message?.content
      return text?.trim()
    }
    logWarn("OpenRouter response missing choices: ${responseData}")
    return null
  } catch (Exception e) {
    logError("Error parsing OpenRouter response: ${e.message}")
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

// =============================================================================
// INLINED HELPERS (formerly from dwinks.UtilitiesAndLoggingLibrary)
// =============================================================================
// App-only variants of the logging, log auto-off, and OAuth helpers. Inlined so
// this app carries no library #include dependency.

/**
 * logError() - Error-level log, gated on the logEnable setting.
 */
void logError(String message) {
  if (settings.logEnable != false) { log.error("${app.label ?: app.name}: ${message}") }
}

/**
 * logWarn() - Warning-level log, gated on the logEnable setting.
 */
void logWarn(String message) {
  if (settings.logEnable != false) { log.warn("${app.label ?: app.name}: ${message}") }
}

/**
 * logInfo() - Info-level log, gated on the logEnable setting.
 */
void logInfo(String message) {
  if (settings.logEnable != false) { log.info("${app.label ?: app.name}: ${message}") }
}

/**
 * logDebug() - Debug-level log, gated on both logEnable and debugLogEnable.
 */
void logDebug(String message) {
  if (settings.logEnable != false && settings.debugLogEnable != false) { log.debug("${app.label ?: app.name}: ${message}") }
}

/**
 * logsOff() - Auto-disable general logging after the 30-minute timer fires.
 */
void logsOff() {
  logWarn('Logging disabled (30 minute timeout)')
  app.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

/**
 * debugLogsOff() - Auto-disable debug logging after the 30-minute timer fires.
 */
void debugLogsOff() {
  logWarn('Debug logging disabled (30 minute timeout)')
  app.updateSetting('debugLogEnable', [value: 'false', type: 'bool'])
}

/**
 * tryCreateAccessToken() - Create the OAuth access token for webhooks if absent.
 */
void tryCreateAccessToken() {
  if (state.accessToken == null) {
    try {
      logDebug('Creating Access Token...')
      createAccessToken()
      logDebug("accessToken: ${state.accessToken}")
    } catch (e) {
      logError('OAuth is not enabled for app. Please enable.')
    }
  }
}
