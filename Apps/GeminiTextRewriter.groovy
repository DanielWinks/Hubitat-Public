// =============================================================================
// GEMINI TEXT REWRITER APP
// =============================================================================
// Author: Daniel Winks
// Description: Utilizes Google Gemini API to rewrite text snippets with
//              various styles and tones. Can be used to improve, shorten,
//              lengthen, formalize, or casualize text for notifications,
//              announcements, or other text-based automations.
//
// Features:
//   - Multiple rewriting modes (improve, shorten, lengthen, formalize, casual)
//   - Custom system prompts for specialized rewriting
//   - HTTP endpoints for integration with other apps/rules
//   - Global variable storage for easy access from rules
//   - Configurable Gemini API key and model selection
//   - Request history and caching
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

// Include the utilities and logging library from the Libraries folder
#include dwinks.UtilitiesAndLoggingLibrary

// Import required Groovy/Java classes for HTTP operations and JSON parsing
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field

/**
 * definition() - Defines app metadata for Hubitat
 * This block provides information about the app that appears in the
 * Hubitat web interface when users browse available apps.
 */
definition(
  name: 'Gemini Text Rewriter',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Use Google Gemini API to rewrite text snippets with various styles.',
  category: 'Utility',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  singleThreaded: false,
  oauth: true,
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/GeminiTextRewriter.groovy'
)

// =============================================================================
// GLOBAL CONSTANTS
// =============================================================================

// Base URL for Google Gemini API
@Field static final String GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models'

// Available Gemini models
@Field static final Map GEMINI_MODELS = [
  'gemini-3-pro-preview': 'Gemini 3 Pro (Most Powerful, Reasoning)',
  'gemini-3-flash-preview': 'Gemini 3 Flash (Fast, High Scale)',
  'gemini-2.5-flash': 'Gemini 2.5 Flash (Fast, Balanced)',
  'gemini-2.5-pro': 'Gemini 2.5 Pro (Reasoning, Complex Tasks)',
  'gemini-2.5-flash-lite': 'Gemini 2.5 Flash-Lite (Fastest, Cost-Efficient)',
  'gemini-2.0-flash': 'Gemini 2.0 Flash (Legacy)'
]

// Predefined rewriting modes
@Field static final Map REWRITE_MODES = [
  'improve': 'Improve grammar, clarity, and flow',
  'shorten': 'Make more concise',
  'lengthen': 'Expand with more detail',
  'formalize': 'Make more formal/professional',
  'casual': 'Make more casual/conversational',
  'simplify': 'Simplify language for easier understanding',
  'custom': 'Use custom system prompt'
]

// =============================================================================
// PREFERENCES / CONFIGURATION UI
// =============================================================================

/**
 * preferences - Defines the app's configuration page
 * This tells Hubitat to use the mainPage() method to build the settings UI
 */
preferences {
  page(name: 'mainPage', title: 'Gemini Text Rewriter')
}

/**
 * mainPage() - Builds the configuration UI for the app
 * This method constructs a dynamic settings page with sections for:
 * - API configuration (key, model selection)
 * - Rewriting options and modes
 * - HTTP endpoint information
 * - Testing interface
 * - Logging controls
 *
 * @return Map - A dynamicPage configuration that Hubitat renders as HTML
 */
