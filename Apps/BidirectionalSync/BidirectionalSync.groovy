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

// =============================================================================
// BIDIRECTIONAL SYNC - PARENT APP
// =============================================================================
// Description: Parent container for Bidirectional Sync child instances.
//              Each child instance keeps two lighting devices in perfect
//              bidirectional sync using echo detection to prevent loops.
// =============================================================================

definition(
  name: 'Bidirectional Sync',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Keep two lighting devices in perfect bidirectional sync. Changes on either device are immediately mirrored to the other.',
  category: 'Convenience',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  documentationLink: ''
)

preferences {
  page(name: 'mainPage', title: '', install: true, uninstall: true)
}

// =============================================================================
// Lifecycle Methods
// =============================================================================

void installed() {
  log.info("${app.label ?: app.name}: Installed")
}

void updated() {
  log.info("${app.label ?: app.name}: Updated")
}

void uninstalled() {
  log.info("${app.label ?: app.name}: Uninstalled")
}

// =============================================================================
// Pages
// =============================================================================

Map mainPage() {
  dynamicPage(name: 'mainPage') {
    isInstalled()
    if (app.getInstallationState() == 'COMPLETE') {
      section("${app.label ?: 'Bidirectional Sync'}") {
        paragraph('Keep two lighting devices in perfect bidirectional sync. Changes on either device are immediately mirrored to the other.')
      }
      section('Sync Instances') {
        app(name: 'bidirectionalSyncChild', appName: 'Bidirectional Sync Child', namespace: 'dwinks', title: 'New Sync Instance', multiple: true)
      }
      section('General') {
        label(title: 'Enter a name for parent app (optional)', required: false)
      }
    }
  }
}

void isInstalled() {
  if (app.getInstallationState() != 'COMPLETE') {
    section() {
      paragraph('Please click <b>Done</b> to install the parent app.')
    }
  }
}
