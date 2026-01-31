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

    attribute 'lastRewrittenText', 'string'
    attribute 'lastOriginalText', 'string'
    attribute 'lastMode', 'string'
    attribute 'status', 'string'
  }
}

preferences {
  input name: 'logEnable', type: 'bool', title: 'Enable Logging', required: false, defaultValue: true
  input name: 'debugLogEnable', type: 'bool', title: 'Enable debug logging', required: false, defaultValue: false
}

void initialize() { configure() }
void configure() { unschedule() }

/**
 * rewriteText() - Command to rewrite text using parent app's Gemini API integration
 * This command calls the parent app to perform the actual rewriting.
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

  // Call parent app to perform the rewrite
  parent?.componentRewriteText(device, text)
}