Map mainPage() {
  return dynamicPage(name: 'mainPage', title: '<h1>Gemini Text Rewriter</h1>', install: true, uninstall: true, refreshInterval: 0) {

    // =========================================================================
    // OAUTH SETUP SECTION
    // =========================================================================
    // Ensure OAuth is enabled and access token is created for webhook access
    tryCreateAccessToken()
    if (!state.accessToken) {
      section("<h2 style='color:red;'>OAuth is not enabled for app!! Please enable in Apps Code.</h2>") {}
    }

    // =========================================================================
    // API CONFIGURATION SECTION
    // =========================================================================
    // Configure Google Gemini API credentials and model selection
    section('<h2>Google Gemini API Configuration</h2>') {
      paragraph('Get your API key from: <a href="https://aistudio.google.com/app/apikey" target="_blank">Google AI Studio</a>')

      input(
        'geminiApiKey',
        'text',
        title: 'Gemini API Key',
        required: true,
        description: 'Enter your Google Gemini API key'
      )

      input(
        'geminiModel',
        'enum',
        title: 'Gemini Model',
        options: GEMINI_MODELS,
        required: true,
        defaultValue: 'gemini-2.5-flash',
        description: 'Select which Gemini model to use'
      )

      input(
        'maxTokens',
        'number',
        title: 'Maximum Output Tokens',
        required: false,
        defaultValue: 1000,
        range: '50..8192',
        description: 'Maximum length of generated text'
      )

      input(
        'temperature',
        'decimal',
        title: 'Temperature (Creativity)',
        required: false,
        defaultValue: 0.7,
        range: '0.0..1.0',
        description: 'Higher = more creative, Lower = more focused'
      )
    }

    // =========================================================================
    // REWRITING OPTIONS SECTION
    // =========================================================================
    // Configure default rewriting behavior and custom prompts
    section('<h2>Rewriting Options</h2>') {
      input(
        'defaultMode',
        'enum',
        title: 'Default Rewriting Mode',
        options: REWRITE_MODES,
        required: true,
        defaultValue: 'improve',
        description: 'Default mode when none specified'
      )

      input(
        'customSystemPrompt',
        'text',
        title: 'Custom System Prompt (for "custom" mode)',
        required: false,
        description: 'Instructions for how to rewrite text in custom mode',
        defaultValue: 'Rewrite the following text to be more engaging and interesting.'
      )

      input(
        'preserveFormatting',
        'bool',
        title: 'Preserve Line Breaks',
        required: false,
        defaultValue: true,
        description: 'Try to maintain original formatting structure'
      )

      input(
        'storeInGlobalVar',
        'bool',
        title: 'Store Result in Global Variable',
        required: false,
        defaultValue: true,
        description: 'Store last rewritten text in connector variable for easy access'
      )

      if (settings.storeInGlobalVar) {
        input(
          'globalVarName',
          'text',
          title: 'Global Variable Name',
          required: false,
          defaultValue: 'geminiRewrittenText',
          description: 'Name of connector variable to store result'
        )
      }
    }

    // =========================================================================
    // WEBHOOKS / ENDPOINTS SECTION
    // =========================================================================
    // Display HTTP endpoints for external integration
    if (state.accessToken) {
      section('<h2>HTTP Endpoints</h2>') {
paragraph('<b>Rewrite Text Endpoint (JSON Response):</b>')
        paragraph("<code>${getFullLocalApiServerUrl()}/rewrite?access_token=${state.accessToken}</code>")

        paragraph('<b>Rewrite Text Endpoint (Plain Text Response):</b>')
        paragraph("<code>${getFullLocalApiServerUrl()}/rewriteText?access_token=${state.accessToken}</code>")

        paragraph('<b>Get Last Result Endpoint (JSON):</b>')
        paragraph("<code>${getFullLocalApiServerUrl()}/lastResult?access_token=${state.accessToken}</code>")

        paragraph('<b>Get Last Result Endpoint (Plain Text):</b>')
        paragraph("<code>${getFullLocalApiServerUrl()}/lastResultText?access_token=${state.accessToken}</code>")
      }
    }

    // =========================================================================
    // TESTING SECTION
    // =========================================================================
    // Provide interface for testing the rewriting functionality
    section('<h2>Testing</h2>') {
      input(
        'testText',
        'text',
        title: 'Test Text',
        required: false,
        description: 'Enter text to test rewriting'
      )

      input(
        'testMode',
        'enum',
        title: 'Test Mode',
        options: REWRITE_MODES,
        required: false,
        defaultValue: settings.defaultMode ?: 'improve'
      )

      input(
        name: 'testBtn',
        type: 'button',
        title: 'Test Rewrite',
        backgroundColor: 'Green',
        textColor: 'white',
        submitOnChange: true
      )

      // Display last result if available
      if (state.lastResult) {
        paragraph("<hr><b>Last Result:</b><br><pre>${state.lastResult}</pre>")
        paragraph("<small>Original: ${state.lastOriginal}</small>")
        paragraph("<small>Mode: ${state.lastMode} | Timestamp: ${state.lastTimestamp}</small>")
      }
    }

    // =========================================================================
    // HISTORY SECTION
    // =========================================================================
    // Show recent rewriting requests
    section('<h2>Request History</h2>') {
      input(
        'keepHistory',
        'bool',
        title: 'Keep Request History',
        required: false,
        defaultValue: false,
        description: 'Store history of recent requests (may use memory)'
      )

      if (settings.keepHistory && state.history) {
        Integer historySize = (state.history?.size() ?: 0)
        paragraph("<b>Recent Requests: ${historySize}</b>")

        input(
          name: 'clearHistoryBtn',
          type: 'button',
          title: 'Clear History',
          backgroundColor: 'Crimson',
          textColor: 'white',
          submitOnChange: true
        )
      }
    }

    // =========================================================================
    // LOGGING SECTION
    // =========================================================================
    // Configure logging verbosity
    section('<h2>Logging</h2>') {
      input(
        'logEnable',
        'bool',
        title: 'Enable Logging',
        required: false,
        defaultValue: true
      )

      input(
        'debugLogEnable',
        'bool',
        title: 'Enable Debug Logging',
        required: false,
        defaultValue: false
      )

      input(
        'descriptionTextEnable',
        'bool',
        title: 'Enable Description Text',
        required: false,
        defaultValue: true
      )

      input(
        name: 'initializeBtn',
        type: 'button',
        title: 'Initialize',
        backgroundColor: 'Crimson',
        textColor: 'white',
        submitOnChange: true
      )
    }

    // =========================================================================
    // APP LABEL SECTION
    // =========================================================================
    // Allow user to name this app instance
    section() {
      label(
        title: 'Enter a name for this app instance',
        required: false
      )
    }
  }
}

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================
// Note: installed(), updated(), and uninstalled() are provided by
// UtilitiesAndLoggingLibrary. The library automatically calls initialize()
// from installed() and configure() from updated().

