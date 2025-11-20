#include dwinks.UtilitiesAndLoggingLibrary

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

definition(
    name: "Device Groups",
    namespace: "dwinks",
    author: "Daniel Winks",
    description: "Groups multiple contact or motion sensors into a single device for simplified monitoring.",
    category: "Convenience, Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
	documentationLink: "")

preferences {
  page(name: "pageMain", title: "", install: true, uninstall: true)
}

void configure() {
}

Map pageMain() {
    dynamicPage(name: "pageMain") {
    isInstalled()
		if(app.getInstallationState() == 'COMPLETE') {
			section("${app.label}") {
				paragraph "Combines multiple contact or motion sensors into a single device for simplified monitoring."
			}
			childApps()
			section("General") {
        label title: "Enter a name for parent app (optional)", required: false
      }
		}
	}
}

void isInstalled() {
	if (app.getInstallationState() != 'COMPLETE') {
		section () {
			paragraph "Please click <b>Done</b> to install the parent app."
		}
  }
}

Map childApps() {
  return section("Child Apps") {
    app(name: "contactSensorChild", appName: "Contact Sensor Group Child", namespace: "dwinks", title: "New Contact Sensor Group", multiple: true)
    app(name: "motionSensorChild", appName: "Motion Sensor Group Child", namespace: "dwinks", title: "New Motion Sensor Group", multiple: true)
  }
}
