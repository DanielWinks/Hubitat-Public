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
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */

metadata {
  definition(
    name: 'Sonos Advanced Queue',
    version: '0.14.2',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvQueue.groovy'
  ) {
    capability 'Actuator'
    command 'getQueue'
    command 'setQueue', [[name: 'Queue HTML', type: 'STRING']]
    command 'removeCurrentlyPlayingItemFromQueue'
    command 'playQueuePosition', [[name: 'Position', type: 'STRING']]
    command 'removeQueueItem', [[name: 'Queue item ID', type: 'STRING'], [name: 'Queue update ID', type: 'STRING']]
  }
}

void initialize() { getQueue() }
void configure() { getQueue() }
void getQueue() { parent?.getQueue() }
void setQueue(String html) { state.queue = html ?: '<p>The Sonos queue is empty.</p>' }
void removeCurrentlyPlayingItemFromQueue() { parent?.removeCurrentlyPlayingItemFromQueue() }
void playQueuePosition(String position) { parent?.playQueuePosition(position) }
void removeQueueItem(String objectId, String updateId) { parent?.removeQueueItem(objectId, updateId) }