/**
 * configure() - Called by library's updated() method
 * Reloads configuration and reinitializes the app.
 */
void configure() {
  logInfo('Configuring Gemini Text Rewriter')
  unsubscribe()

  // Subscribe to location events for cross-app communication
  subscribe(location, 'geminiRewriteRequest', 'handleRewriteRequestEvent')

  // Clean up global variables from previous config if name changed
  if (settings.storeInGlobalVar && settings.globalVarName) {
    try {
      // Could add logic here to clean up old var if name changed
    } catch (Exception e) {
      logDebug("Note during configure: ${e.message}")
    }
  }

  initialize()
}

/**
 * initialize() - Main initialization method
 * Sets up the app's initial state and prepares for operation.
 * Called automatically by library's installed() method.
 */
void initialize() {
  logDebug('Initializing Gemini Text Rewriter')

  // Clear stale state
  state.remove('lastError')

  // Initialize history if enabled
  if (settings.keepHistory && !state.history) {
    state.history = []
  } else if (!settings.keepHistory) {
    state.remove('history')
  }

  // Validate API key is present
  if (!settings.geminiApiKey) {
    logWarn('No Gemini API key configured')
  }

  logInfo('Gemini Text Rewriter initialized')

  // Schedule logs to turn off after 30 minutes (if enabled)
  if (settings.logEnable) { runIn(1800, 'logsOff') }
  if (settings.debugLogEnable) { runIn(1800, 'debugLogsOff') }
}

// =============================================================================
// WEBHOOK / HTTP ENDPOINT MAPPINGS
// =============================================================================

/**
 * mappings - Defines HTTP endpoints for external access
 * These endpoints allow other apps, rules, or external systems to
 * trigger text rewriting via HTTP requests.
 */
