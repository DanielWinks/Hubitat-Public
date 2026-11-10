/**
 *  MIT License
 *  Copyright 2026 Daniel Winks
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

import groovy.transform.Field

metadata {
  definition(
    name: 'Sonos Advanced Alarm Manager',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    singleThreaded: true,
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvAlarmManager.groovy'
  ) {
    capability 'Refresh'
    capability 'Actuator'

    command 'createAlarm', [
      [name: 'Start Time (HH:mm:ss)', type: 'STRING'],
      [name: 'Duration (HH:mm:ss)', type: 'STRING'],
      [name: 'Recurrence', type: 'ENUM', constraints: ['ONCE', 'DAILY', 'WEEKDAYS', 'WEEKENDS']],
      [name: 'Room UUID (RINCON)', type: 'STRING'],
      [name: 'Program URI', type: 'STRING'],
      [name: 'Program Metadata', type: 'STRING'],
      [name: 'Play Mode', type: 'ENUM', constraints: ['NORMAL', 'REPEAT_ALL', 'SHUFFLE_NOREPEAT', 'SHUFFLE']],
      [name: 'Volume (0-100)', type: 'NUMBER'],
      [name: 'Include rooms currently grouped with this room?', description: 'When enabled, the alarm also plays in other rooms grouped with the alarm room when it sounds.', type: 'ENUM', constraints: ['true', 'false']],
      [name: 'Enabled', type: 'ENUM', constraints: ['true', 'false']]
    ]
    command 'createAlarmFromFavorite', [
      [name: 'Favorite ID', type: 'STRING'],
      [name: 'Start Time (HH:mm:ss)', type: 'STRING'],
      [name: 'Duration (HH:mm:ss)', type: 'STRING'],
      [name: 'Recurrence', type: 'ENUM', constraints: ['ONCE', 'DAILY', 'WEEKDAYS', 'WEEKENDS']],
      [name: 'Room UUID (RINCON)', type: 'STRING'],
      [name: 'Play Mode', type: 'ENUM', constraints: ['NORMAL', 'REPEAT_ALL', 'SHUFFLE_NOREPEAT', 'SHUFFLE']],
      [name: 'Volume (0-100)', type: 'NUMBER'],
      [name: 'Include rooms currently grouped with this room?', description: 'When enabled, the alarm also plays in other rooms grouped with the alarm room when it sounds.', type: 'ENUM', constraints: ['true', 'false']],
      [name: 'Enabled', type: 'ENUM', constraints: ['true', 'false']]
    ]
    command 'createAlarmFromPlaylist', [
      [name: 'Playlist ID', type: 'STRING'],
      [name: 'Start Time (HH:mm:ss)', type: 'STRING'],
      [name: 'Duration (HH:mm:ss)', type: 'STRING'],
      [name: 'Recurrence', type: 'ENUM', constraints: ['ONCE', 'DAILY', 'WEEKDAYS', 'WEEKENDS']],
      [name: 'Room UUID (RINCON)', type: 'STRING'],
      [name: 'Play Mode', type: 'ENUM', constraints: ['NORMAL', 'REPEAT_ALL', 'SHUFFLE_NOREPEAT', 'SHUFFLE']],
      [name: 'Volume (0-100)', type: 'NUMBER'],
      [name: 'Include rooms currently grouped with this room?', description: 'When enabled, the alarm also plays in other rooms grouped with the alarm room when it sounds.', type: 'ENUM', constraints: ['true', 'false']],
      [name: 'Enabled', type: 'ENUM', constraints: ['true', 'false']]
    ]
    command 'destroyAlarm', [
      [name: 'Alarm ID', type: 'STRING'],
      [name: 'Room UUID (RINCON)', type: 'STRING']
    ]
  }
}

void refresh() { parent?.refreshSonosAlarmsForHousehold(device.getDataValue('householdId')?.toString()) }
void createAlarm(String startTime, String duration, String recurrence, String roomUuid,
    String programUri, String programMetadata, String playMode, BigDecimal volume,
    String includeGroupedRooms, String enabled) {
  Integer alarmVolume = volume?.intValue()
  if(alarmVolume == null || alarmVolume < 0 || alarmVolume > 100) { return }
  if(!roomUuid || !programUri) { return }
  parent?.createSonosAlarm(device.getDataValue('householdId')?.toString(), [
    StartLocalTime: startTime, Duration: duration, Recurrence: recurrence,
    Enabled: enabled == 'true' ? '1' : '0', RoomUUID: roomUuid,
    ProgramURI: programUri, ProgramMetaData: programMetadata ?: '',
    PlayMode: playMode, Volume: alarmVolume.toString(),
    IncludeLinkedZones: includeGroupedRooms == 'true' ? '1' : '0'
  ])
}
void createAlarmFromFavorite(String favoriteId, String startTime, String duration, String recurrence,
    String roomUuid, String playMode, BigDecimal volume, String includeGroupedRooms, String enabled) {
  createAlarmFromCatalogSource('favorite', favoriteId, startTime, duration, recurrence, roomUuid, playMode, volume, includeGroupedRooms, enabled)
}
void createAlarmFromPlaylist(String playlistId, String startTime, String duration, String recurrence,
    String roomUuid, String playMode, BigDecimal volume, String includeGroupedRooms, String enabled) {
  createAlarmFromCatalogSource('playlist', playlistId, startTime, duration, recurrence, roomUuid, playMode, volume, includeGroupedRooms, enabled)
}
private void createAlarmFromCatalogSource(String sourceType, String sourceId, String startTime, String duration,
    String recurrence, String roomUuid, String playMode, BigDecimal volume, String includeGroupedRooms, String enabled) {
  Integer alarmVolume = volume?.intValue()
  if(!sourceId || !roomUuid || alarmVolume == null || alarmVolume < 0 || alarmVolume > 100) { return }
  parent?.createSonosAlarmFromCatalogSource(device.getDataValue('householdId')?.toString(), sourceType, sourceId, [
    StartLocalTime: startTime, Duration: duration, Recurrence: recurrence,
    Enabled: enabled == 'true' ? '1' : '0', RoomUUID: roomUuid,
    PlayMode: playMode, Volume: alarmVolume.toString(),
    IncludeLinkedZones: includeGroupedRooms == 'true' ? '1' : '0'
  ])
}
void destroyAlarm(String alarmId, String roomUuid) {
  if(!alarmId || !roomUuid) { return }
  parent?.destroySonosAlarm(device.getDataValue('householdId')?.toString(), alarmId, roomUuid)
}

String getCurrentTTSVoice() {
  return cachedTTSDefaultVoice != null ? cachedTTSDefaultVoice : 'Matthew'
}

List<String> getCachedTTSVoiceNames() {
  if(cachedTTSVoiceNames != null && cachedTTSVoiceNames.size() > 0) { return cachedTTSVoiceNames }
  return ['Matthew']
}

void updateTTSVoiceCache(List<String> voiceNames, String defaultVoice) {
  cachedTTSVoiceNames = voiceNames
  cachedTTSDefaultVoice = defaultVoice
  logTrace("TTS voice cache updated: ${voiceNames?.size()} voices, default: ${defaultVoice}")
}

@Field static volatile List<String> cachedTTSVoiceNames = null
@Field static volatile String cachedTTSDefaultVoice = null
