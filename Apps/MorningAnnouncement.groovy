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
import com.hubitat.app.DeviceWrapper
import com.hubitat.hub.domain.Event

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

      input 'weatherReportDevice', 'capability.sensor', title: '<b>Weather Report Device</b>', required: false, multiple: false, submitOnChange: true
      if (weatherReportDevice) {
        input 'weatherReportAttribute', 'text', title: 'Weather Report Attribute Name', required: true, defaultValue: 'detailedForecast'
      }

      input 'weatherAlertsDevice', 'capability.sensor', title: '<b>Weather Alerts Device</b>', required: false, multiple: false, submitOnChange: true
      if (weatherAlertsDevice) {
        input 'weatherAlertsAttribute', 'text', title: 'Weather Alerts Attribute Name', required: true, defaultValue: 'alertsFriendlyText'
      }

      input 'calendarEventsDevice', 'capability.sensor', title: '<b>Calendar Events Device</b>', required: false, multiple: false, submitOnChange: true
      if (calendarEventsDevice) {
        input 'calendarEventsAttribute', 'text', title: 'Calendar Events Attribute Name', required: true, defaultValue: 'nextEventFriendlyString'
      }
    }

    section('<b>Gemini Text Rewriter Integration</b>') {
      paragraph 'Enable AI enhancement using your Gemini Text Rewriter app (must be installed).'
      input 'useGeminiRewriter', 'bool', title: 'Enable AI Enhancement', defaultValue: true
      paragraph '''<small>When enabled, this app will communicate with your Gemini Text Rewriter app via location events to enhance the announcement text. Make sure you have the Gemini Text Rewriter app installed and configured.</small>'''
    }

    section('<b>Announcement Instructions</b>') {
      paragraph 'Customize how the AI combines weather, alerts, and calendar into your announcement.'
      input 'customInstructions', 'text',
        title: 'Instructions for AI',
        required: true,
        defaultValue: '''Create a warm, friendly morning announcement from the following information. Keep it natural and conversational, like a helpful assistant. Include:
1. A cheerful greeting appropriate for the time of day
2. Today's weather forecast in a brief, easy-to-understand way
3. Any weather alerts (if present) with appropriate emphasis
4. Today's calendar events (if any) in a helpful reminder format
5. A positive closing thought or encouragement

Keep the tone upbeat and informative. Make it feel personal and engaging, not robotic.''',
        description: 'This prompt guides the AI on how to structure and present your morning announcement.'
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
 * generateAnnouncement() - Main method to generate morning announcement
 */
void generateAnnouncement() {
  logInfo('Generating morning announcement...')

  try {
    // Gather input data from devices
    String weatherReport = getDeviceAttributeValue(settings.weatherReportDevice, settings.weatherReportAttribute)
    String weatherAlerts = getDeviceAttributeValue(settings.weatherAlertsDevice, settings.weatherAlertsAttribute)
    String calendarEvents = getDeviceAttributeValue(settings.calendarEventsDevice, settings.calendarEventsAttribute)

    // Build the input text for AI
    String combinedInput = buildCombinedInput(weatherReport, weatherAlerts, calendarEvents)

    logDebug("Combined input: ${combinedInput}")

    // Get AI-enhanced announcement
    String announcement = enhanceWithAI(combinedInput)

    // Store results
    state.lastAnnouncement = announcement
    state.lastGenerated = new Date().format('yyyy-MM-dd HH:mm:ss')

    // Store in global variable
    if (settings.globalVariableName) {
      setGlobalVar(settings.globalVariableName, announcement)
      logInfo("Stored announcement in global variable: ${settings.globalVariableName}")
    }

    logInfo('Morning announcement generated successfully')

  } catch (Exception e) {
    logError("Failed to generate announcement: ${e.message}")
    state.lastAnnouncement = "Error: ${e.message}"
    state.lastGenerated = new Date().format('yyyy-MM-dd HH:mm:ss')
  }
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
 */
private String buildCombinedInput(String weatherReport, String weatherAlerts, String calendarEvents) {
  StringBuilder input = new StringBuilder()

  // Add custom instructions
  input.append(settings.customInstructions ?: 'Create a morning announcement from the following:')
  input.append('\n\n')

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
    input.append('TODAY\'S CALENDAR:\n')
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
 * enhanceWithAI() - Send text to Gemini for enhancement via location events
 */
private String enhanceWithAI(String inputText) {
  // Check if Gemini enhancement is enabled
  if (!settings.useGeminiRewriter) {
    logWarn('Gemini Text Rewriter not enabled - returning raw text')
    return inputText
  }

  try {
    // Generate unique request ID
    String requestId = UUID.randomUUID().toString()

    logDebug("Sending rewrite request with ID: ${requestId}")

    // Store request details in state for response handler
    state.pendingGeminiRequest = [
      requestId: requestId,
      originalText: inputText,
      timestamp: now()
    ]

    // Prepare request data
    Map requestData = [
      requestId: requestId,
      mode: 'custom'
    ]

    // Send location event to Gemini Text Rewriter app
    sendLocationEvent(
      name: 'geminiRewriteRequest',
      value: inputText,
      data: JsonOutput.toJson(requestData)
    )

    logDebug("Sent rewrite request via location event")

    // Wait for response (with timeout)
    Integer maxWaitSeconds = 30
    Integer waitInterval = 100 // milliseconds
    Integer totalWait = 0

    while (totalWait < (maxWaitSeconds * 1000)) {
      // Check if response has arrived
      if (state.geminiResponse?.requestId == requestId) {
        logDebug("Received response for request ID: ${requestId}")
        Map response = state.geminiResponse
        state.remove('geminiResponse')
        state.remove('pendingGeminiRequest')

        if (response.success && response.rewritten) {
          logInfo('AI enhancement successful')
          return response.rewritten
        } else {
          logWarn("AI enhancement failed: ${response.error ?: 'Unknown error'}")
          return inputText
        }
      }

      // Wait a bit before checking again
      pauseExecution(waitInterval)
      totalWait += waitInterval
    }

    // Timeout - no response received
    logWarn("Gemini response timeout after ${maxWaitSeconds} seconds")
    state.remove('pendingGeminiRequest')
    return inputText

  } catch (Exception e) {
    logError("AI enhancement error: ${e.message}")
    logError("Error type: ${e.class.name}")
    logWarn("Returning raw concatenated text without AI enhancement")
    state.remove('pendingGeminiRequest')
    return inputText
  }
}

/**
 * handleGeminiResponseEvent() - Process responses from Gemini Text Rewriter app
 */
void handleGeminiResponseEvent(Event evt) {
  try {
    logDebug("Received Gemini response event: ${evt.value?.take(50)}...")

    Map responseData = evt.data ? parseJson(evt.data) : [:]
    String requestId = responseData?.requestId

    // Check if this response matches our pending request
    if (state.pendingGeminiRequest?.requestId == requestId) {
      logDebug("Response matches pending request ID: ${requestId}")

      // Store response for the waiting enhanceWithAI method
      state.geminiResponse = [
        requestId: requestId,
        success: responseData.success,
        rewritten: responseData.rewritten ?: evt.value,
        error: responseData.error,
        mode: responseData.mode
      ]
    } else {
      logDebug("Response ID ${requestId} doesn't match pending request, ignoring")
    }

  } catch (Exception e) {
    logError("Error handling Gemini response event: ${e.message}")
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