mappings {
  path('/rewrite') {
    action: [
      GET: 'rewriteWebhookGet',
      POST: 'rewriteWebhookPost'
    ]
  }
  path('/rewriteText') {
    action: [
      GET: 'rewriteWebhookTextGet',
      POST: 'rewriteWebhookTextPost'
    ]
  }
  path('/lastResult') {
    action: [
      GET: 'getLastResultWebhook'
    ]
  }
  path('/lastResultText') {
    action: [
      GET: 'getLastResultTextWebhook'
    ]
  }
}

/**
 * rewriteWebhookGet() - Handle GET requests to rewrite endpoint
 * Accepts text and mode as URL parameters.
 * Example: /rewrite?text=hello+world&mode=improve
 *
 * @return Map - HTTP response with rewritten text or error
 */
Map rewriteWebhookGet() {
  try {
    // Extract parameters from URL query string
    String text = params.text ?: ''
    String mode = params.mode ?: settings.defaultMode

    if (!text) {
      return render(
        contentType: 'application/json',
        data: JsonOutput.toJson([error: 'No text provided']),
        status: 400
      )
    }

    // Call the main rewriting method
    Map result = rewriteText(text, mode)

    if (result.success) {
      return render(
        contentType: 'application/json',
        data: JsonOutput.toJson([
          success: true,
          original: text,
          rewritten: result.text,
          mode: mode
        ]),
        status: 200
      )
    } else {
      return render(
        contentType: 'application/json',
        data: JsonOutput.toJson([
          success: false,
          error: result.error
        ]),
        status: 500
      )
    }
  } catch (Exception e) {
    logError("Webhook error: ${e.message}")
    return render(
      contentType: 'application/json',
      data: JsonOutput.toJson([error: "Internal error: ${e.message}"]),
      status: 500
    )
  }
}

/**
 * rewriteWebhookPost() - Handle POST requests to rewrite endpoint
 * Accepts JSON body with text and optional mode.
 * Example body: {"text": "hello world", "mode": "improve"}
 *
 * @return Map - HTTP response with rewritten text or error
 */
Map rewriteWebhookPost() {
  try {
    // Parse JSON from request body
    def jsonSlurper = new JsonSlurper()
    def requestData = jsonSlurper.parseText(request.body)

    String text = requestData.text ?: ''
    String mode = requestData.mode ?: settings.defaultMode

    if (!text) {
      return render(
        contentType: 'application/json',
        data: JsonOutput.toJson([error: 'No text provided']),
        status: 400
      )
    }

    // Call the main rewriting method
    Map result = rewriteText(text, mode)

    if (result.success) {
      return render(
        contentType: 'application/json',
        data: JsonOutput.toJson([
          success: true,
          original: text,
          rewritten: result.text,
          mode: mode
        ]),
        status: 200
      )
    } else {
      return render(
        contentType: 'application/json',
        data: JsonOutput.toJson([
          success: false,
          error: result.error
        ]),
        status: 500
      )
    }
  } catch (Exception e) {
    logError("Webhook POST error: ${e.message}")
    return render(
      contentType: 'application/json',
      data: JsonOutput.toJson([error: "Internal error: ${e.message}"]),
      status: 500
    )
  }
}

/**
 * rewriteWebhookTextGet() - Handle GET requests returning plain text
 * Accepts text and mode as URL parameters.
 * Returns just the rewritten string, or error message as text.
 *
 * @return Map - HTTP response with plain text result
 */
Map rewriteWebhookTextGet() {
  try {
    String text = params.text ?: ''
    String mode = params.mode ?: settings.defaultMode

    if (!text) {
      return render(contentType: 'text/plain', data: 'Error: No text provided', status: 400)
    }

    Map result = rewriteText(text, mode)

    if (result.success) {
      return render(contentType: 'text/plain', data: result.text, status: 200)
    } else {
      return render(contentType: 'text/plain', data: "Error: ${result.error}", status: 500)
    }
  } catch (Exception e) {
    logError("Webhook Text GET error: ${e.message}")
    return render(contentType: 'text/plain', data: "Error: ${e.message}", status: 500)
  }
}

/**
 * rewriteWebhookTextPost() - Handle POST requests returning plain text
 * Accepts JSON body or Form encoded data.
 * Returns just the rewritten string.
 *
 * @return Map - HTTP response with plain text result
 */
