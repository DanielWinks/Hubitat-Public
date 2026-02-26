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

#include dwinks.UtilitiesAndLoggingLibrary

metadata {
  definition(
    name: 'Sonos Advanced Playlist',
    version: '0.10.11',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvPlaylist.groovy'
  ) {
    capability 'Actuator'
    command 'getPlaylists'
    command 'loadPlaylistFull', [
      [ name: 'playlistId', type: 'STRING'],
      [ name: 'repeatMode', type: 'ENUM', constraints: [ 'repeat all', 'repeat one', 'off' ]],
      [ name: 'queueMode', type: 'ENUM', constraints: [ 'replace', 'append', 'insert', 'insert_next' ]],
      [ name: 'shuffleMode', type: 'ENUM', constraints: ['off', 'on']],
      [ name: 'autoPlay', type: 'ENUM', constraints: [ 'true', 'false' ]],
      [ name: 'crossfadeMode', type: 'ENUM', constraints: ['on', 'off']]
    ]
    command 'loadPlaylist', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistAndAppend', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistNext', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistAndShuffle', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistQueued', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistRepeatOne', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistNoRepeat', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistNoShuffle', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistNoCrossfade', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistAppendAndPlay', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistShuffleNoRepeat', [[ name: 'playlistId', type: 'STRING']]
  }
}

void initialize() {configure()}
void configure() {getPlaylists()}
void getPlaylists() {parent?.getPlaylists()}
void setPlaylists(String playlists) {state.playlists = playlists}

// Load Playlist Commands - Full Control
void loadPlaylist(String playlistId) {
  parent?.loadPlaylist(playlistId)
}

void loadPlaylistFull(String playlistId) {
  parent?.loadPlaylistFull(playlistId)
}

void loadPlaylistFull(String playlistId, String repeatMode) {
  parent?.loadPlaylistFull(playlistId, repeatMode)
}

void loadPlaylistFull(String playlistId, String repeatMode, String queueMode) {
  parent?.loadPlaylistFull(playlistId, repeatMode, queueMode)
}

void loadPlaylistFull(String playlistId, String repeatMode, String queueMode, String shuffleMode) {
  parent?.loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode)
}

void loadPlaylistFull(String playlistId, String repeatMode, String queueMode, String shuffleMode, String autoPlay) {
  parent?.loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode, autoPlay)
}

void loadPlaylistFull(String playlistId, String repeatMode, String queueMode, String shuffleMode, String autoPlay, String crossfadeMode) {
  parent?.loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

// Load Playlist Shortcuts - Preconfigured Options
void loadPlaylistAndAppend(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'repeat all', 'append', 'off', 'false', 'on')
}

void loadPlaylistNext(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'repeat all', 'insert_next', 'off', 'false', 'on')
}

void loadPlaylistAndShuffle(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'repeat all', 'replace', 'on', 'true', 'on')
}

void loadPlaylistQueued(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'repeat all', 'replace', 'off', 'false', 'on')
}

void loadPlaylistRepeatOne(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'repeat one', 'replace', 'off', 'true', 'on')
}

void loadPlaylistNoRepeat(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'off', 'replace', 'off', 'true', 'on')
}

void loadPlaylistNoShuffle(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'repeat all', 'replace', 'off', 'true', 'on')
}

void loadPlaylistNoCrossfade(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'off', 'replace', 'off', 'true', 'off')
}

void loadPlaylistAppendAndPlay(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'repeat all', 'append', 'off', 'true', 'on')
}

void loadPlaylistShuffleNoRepeat(String playlistId) {
  parent?.loadPlaylistFull(playlistId, 'off', 'replace', 'on', 'true', 'on')
}
