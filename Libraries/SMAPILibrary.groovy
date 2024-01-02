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

library(
  name: 'SMAPILibrary',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Sonos Music API Library',
  importUrl: ''
)

Map AlarmClock1 = [:]

Map AVTransport = [
  service: 'AVTransport',
  serviceType: 'urn:schemas-upnp-org:service:AVTransport:1',
  serviceId: 'urn:upnp-org:serviceId:AVTransport',
  controlURL: '/MediaRenderer/AVTransport/Control',
  eventSubURL: '/MediaRenderer/AVTransport/Event',
  actions: [
    SetAVTransportURI: [
      arguments: [InstanceID: 0, CurrentURI: '', CurrentURIMetaData: '']
    ],
    SetNextAVTransportURI: [
      arguments: [InstanceID: 0, NextURI: '', NextURIMetaData: '']
    ],
    AddURIToQueue: [
      arguments: [InstanceID: 0, EnqueuedURI: '', EnqueuedURIMetaData: '', DesiredFirstTrackNumberEnqueued:0, EnqueueAsNext:false],
      outputs: [NumTracksAdded: '', NewQueueLength: '']
    ],
    AddMultipleURIsToQueue: [
      arguments: [InstanceID: 0, UpdateID: '', NumberOfURIs: '', EnqueuedURIs: '', EnqueuedURIsMetaData: '', ContainerURI: '', ContainerMetaData: '', DesiredFirstTrackNumberEnqueued:0, EnqueueAsNext:false],
      outputs: [NumTracksAdded: '', NewQueueLength: '', NewUpdateID: '']
    ],
    ReorderTracksInQueue: [
      arguments: [InstanceID: 0, StartingIndex: '', NumberOfTracks: '', InsertBefore: '', UpdateID: '']
    ],
    RemoveTrackFromQueue: [
      arguments: [InstanceID: 0, ObjectID: '', UpdateID: '']
    ],
    RemoveTrackRangeFromQueue: [
      arguments: [InstanceID: 0, ObjectID: '', UpdateID: ''],
      outputs: [NewUpdateID: '']
    ],
    RemoveAllTracksFromQueue: [
      arguments: [InstanceID: 0]
    ],
    SaveQueue: [
      arguments: [InstanceID: 0, Title: '', ObjectID: ''],
      outputs: [AssignedObjectID: '']
    ],
    BackupQueue: [
      arguments: [InstanceID: 0]
    ],
    CreateSavedQueue: [
      arguments: [InstanceID: 0, Title: '', EnqueuedURI: '', EnqueuedURIMetaData: ''],
      outputs: [NumTracksAdded: '', NewQueueLength: '', AssignedObjectID: '', NewUpdateID: '']
    ],
    AddURIToSavedQueue: [
      arguments: [InstanceID: 0, ObjectID: '', UpdateID: '', EnqueuedURI: '', EnqueuedURIMetaData: '', AddAtIndex: ''],
      outputs: [NumTracksAdded: '', NewQueueLength: '', NewUpdateID: '']
    ],
    ReorderTracksInSavedQueue: [
      arguments: [InstanceID: 0, ObjectID: '', UpdateID: '', TrackList: '', NewPositionList: ''],
      outputs: [QueueLengthChange: '', NewQueueLength: '', NewUpdateID: '']
    ],
    GetMediaInfo: [
      arguments: [InstanceID: 0],
      outputs: [NrTracks: '', MediaDuration: '', CurrentURI: '', CurrentURIMetaData: '', NextURI: '', NextURIMetaData: '', PlayMedium: '', RecordMedium: '', WriteStatus: '']
    ],
    GetTransportInfo: [
      arguments: [InstanceID: 0],
      outputs: [CurrentTransportState: '', CurrentTransportStatus: '', CurrentSpeed: '']
    ],
    GetPositionInfo: [
      arguments: [InstanceID: 0],
      outputs: [Track: '', TrackDuration: '', TrackMetaData: '', TrackURI: '', RelTime: '', AbsTime: '', RelCount: '', AbsCount: '']
    ],
    GetDeviceCapabilities: [
      arguments: [InstanceID: 0],
      outputs: [PlayMedia: '', RecMedia: '', RecQualityModes: '']
    ],
    GetTransportSettings: [
      arguments: [InstanceID: 0],
      outputs: [PlayMode: '', RecQualityMode: '', CurrentSpeed: '']
    ],
    GetCrossfadeMode: [
      arguments: [InstanceID: 0],
      outputs: [CrossfadeMode: '']
    ],
    Stop: [
      arguments: [InstanceID: 0]
    ],
    Play: [
      arguments: [InstanceID: 0, Speed: 1]
    ],
    Pause: [
      arguments: [InstanceID: 0]
    ],
    Seek: [
      arguments: [InstanceID: 0, Unit: '', Target: '']
    ],
    Next: [
      arguments: [InstanceID: 0]
    ],
    Previous: [
      arguments: [InstanceID: 0]
    ],
    SetPlayMode: [
      arguments: [InstanceID: 0, NewPlayMode: '']
    ],
    SetCrossfadeMode: [
      arguments: [InstanceID: 0, CrossfadeMode: '']
    ],
    GetCurrentTransportActions: [
      arguments: [InstanceID: 0],
      outputs: [Actions: '']
    ],
    BecomeCoordinatorOfStandaloneGroup: [
      arguments: [InstanceID: 0],
      outputs: [DelegatedGroupCoordinatorID: '', NewGroupID: '']
    ],
    DelegateGroupCoordinationTo: [
      arguments: [InstanceID: 0, NewCoordinator: '', RejoinGroup: ''] // RejoinGroup: BOOL
    ],
    BecomeGroupCoordinator: [
      arguments: [
        InstanceID: 0,
        CurrentCoordinator: '',
        CurrentGroupID: '',
        OtherMembers: '',
        TransportSettings: '',
        CurrentURI: '',
        CurrentURIMetaData: '',
        SleepTimerState: '',
        AlarmState: '',
        StreamRestartState: '',
        CurrentQueueTrackList: '',
        CurrentVLIState: ''
      ]
    ],
    BecomeGroupCoordinatorAndSource: [
      arguments: [
        InstanceID: 0,
        CurrentCoordinator: '',
        CurrentGroupID: '',
        OtherMembers: '',
        TransportSettings: '',
        CurrentURI: '',
        CurrentURIMetaData: '',
        SleepTimerState: '',
        AlarmState: '',
        StreamRestartState: '',
        CurrentAVTTrackList: '',
        CurrentQueueTrackList: '',
        CurrentSourceState: '',
        ResumePlayback: ''
      ]
    ],
    ChangeCoordinator: [
      arguments: [InstanceID: 0, CurrentCoordinator: '', NewCoordinator: '', NewTransportSettings: '', CurrentAVTransportURI: '']
    ],
    ChangeTransportSettings: [
      arguments: [InstanceID: 0, NewTransportSettings: '', CurrentAVTransportURI: '']
    ],
    ConfigureSleepTimer: [
      arguments: [InstanceID: 0, NewSleepTimerDuration: '']
    ],
    GetRemainingSleepTimerDuration: [
      arguments: [InstanceID: 0],
      outputs: [RemainingSleepTimerDuration: '', CurrentSleepTimerGeneration: '']
    ],
    RunAlarm: [
      arguments: [InstanceID: 0, AlarmID: '', LoggedStartTime: '', Duration: '', ProgramURI: '', ProgramMetaData: '', PlayMode: '', Volume: '', IncludeLinkedZones: '']
    ],
    StartAutoplay: [
      arguments: [InstanceID: 0, ProgramURI: '', ProgramMetaData: '', Volume: '', IncludeLinkedZones: '', ResetVolumeAfter: '']
    ],
    GetRunningAlarmProperties: [
      arguments: [InstanceID: 0, AlarmID: ''],
      outputs: [GroupID: '', LoggedStartTime: '']
    ],
    SnoozeAlarm: [
      arguments: [InstanceID: 0, Duration: '']
    ],
    EndDirectControlSession: [
      arguments: [InstanceID: 0]
    ]
  ],
  stateVariables: [
    TransportState: ['STOPPED', 'PLAYING', 'PAUSED_PLAYBACK', 'TRANSITIONING'],
    CurrentPlayMode: ['NORMAL', 'REPEAT_ALL', 'REPEAT_ONE', 'SHUFFLE_NOREPEAT', 'SHUFFLE', 'SHUFFLE_REPEAT_ONE'],
    NewPlayMode: ['NORMAL', 'REPEAT_ALL', 'REPEAT_ONE', 'SHUFFLE_NOREPEAT', 'SHUFFLE', 'SHUFFLE_REPEAT_ONE'],
    CurrentCrossfadeMode: [true, false],
    A_ARG_TYPE_SeekMode: [ allowedValueList: [ TRACK_NR: '', REL_TIME: '', TIME_DELTA: '' ]] // <- Seek "Unit": Position in queue (1 indexed), hh:mm:ss for REL_TIME, +/-hh:mm:ss for TIME_DELTA
  ]
]