Map rewriteWebhookTextPost() {
  try {
    String text = ''
    String mode = settings.defaultMode

    // Attempt to handle both JSON and Form encodings
    if (request.JSON) {
      text = request.JSON.text ?: ''
      mode = request.JSON.mode ?: settings.defaultMode
    } else {
      // Clean up body if it's sent as raw string
      def slurper = new JsonSlurper()
      try {
        def bodyMap = slurper.parseText(request.body)
        text = bodyMap.text ?: ''
        mode = bodyMap.mode ?: settings.defaultMode
      } catch (e) {
        text = request.body
      }
    }

    if (!text) {
      return render(contentType: 'text/plain', data: 'Error: No text provided', status: 400)
    }

    Map result = rewriteText(text, mode)

    if (result.success) {
      return render(contentType: 'text/plain', data: result.text, status: 200)
    } else {
      return render(contentType: 'text/plain', data: "Error: ${result.error}", status: 500)
    }
  } catch (Exception e) {
    logError("Webhook Text POST error: ${e.message}")
    return render(contentType: 'text/plain', data: "Error: ${e.message}", status: 500)
  }
}

/**
 * getLastResultWebhook() - Returns the last rewriting result
 * Allows easy retrieval of the most recent rewritten text.
 *
 * @return Map - HTTP response with last result or error
 */
Map getLastResultWebhook() {
  try {
    if (state.lastResult) {
      return render(
        contentType: 'application/json',
        data: JsonOutput.toJson([
          success: true,
          original: state.lastOriginal,
          rewritten: state.lastResult,
          mode: state.lastMode,
          timestamp: state.lastTimestamp
        ]),
        status: 200
      )
    } else {
      return render(
        contentType: 'application/json',
        data: JsonOutput.toJson([error: 'No results available yet']),
        status: 404
      )
    }
  } catch (Exception e) {
    logError("Error retrieving last result: ${e.message}")
    return render(
      contentType: 'application/json',
      data: JsonOutput.toJson([error: "Internal error: ${e.message}"]),
      status: 500
    )
  }
}

/**
 * getLastResultTextWebhook() - Returns the last rewriting result as plain text
 * Allows easy retrieval of the most recent rewritten text string.
 *
 * @return Map - HTTP response with last result text or error
 */
Map getLastResultTextWebhook() {
  try {
    if (state.lastResult) {
      return render(
        contentType: 'text/plain',
        data: state.lastResult,
        status: 200
      )
    } else {
      return render(
        contentType: 'text/plain',
        data: 'Error: No results available yet',
        status: 404
      )
    }
  } catch (Exception e) {
    logError("Error retrieving last result text: ${e.message}")
    return render(
      contentType: 'text/plain',
      data: "Error: ${e.message}",
      status: 500
    )
  }
}

// =============================================================================
// UI BUTTON HANDLERS
// =============================================================================

/**
 * appButtonHandler() - Responds to button clicks in the settings page
 * Handles test, initialize, and clear history button actions.
 *
 * @param buttonId - The ID of the button that was clicked
 */
void appButtonHandler(String buttonId) {
  switch (buttonId) {
    case 'testBtn':
      // Run a test rewrite with the configured test text
      if (settings.testText) {
        String mode = settings.testMode ?: settings.defaultMode
        Map result = rewriteText(settings.testText, mode)
        if (result.success) {
          logInfo("Test completed successfully: ${result.text}")
        } else {
          logError("Test failed: ${result.error}")
        }
      } else {
        logWarn('No test text provided')
      }
      break

    case 'initializeBtn':
      // Reinitialize the app
      initialize()
      break

    case 'clearHistoryBtn':
      // Clear the request history
      state.history = []
      logInfo('Request history cleared')
      break
  }
}

// =============================================================================
// LOCATION EVENT HANDLERS FOR CROSS-APP COMMUNICATION
// =============================================================================

