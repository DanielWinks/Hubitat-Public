package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

/**
 * Regression specs for Morning Announcement's date handling.
 *
 * Reported bug: the announcement greeted "Happy Saturday" on a Sunday (run at
 * 4 AM Eastern). Root cause was NOT the user's suspected timezone-to-UTC drift
 * (that can only ever push a US hub's date a day AHEAD, never back) - it was
 * that the greeting's weekday was INFERRED by the LLM rather than computed in
 * code. The fix computes the day in code, in the hub timezone, and feeds it to
 * the greeting writer as ground truth.
 *
 * These specs lock in the deterministic, HTTP-free half of that fix:
 * buildTodaySpoken() and buildDateAnchor() must compute the day in the hub's
 * configured timezone (America/New_York in the harness) and always include the
 * weekday. The LLM-prompt half is not unit-testable and is verified on-hub.
 */
class MorningAnnouncementSpec extends Specification {

  @Shared HubitatScriptHarness app

  private static final List<String> WEEKDAYS =
    ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

  def setupSpec() {
    File appFile = new File('../Apps/MorningAnnouncement.groovy')
    assert appFile.exists(), "Could not find ${appFile.absolutePath}"
    app = ScriptLoader.load(appFile)
  }

  def "buildTodaySpoken computes today's weekday + date in the hub timezone"() {
    given: 'today formatted in the hub timezone right now'
    String expected = new Date().format('EEEE, MMMM d', app.location.timeZone)

    expect: 'the helper uses the hub timezone and leads with a weekday name'
    app.buildTodaySpoken() == expected
    WEEKDAYS.any { expected.startsWith(it) }
  }

  def "buildDateAnchor resolves TODAY in the hub timezone, not the JVM default (UTC)"() {
    given:
    String anchor = app.buildDateAnchor()
    String todayLine = "TODAY: ${new Date().format('EEEE, MMMM d, yyyy', app.location.timeZone)}"

    expect: 'the anchor TODAY line matches hub-local today and has the expected structure'
    anchor.contains(todayLine)
    anchor.contains('TOMORROW:')
    anchor.contains('next ')
  }

  def "the greeting day and the date-anchor TODAY always agree on the weekday"() {
    given: 'both derive the weekday from the same hub-tz clock'
    String spokenWeekday = app.buildTodaySpoken().split(',')[0]
    String anchor = app.buildDateAnchor()

    expect: 'so they can never disagree (the class of bug that produced "Happy Saturday")'
    anchor.contains("TODAY: ${spokenWeekday},")
  }
}