Map ContentDirectory = [
  service: 'ContentDirectory',
  serviceType: 'urn:schemas-upnp-org:service:ContentDirectory:1',
  serviceId: 'urn:upnp-org:serviceId:ContentDirectory',
  controlURL: '/MediaServer/ContentDirectory/Control',
  eventSubURL: '/MediaServer/ContentDirectory/Event',
  actions: [
    Browse: [ arguments: [
        ObjectID: '',
        BrowseFlag: "BrowseDirectChildren",
        Filter: '*',
        StartingIndex: 0,
        RequestedCount: 100,
        SortCriteria: ''
      ]
    ]
  ]
]

Map RenderingControl = [
  service: 'RenderingControl',
  serviceType: 'urn:schemas-upnp-org:service:RenderingControl:1',
  controlURL: '/MediaRenderer/RenderingControl/Control',
  eventSubURL: '/MediaRenderer/RenderingControl/Event',
  actions: [
    GetVolume: [arguments: [InstanceID: 0, Channel: "Master"]],
    GetMute: [arguments: [InstanceID: 0, Channel: "Master"]],
    SetVolume: [arguments: [InstanceID: 0, Channel: "Master", DesiredVolume: 50]],
    SetMute: [arguments: [InstanceID: 0, Channel: "Master", DesiredMute: true]],
  ]
]