/**
 * handleRewriteRequestEvent() - Process rewrite requests from other apps via location events
 * This allows other apps to request text rewriting without HTTP calls
 *
 * Expected event format:
 *   name: 'geminiRewriteRequest'
 *   value: text to rewrite (String)
 *   data: Map with optional 'mode' and 'requestId'
 */
void handleRewriteRequestEvent(Event evt) {
  try {
    logDebug("Received rewrite request event: ${evt.value}")

    String textToRewrite = evt.value
    Map eventData = evt.data ? parseJson(evt.data) : [:]
    String mode = eventData?.mode ?: settings.defaultMode ?: 'improve'
    String requestId = eventData?.requestId ?: UUID.randomUUID().toString()

    logDebug("Processing rewrite request ID: ${requestId}, mode: ${mode}")

    // Call the main rewrite method
    Map result = rewriteText(textToRewrite, mode)

    // Send response back via location event
    Map responseData = [
      requestId: requestId,
      success: result.success,
      rewritten: result.text,
      error: result.error,
      mode: mode
    ]

    sendLocationEvent(
      name: 'geminiRewriteResponse',
      value: result.success ? result.text : textToRewrite,
      data: JsonOutput.toJson(responseData)
    )

    logDebug("Sent rewrite response for request ID: ${requestId}")

  } catch (Exception e) {
    logError("Error handling rewrite request event: ${e.message}")
    // Send error response
    sendLocationEvent(
      name: 'geminiRewriteResponse',
      value: evt.value,
      data: JsonOutput.toJson([
        success: false,
        error: e.message,
        rewritten: evt.value
      ])
    )
  }
}

// =============================================================================
// CORE REWRITING LOGIC
// =============================================================================

/**
 * rewriteText() - Main method to rewrite text using Gemini API
 * This is the core functionality that:
 * 1. Builds the appropriate system prompt based on mode
 * 2. Constructs the API request
 * 3. Calls the Gemini API
 * 4. Parses and returns the result
 * 5. Stores result in state and optionally in global variable
 * 6. Adds to history if enabled
 *
 * @param text - The text to rewrite
 * @param mode - The rewriting mode (improve, shorten, etc.)
 * @return Map - Result map with success flag and text or error
 */
Map rewriteText(String text, String mode = null) {
  try {
    // Use default mode if none provided
    if (!mode) {
      mode = settings.defaultMode ?: 'improve'
    }

    // Validate API key
    if (!settings.geminiApiKey) {
      String error = 'No Gemini API key configured'
      logError(error)
      return [success: false, error: error]
    }

    // Build the system prompt based on selected mode
    String systemPrompt = buildSystemPrompt(mode)

    logDebug("Rewriting text with mode: ${mode}")
    logDebug("System prompt: ${systemPrompt}")
    logDebug("Original text: ${text}")

    // Construct the API request
    Map requestBody = buildGeminiRequest(systemPrompt, text)

    // Build the API endpoint URL
    String modelName = (settings.geminiModel ?: 'gemini-2.5-flash').trim()
    String apiKey = settings.geminiApiKey?.trim()
    String apiUrl = "${GEMINI_API_BASE}/${modelName}:generateContent?key=${apiKey}"

    // Log the URL for debugging (masking key)
    logDebug("API URL: ${apiUrl.replaceAll("key=[^&]+", "key=******")}")

    // Make the HTTP request to Gemini API
    Map response = makeGeminiApiCall(apiUrl, requestBody)

    if (response.success) {
      // Extract the rewritten text from the response
      String rewrittenText = extractTextFromResponse(response.data)

      if (rewrittenText) {
        // Store the result in state
        state.lastResult = rewrittenText
        state.lastOriginal = text
        state.lastMode = mode
        state.lastTimestamp = new Date().format('yyyy-MM-dd HH:mm:ss')

        // Store in global variable if configured
        if (settings.storeInGlobalVar && settings.globalVarName) {
          storeInGlobalVariable(rewrittenText)
        }

        // Add to history if enabled
        if (settings.keepHistory) {
          addToHistory(text, rewrittenText, mode)
        }

        // Send location event for other apps to listen to
        sendLocationEvent(
          name: 'geminiTextRewritten',
          value: rewrittenText,
          data: JsonOutput.toJson([
            success: true,
            original: text,
            rewritten: rewrittenText,
            mode: mode,
            timestamp: state.lastTimestamp
          ])
        )

        logInfo("Successfully rewrote text (mode: ${mode})")
        logDebug("Rewritten text: ${rewrittenText}")

        return [success: true, text: rewrittenText]
      } else {
        String error = 'No text returned from Gemini API'
        logError(error)
        state.lastError = error
        return [success: false, error: error]
      }
    } else {
      logError("API call failed: ${response.error}")
      state.lastError = response.error
      return [success: false, error: response.error]
    }

  } catch (Exception e) {
    String error = "Error rewriting text: ${e.message}"
    logError(error)
    state.lastError = error
    return [success: false, error: error]
  }
}

