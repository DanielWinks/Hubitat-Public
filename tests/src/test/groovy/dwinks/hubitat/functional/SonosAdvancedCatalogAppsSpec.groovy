package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Specification
import spock.lang.Unroll

class SonosAdvancedCatalogAppsSpec extends Specification {

  @Unroll
  def '#plural child app stores the latest snapshot and renders cards from it'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(new File(file))

    when:
    app.storeCatalogSnapshot([
      [id: '9', name: 'Zed <b>', imageUrl: 'http://192.168.1.50:1400/getaa?s=1&u=x'],
      [id: '2', name: 'alpha', imageUrl: ''],
      [id: '', name: 'ignored', imageUrl: '']
    ], 'PLAYER-1')
    Map page = (Map)app.mainPage()
    String html = app.renderCatalogCards(app.state.catalogRecords as List<Map>, app.state.catalogUpdatedAt as Long)

    then:
    page.title == "Sonos ${plural}"
    app.state.catalogRecords*.id == ['2', '9']
    app.state.catalogSourceDni == 'PLAYER-1'
    html.indexOf('alpha') < html.indexOf('Zed')
    html.contains("${single} ID:")
    html.contains('class=\'sonos-catalog-card-header\'>alpha')
    html.contains('No artwork')
    html.contains('Zed &lt;b&gt;')
    !html.contains('Zed <b>')
    html.contains('src=\'http://192.168.1.50:1400/getaa?s=1&amp;u=x\'')

    when:
    app.storeCatalogSnapshot([[id: '5', name: 'Only', imageUrl: 'javascript:alert(1)']])
    String replaced = app.renderCatalogCards(app.state.catalogRecords as List<Map>, null)

    then:
    app.state.catalogRecords*.id == ['5']
    !replaced.contains('javascript:')
    replaced.contains('No artwork')

    where:
    plural      | single     | file
    'Favorites' | 'Favorite' | '../Apps/SonosAdvancedFavoritesApp.groovy'
    'Playlists' | 'Playlist' | '../Apps/SonosAdvancedPlaylistsApp.groovy'
  }

  @Unroll
  def '#plural page distinguishes an initial load from a completed empty catalog'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(new File(file))

    when:
    String initial = app.renderCatalogCards([], null)
    app.storeCatalogSnapshot([], 'PLAYER-1')
    String completed = app.renderCatalogCards(app.state.catalogRecords as List<Map>, app.state.catalogUpdatedAt as Long)

    then:
    initial.contains('sonos-catalog-empty')
    initial.contains('have been loaded yet')
    completed.contains('sonos-catalog-empty')
    completed.contains("No ${plural.toLowerCase()} found in your Sonos library.")
    !completed.contains('have been loaded yet')

    where:
    plural      | file
    'Favorites' | '../Apps/SonosAdvancedFavoritesApp.groovy'
    'Playlists' | '../Apps/SonosAdvancedPlaylistsApp.groovy'
  }
}