Map GroupRenderingControl = [
  service: 'GroupRenderingControl',
  serviceType: 'urn:schemas-upnp-org:service:GroupRenderingControl:1',
  controlURL: '/MediaRenderer/GroupRenderingControl/Control',
  eventSubURL: '/MediaRenderer/GroupRenderingControl/Event',
  actions: [
      GetGroupVolume: [arguments: [InstanceID: 0]],
      GetGroupMute: [arguments: [InstanceID: 0]],
      SetGroupVolume: [arguments: [InstanceID: 0, DesiredVolume: 50]],
      SetGroupMute: [arguments: [InstanceID: 0, DesiredMute: true]],
  ]
]

Map ZoneGroupTopology = [
  service: 'ZoneGroupTopology',
  serviceType: 'urn:schemas-upnp-org:service:ZoneGroupTopology:1',
  controlURL: '/ZoneGroupTopology/Control',
  eventSubURL: '/ZoneGroupTopology/Event',
  actions: [
      GetZoneGroupState: [],
      GetZoneGroupAttributes: []
  ]
]



Map Queue = [
  service: 'Queue',
  serviceType: 'urn:schemas-sonos-com:service:Queue:1',
  controlURL: '/MediaRenderer/Queue/Control',
  eventSubURL: '/MediaRenderer/Queue/Event',
  actions: [ ]
]

void sonosEventSubscribe(String eventSubURL, String host, Integer timeout, String dni) {
  logDebug("Subscribing to ${eventSubURL} for ${host} with timeout of ${timeout} using DNI of ${dni}")
  String callback = "${getLocation().getHub().localIP}:${getLocation().getHub().localSrvPortTCP}" //Ex: http://192.168.1.4:39501/notify
  if (host && eventSubURL && timeout > 0 && dni) {
    sendHubCommand(new hubitat.device.HubAction([
      method: 'SUBSCRIBE',
      path: eventSubURL,
      headers: [
        HOST: host,
        CALLBACK: "<http://${callback}>",
        NT: 'upnp:event',
        TIMEOUT: "Second-${timeout}"
      ]
    ], dni))
  } else {
    throw new IllegalArgumentException('Must provide host, eventSubURL, timeout, and dni')
  }
}

void sonosEventRenew(String eventSubURL, String host, Integer timeout, String dni, String subscriptionId) {
  logDebug("Resubscribing to ${eventSubURL} ${host} ${timeout} ${subscriptionId}")
  if (host && eventSubURL && subscriptionId && dni) {
    sendHubCommand(new hubitat.device.HubAction([
      method: 'SUBSCRIBE',
      path: eventSubURL,
      headers: [
        HOST: host,
        SID: "${subscriptionId}",
        TIMEOUT: "Second-${timeout}"
      ]
    ], dni))
  } else {
    throw new IllegalArgumentException('Must provide host, eventSubURL, subscriptionId, and dni')
  }
}

void sonosEventUnsubscribe(String eventSubURL, String host, String dni, String subscriptionId) {
  logDebug("Unsubscribing from ${eventSubURL} for ${host} for subId of ${subscriptionId} using DNI of ${dni}")
  if (host && eventSubURL && subscriptionId && dni) {
    sendHubCommand(new hubitat.device.HubAction([
      method: 'UNSUBSCRIBE',
      path: eventSubURL,
      headers: [
        HOST: host,
        SID: "${subscriptionId}"
      ]
    ], dni))
  } else {
    throw new IllegalArgumentException('Must provide host, eventSubURL, subscriptionId, and dni')
  }
}

GPathResult parseSonosMessageXML(Map message) {
  String body = message.body.replace('&quot;','"').replace('&apos;',"'").replace('&lt;','<').replace('&gt;','>').replace('&amp;','&')
  GPathResult propertyset = parseXML(body)
  return propertyset
}