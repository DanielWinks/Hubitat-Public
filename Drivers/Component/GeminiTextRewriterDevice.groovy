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
 */

#include dwinks.UtilitiesAndLoggingLibrary

metadata {
  definition(
    name: 'Gemini Text Rewriter Device',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/GeminiTextRewriterDevice.groovy'
  ) {
    capability 'Actuator'

    command 'rewriteText', [[name: 'text', type: 'STRING', description: 'Text to rewrite using Gemini API']]
    command 'rewriteTextWithPrompt', [
      [name: 'text', type: 'STRING', description: 'Text to rewrite'],
      [name: 'customPrompt', type: 'STRING', description: 'Custom prompt/instructions for rewriting']
    ]
    command 'setCustomInstructions', [[name: 'instructions', type: 'STRING', description: 'Custom rewrite instructions/prompt']]
    command 'clearCustomInstructions'

    attribute 'lastRewrittenText', 'string'
    attribute 'lastOriginalText', 'string'
    attribute 'lastMode', 'string'
    attribute 'status', 'string'
    attribute 'customInstructions', 'string'
    attribute 'defaultMode', 'string'
  }
}

preferences {
  input name: 'defaultMode', type: 'enum', title: 'Default Rewrite Mode', required: false, defaultValue: 'improve',
    options: ['improve', 'shorten', 'lengthen', 'formalize', 'casual', 'simplify', 'custom']
  input name: 'logEnable', type: 'bool', title: 'Enable Logging', required: false, defaultValue: true
  input name: 'debugLogEnable', type: 'bool', title: 'Enable debug logging', required: false, defaultValue: false
}

void initialize() {
  configure()
  // Set default mode attribute from preferences
  if (settings.defaultMode) {
    sendEvent(name: 'defaultMode', value: settings.defaultMode)
  }
}

void configure() {
  unschedule()
  // Update default mode if changed in preferences
  if (settings.defaultMode) {
    sendEvent(name: 'defaultMode', value: settings.defaultMode)
  }
}

/**
 * rewriteText() - Command to rewrite text using parent app's Gemini API integration
 * This command calls the parent app to perform the actual rewriting.
 * Uses the device's default mode and custom instructions if set.
 *
 * @param text - The text to rewrite
 */
void rewriteText(String text) {
  logDebug("rewriteText command called with text: ${text}")

  if (!text) {
    logWarn('No text provided to rewrite')
    sendEvent(name: 'status', value: 'error: no text provided')
    return
  }

  sendEvent(name: 'lastOriginalText', value: text)
  sendEvent(name: 'status', value: 'processing')

  // Get default mode and custom instructions from device state
  String mode = device.currentValue('defaultMode') ?: settings.defaultMode ?: 'improve'
  String customInstructions = device.currentValue('customInstructions')

  sendEvent(name: 'lastMode', value: mode)

  // Call parent app to perform the rewrite with mode and custom instructions
  getParent()?.componentRewriteText(device, text, mode, customInstructions)
}

/**
 * rewriteTextWithPrompt() - Rewrite text with a directly specified custom prompt
 * This is a convenience command for one-step custom rewriting without needing
 * to call setCustomInstructions first. Useful for Rule Machine actions.
 *
 * @param text - The text to rewrite
 * @param customPrompt - The custom prompt/instructions to use for rewriting
 */
void rewriteTextWithPrompt(String text, String customPrompt) {
  logDebug("rewriteTextWithPrompt called with text: ${text}, customPrompt: ${customPrompt}")

  if (!text) {
    logWarn('No text provided to rewrite')
    sendEvent(name: 'status', value: 'error: no text provided')
    return
  }

  if (!customPrompt) {
    logWarn('No custom prompt provided')
    sendEvent(name: 'status', value: 'error: no custom prompt provided')
    return
  }

  sendEvent(name: 'lastOriginalText', value: text)
  sendEvent(name: 'status', value: 'processing')
  sendEvent(name: 'lastMode', value: 'custom')

  // Call parent app with custom mode and the provided prompt
  getParent()?.componentRewriteText(device, text, 'custom', customPrompt)
}

/**
 * setCustomInstructions() - Store custom rewrite instructions for this device
 * These instructions will be used as a custom prompt when rewriting text.
 *
 * @param instructions - The custom rewrite instructions/prompt
 */
void setCustomInstructions(String instructions) {
  logDebug("setCustomInstructions called with: ${instructions}")

  if (!instructions) {
    logWarn('No instructions provided')
    return
  }

  sendEvent(name: 'customInstructions', value: instructions)
  logInfo("Custom instructions set: ${instructions}")
}

/**
 * clearCustomInstructions() - Clear any custom rewrite instructions
 */
void clearCustomInstructions() {
  logDebug("clearCustomInstructions called")
  sendEvent(name: 'customInstructions', value: '')
  logInfo("Custom instructions cleared")
}