/**
 * buildSystemPrompt() - Constructs the system prompt based on mode
 * Each mode has a specific instruction for how to rewrite the text.
 *
 * @param mode - The rewriting mode
 * @return String - The system prompt to send to Gemini
 */
String buildSystemPrompt(String mode) {
  String baseInstruction = ''

  switch (mode) {
    case 'improve':
      baseInstruction = 'You are an expert editor. Improve the grammar, clarity, flow, and sentence structure of the following text perfectly. Maintain the original meaning and tone, but make it read like professional, high-quality writing.'
      break

    case 'shorten':
      baseInstruction = 'You are an expert summarizer. Rewrite the following text to be significantly more concise. Remove unnecessary words and fluff while strictly preserving all key information, facts, and the core message. The result should be brief and punchy.'
      break

    case 'lengthen':
      baseInstruction = 'You are a creative writer. Expand the following text with descriptive details, clearer explanations, and better context. Elaborate on the core message to make it more engaging and comprehensive, without adding false information.'
      break

    case 'formalize':
      baseInstruction = 'You are a professional communications expert. Rewrite the following text to use formal, professional, and polite language suitable for a business email, official announcement, or formal document. Avoid slang and contractions.'
      break

    case 'casual':
      baseInstruction = 'You are a friendly companion. Rewrite the following text to sound casual, conversational, relaxed, and approachable. Use contractions and simpler language suitable for a text message or chat with a friend.'
      break

    case 'simplify':
      baseInstruction = 'You are a teacher. Rewrite the following text to be extremely easy to understand. Use simple vocabulary, short sentences, and clear structure. Aim for a 5th-grade reading level.'

    case 'custom':
      baseInstruction = settings.customSystemPrompt ?: 'Rewrite the following text.'
      break

    default:
      baseInstruction = 'Improve the following text.'
  }

  // Add formatting instruction if enabled
  if (settings.preserveFormatting) {
    baseInstruction += ' Preserve any line breaks and paragraph structure from the original.'
  }

  // Add final instruction
  baseInstruction += ' Return only the rewritten text without any additional commentary or explanation.'

  return baseInstruction
}

/**
 * buildGeminiRequest() - Constructs the JSON request body for Gemini API
 * Builds the proper structure expected by Google's Gemini API.
 *
 * @param systemPrompt - Instructions for how to rewrite
 * @param text - The text to rewrite
 * @return Map - Request body structure
 */
Map buildGeminiRequest(String systemPrompt, String text) {
  return [
    contents: [
      [
        parts: [
          [text: "${systemPrompt}\n\nText to rewrite:\n${text}"]
        ]
      ]
    ],
    generationConfig: [
      temperature: (settings.temperature ?: 0.7) as Double,
      maxOutputTokens: (settings.maxTokens ?: 1000) as Integer, // Default to 1000 if not set
      topP: 0.95,
      topK: 40
    ]
  ]
}

/**
 * makeGeminiApiCall() - Executes the HTTP request to Gemini API
 * Makes a POST request to the Gemini API with proper error handling.
 *
 * @param apiUrl - The full API endpoint URL with API key
 * @param requestBody - The request payload
 * @return Map - Response with success flag and data or error
 */
