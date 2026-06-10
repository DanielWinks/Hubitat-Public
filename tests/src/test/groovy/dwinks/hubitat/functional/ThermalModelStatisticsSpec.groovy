package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

/**
 * Logical-correctness specs for the Solar Shade Thermal Model child driver - the
 * online regression that learns the home's natural (HVAC-off) temperature response
 * vs. (outdoor - indoor) and most-exposed-wall solar load. We test the pure solver
 * and that accumulate() + fit() recover known coefficients from exact data.
 */
class ThermalModelStatisticsSpec extends Specification {

  @Shared HubitatScriptHarness drv

  def setupSpec() {
    File f = new File('../Drivers/Component/ThermalModelStatistics.groovy')
    assert f.exists(), "Could not find ${f.absolutePath}"
    drv = ScriptLoader.load(f)
  }

  def setup() {
    // fresh accumulators per test (the driver stores running sums in state)
    drv.state.clear()
  }

  def "Gauss-Jordan solver solves a diagonal system and reports singular systems"() {
    expect:
    drv.solveLinearSystem([[2.0d, 0.0d], [0.0d, 4.0d]] as double[][], [2.0d, 8.0d] as double[]).toList() == [1.0d, 2.0d]
    drv.solveLinearSystem([[1.0d, 1.0d], [1.0d, 1.0d]] as double[][], [2.0d, 2.0d] as double[]) == null
  }

  def "fit returns not-ok until enough samples have accumulated"() {
    when:
    drv.accumulate('open', 10.0d, 50.0d, 0.3d)
    drv.accumulate('open', 5.0d, 80.0d, 0.2d)

    then:
    !drv.fit('open').ok    // only 2 samples (< 3)
  }

  def "accumulate + fit recover the natural-response coefficients from exact data"() {
    given: 'rate = 0.05 + 0.02*(Tout-Tin) + 0.001*solar over varied conditions'
    List<List<Double>> rows = [
      [10.0d, 50.0d], [5.0d, 80.0d], [-3.0d, 20.0d],
      [8.0d, 10.0d], [0.0d, 0.0d], [15.0d, 100.0d]
    ]

    when:
    rows.each { r ->
      double tempDiff = r[0]
      double solar = r[1]
      double y = 0.05d + 0.02d * tempDiff + 0.001d * solar
      drv.accumulate('open', tempDiff, solar, y)
    }
    Map f = drv.fit('open')

    then:
    f.ok
    Math.abs((f.b0 as double) - 0.05d) < 1e-6     // intercept
    Math.abs((f.b1 as double) - 0.02d) < 1e-6     // coupling (1/tau ~ 50 min)
    Math.abs((f.b2 as double) - 0.001d) < 1e-6    // solar gain
    Math.abs((f.r2 as double) - 1.0d) < 1e-6      // perfect fit on noise-free data
  }

  def "open and closed models accumulate independently"() {
    when:
    drv.accumulate('open', 10.0d, 50.0d, 0.5d)
    drv.accumulate('closed', 10.0d, 50.0d, 0.1d)

    then:
    (drv.sd('open_n') as double) == 1.0d
    (drv.sd('closed_n') as double) == 1.0d
    (drv.sd('open_t1') as double) == 0.5d
    (drv.sd('closed_t1') as double) == 0.1d
  }
}
