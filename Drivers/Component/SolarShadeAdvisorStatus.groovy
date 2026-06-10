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

metadata {
  definition(
    name: 'Solar Shade Advisor Status',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true
  ) {
    capability 'Sensor'

    attribute 'advisoryState', 'string'           // recommendation key, e.g. win:closed|shade:draw
    attribute 'advisoryMessage', 'string'
    attribute 'windowRecommendation', 'string'    // open | closed
    attribute 'shadeRecommendation', 'string'     // draw | open
    attribute 'shadesToDraw', 'string'            // comma-separated wall names, or 'none'
    attribute 'predictedIndoorHigh', 'number'
    attribute 'predictedIndoorLow', 'number'
    attribute 'outdoorDewPoint', 'number'
    attribute 'indoorDewPoint', 'number'
    attribute 'humidityVeto', 'string'            // active | inactive
    attribute 'modelTimeConstantMin', 'number'
    attribute 'modelSamples', 'number'
    attribute 'lastEvaluation', 'string'
  }
}

// This is a display-only child device. The parent app pushes attribute values
// via sendEvent(); the device itself takes no actions and has no commands.

void installed() {
  sendEvent(name: 'advisoryState', value: 'D')
  sendEvent(name: 'advisoryMessage', value: 'Awaiting first evaluation.')
  sendEvent(name: 'humidityVeto', value: 'inactive')
}

void updated() { }