Map makeGeminiApiCall(String apiUrl, Map requestBody) {
  try {
    def params = [
      uri: apiUrl,
      contentType: 'application/json',
      requestContentType: 'application/json',
      body: JsonOutput.toJson(requestBody),
      timeout: 30
    ]

    def responseData = null
    def responseStatus = null

    httpPost(params) { response ->
      responseStatus = response.status
      responseData = response.data
      logDebug("API Response Status: ${responseStatus}")
    }

    if (responseStatus == 200 && responseData) {
      return [success: true, data: responseData]
    } else {
      String error = "API returned status ${responseStatus}"
      logError(error)
      return [success: false, error: error]
    }

  } catch (groovyx.net.http.HttpResponseException e) {
    String error = "HTTP request failed: ${e.message}"
    String detailedError = error

    // Attempt to extract detailed error message from response body
    try {
      if (e.response && e.response.data) {
        def errorJson = e.response.data
        logDebug("Error response data: ${errorJson}")
        if (errorJson.error && errorJson.error.message) {
          detailedError = "Gemini API Error: ${errorJson.error.message}"
        }
      }
    } catch (Exception ex) {
      logDebug("Could not parse error response: ${ex.message}")
    }

    logError(detailedError)

    if (e.statusCode == 404) {
      logError("404 Not Found - Please check your API Key and selected Model. Ensure you have access to the selected model.")
    }

    return [success: false, error: detailedError]
  } catch (Exception e) {
    String error = "HTTP request failed: ${e.message}"
    logError(error)
    return [success: false, error: error]
  }
}

/**
 * extractTextFromResponse() - Extracts the generated text from API response
 * Parses the Gemini API response structure to get the generated content.
 *
 * @param responseData - The API response data
 * @return String - The extracted text, or null if not found
 */
String extractTextFromResponse(def responseData) {
  try {
    // Navigate the Gemini API response structure
    // Structure: responseData.candidates[0].content.parts[0].text
    if (responseData?.candidates && responseData.candidates.size() > 0) {
      def firstCandidate = responseData.candidates[0]

      // Check for non-standard finish reasons (e.g., truncation or safety)
      if (firstCandidate.finishReason && firstCandidate.finishReason != 'STOP') {
        logWarn("Gemini generated text may be truncated or filtered. Finish Reason: ${firstCandidate.finishReason}")
      }

      if (firstCandidate?.content?.parts && firstCandidate.content.parts.size() > 0) {
        return firstCandidate.content.parts[0].text?.trim()
      }
    }
    logError('Could not find text in API response')
    logDebug("Response data: ${responseData}")
    return null
  } catch (Exception e) {
    logError("Error extracting text from response: ${e.message}")
    return null
  }
}

/**
 * storeInGlobalVariable() - Stores result in a Hubitat connector variable
 * This allows easy access to the rewritten text from Rule Machine and other apps.
 *
 * @param text - The text to store
 */
void storeInGlobalVariable(String text) {
  try {
    String varName = settings.globalVarName ?: 'geminiRewrittenText'
    setGlobalVar(varName, text)
    logDebug("Stored result in global variable: ${varName}")
  } catch (Exception e) {
    logError("Could not store in global variable: ${e.message}")
  }
}

/**
 * addToHistory() - Adds a rewrite operation to the history
 * Maintains a limited history of recent rewrites if enabled.
 *
 * @param original - Original text
 * @param rewritten - Rewritten text
 * @param mode - Mode used
 */
void addToHistory(String original, String rewritten, String mode) {
  try {
    if (!state.history) {
      state.history = []
    }

    // Add new entry at the beginning
    state.history.add(0, [
      original: original,
      rewritten: rewritten,
      mode: mode,
      timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
    ])

    // Keep only last 20 entries to avoid excessive memory usage
    if (state.history.size() > 20) {
      state.history = state.history.take(20)
    }

  } catch (Exception e) {
    logError("Error adding to history: ${e.message}")
  }
}

// =============================================================================
// UTILITY METHODS
// =============================================================================
// Note: tryCreateAccessToken() is provided by UtilitiesAndLoggingLibrary
// and is called in mainPage() to ensure OAuth token exists for webhooks.
