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

import groovy.transform.CompileStatic
import groovy.transform.Field

library(
  name: 'SunPosition',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Sun Position Library',
  importUrl: ''
)

@Field static final double RAD = 0.017453292519943
@Field static final double E = 0.409099940679715

@CompileStatic
BigDecimal toJulian() { return nowDays() + 2440587.5}

@CompileStatic
BigDecimal toDays() { return toJulian() - 2451545 }

@CompileStatic
BigDecimal rightAscension(double l, double b) { return Math.atan2(Math.sin(l) * Math.cos(E) - Math.tan(b) * Math.sin(E), Math.cos(l)) }

@CompileStatic
BigDecimal declination(double l, double b) { return Math.asin(Math.sin(b) * Math.cos(E) + Math.cos(b) * Math.sin(E) * Math.sin(l)) }

@CompileStatic
BigDecimal azimuth(double H, double phi, double dec)  { return Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi)) }

@CompileStatic
BigDecimal altitude(double H, double phi, double dec) { return Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H)) }

@CompileStatic
BigDecimal siderealTime(double d, double lw) { return RAD * (280.16 + 360.9856235 * d) - lw }


@CompileStatic
BigDecimal solarMeanAnomaly(double d) { return RAD * (357.5291 + 0.98560028 * d) }

@CompileStatic
BigDecimal eclipticLongitude(double M) {
  double C = RAD * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M))
  double P = RAD * 102.9372
  return M + C + P + Math.PI
}

@CompileStatic
Map<String, BigDecimal> sunCoords(double d) {
  double M = solarMeanAnomaly(d)
  double L = eclipticLongitude(M)
  return [dec: declination(L, 0), ra: rightAscension(L, 0)]
}

@CompileStatic
Map<String, BigDecimal> getPosition(BigDecimal lat, BigDecimal lng) {
  double lw  = RAD * -lng
  double phi = RAD * lat
  double d   = toDays()
  Map<String, BigDecimal> c  = sunCoords(d)
  double H  = siderealTime(d, lw) - c.ra

  double az = azimuth(H, phi, c.dec as double)
  az = (az * 180 / Math.PI) + 180

  double al = altitude(H, phi, c.dec as double)
  al = al * 180 / Math.PI

  return [
      azimuth: new BigDecimal(az),
      altitude: new BigDecimal(al),
  ]
}