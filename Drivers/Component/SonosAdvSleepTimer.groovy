/**
 * MIT License
 * Copyright 2026 Daniel Winks (daniel.winks@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */

metadata {
  definition(
    name: 'Sonos Advanced Sleep Timer',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvSleepTimer.groovy'
  ) {
    capability 'Actuator'
    capability 'Refresh'
    command 'setSleepTimer', [[name: 'Duration (hh:mm:ss)', type: 'STRING']]
    command 'cancelSleepTimer'
    attribute 'remainingDuration', 'string'
    attribute 'sleepTimerState', 'enum', ['active', 'inactive', 'unknown']
    attribute 'generation', 'string'
  }
}

void initialize() { refresh() }
void configure() { refresh() }
void refresh() { parent?.getSleepTimer() }
void setSleepTimer(String duration) { parent?.setSleepTimer(duration) }
void cancelSleepTimer() { parent?.cancelSleepTimer() }
