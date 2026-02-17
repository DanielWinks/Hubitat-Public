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
    name: 'Sonos Advanced Favorites',
    version: '0.10.2',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvFavorites.groovy'
  ) {
    capability 'Actuator'
    command 'getFavorites'
    command 'loadFavoriteFull', [
      [ name: 'favoriteId', type: 'STRING'],
      [ name: 'repeatMode', type: 'ENUM', constraints: [ 'repeat all', 'repeat one', 'off' ]],
      [ name: 'queueMode', type: 'ENUM', constraints: [ 'replace', 'append', 'insert', 'insert_next' ]],
      [ name: 'shuffleMode', type: 'ENUM', constraints: ['off', 'on']],
      [ name: 'autoPlay', type: 'ENUM', constraints: [ 'true', 'false' ]],
      [ name: 'crossfadeMode', type: 'ENUM', constraints: ['on', 'off']]
    ]
    command 'loadFavorite', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteAndAppend', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteNext', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteAndShuffle', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteQueued', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteRepeatOne', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteNoRepeat', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteNoShuffle', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteNoCrossfade', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteAppendAndPlay', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteShuffleNoRepeat', [[ name: 'favoriteId', type: 'STRING']]
  }
}

void initialize() {configure()}
void configure() {getFavorites()}
void getFavorites() {parent?.getFavorites()}
void setFavorites(String favorites) {state.favorites = favorites}

// Load Favorite Commands - Full Control
void loadFavorite(String favoriteId) {
  parent?.loadFavorite(favoriteId)
}

void loadFavoriteFull(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId)
}

void loadFavoriteFull(String favoriteId, String repeatMode) {
  parent?.loadFavoriteFull(favoriteId, repeatMode)
}

void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode) {
  parent?.loadFavoriteFull(favoriteId, repeatMode, queueMode)
}

void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode) {
  parent?.loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode)
}

void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode, String autoPlay) {
  parent?.loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay)
}

void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode, String autoPlay, String crossfadeMode) {
  parent?.loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

// Load Favorite Shortcuts - Preconfigured Options
void loadFavoriteAndAppend(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'repeat all', 'append', 'off', 'false', 'on')
}

void loadFavoriteNext(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'repeat all', 'insert_next', 'off', 'false', 'on')
}

void loadFavoriteAndShuffle(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'repeat all', 'replace', 'on', 'true', 'on')
}

void loadFavoriteQueued(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'repeat all', 'replace', 'off', 'false', 'on')
}

void loadFavoriteRepeatOne(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'repeat one', 'replace', 'off', 'true', 'on')
}

void loadFavoriteNoRepeat(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'off', 'replace', 'off', 'true', 'on')
}

void loadFavoriteNoShuffle(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'repeat all', 'replace', 'off', 'true', 'on')
}

void loadFavoriteNoCrossfade(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'off', 'replace', 'off', 'true', 'off')
}

void loadFavoriteAppendAndPlay(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'repeat all', 'append', 'off', 'true', 'on')
}

void loadFavoriteShuffleNoRepeat(String favoriteId) {
  parent?.loadFavoriteFull(favoriteId, 'off', 'replace', 'on', 'true', 'on')
}