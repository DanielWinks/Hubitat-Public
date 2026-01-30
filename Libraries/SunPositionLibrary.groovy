/**
 * =============================================================================
 * SUN POSITION LIBRARY FOR HUBITAT
 * =============================================================================
 *
 * WHAT THIS LIBRARY DOES (Simple Explanation):
 * This code calculates where the Sun is in the sky at any given moment for
 * a specific location on Earth. It tells you two things:
 * 1. AZIMUTH - Which compass direction to look to see the Sun (in degrees)
 *    - 0° = North, 90° = East, 180° = South, 270° = West
 * 2. ALTITUDE - How high above the horizon the Sun is (in degrees)
 *    - Positive numbers = Sun is above the horizon (daytime)
 *    - Negative numbers = Sun is below the horizon (nighttime)
 *    - 0° = Sun is exactly on the horizon (sunrise/sunset)
 *
 * WHY THIS IS USEFUL:
 * You can use this in your smart home to:
 * - Automatically close blinds when the Sun shines directly through a window
 * - Turn on lights when the Sun goes below a certain angle
 * - Track the Sun's position throughout the day
 * - Create automations based on Sun position rather than just time
 *
 * HOW TO USE THIS LIBRARY:
 * Call the main function with your location's latitude and longitude:
 *   Map position = getPosition(40.7128, -74.0060)  // Example: New York City
 *   // Result contains: position.azimuth and position.altitude
 *
 * EXAMPLE OUTPUT:
 *   [azimuth: 123.45, altitude: 45.67]
 *   This means: Look 123.45° clockwise from North, and look up 45.67°
 *
 * =============================================================================
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
 * =============================================================================
 */

// =============================================================================
// IMPORTS (Loading Other Code Libraries)
// =============================================================================
// These lines tell the program to load additional code libraries that provide
// useful features we'll use in this file.

// CompileStatic: Makes the code run faster by checking types at compile time
// Think of it like a spell-checker that catches mistakes before running
import groovy.transform.CompileStatic

// Field: Allows us to create variables that are shared across the entire library
// These are like global constants that any function can access
import groovy.transform.Field

// =============================================================================
// LIBRARY REGISTRATION
// =============================================================================
// This block registers this code as a reusable library in Hubitat.
// Think of it like putting a label on a toolbox so others know what's inside.
library(
  name: 'SunPosition',              // The name of this library
  namespace: 'dwinks',              // Author's identifier (prevents name conflicts)
  author: 'Daniel Winks',           // Who created this library
  description: 'Sun Position Library', // What this library does
  importUrl: ''                     // Where to download updates (empty for now)
)

// =============================================================================
// MATHEMATICAL CONSTANTS
// =============================================================================
// These are fixed numbers used in astronomical calculations. They never change.

// RAD: Conversion factor from degrees to radians
// WHAT ARE DEGREES vs RADIANS?
// - Degrees: The familiar way to measure angles (360° in a circle)
// - Radians: Another way to measure angles used in math (2π radians in a circle)
// - Most trigonometry functions (sin, cos, tan) require radians, not degrees
// - This number (approximately 0.0174533) converts degrees to radians
// - Formula: radians = degrees × (π / 180)
@Field static final double RAD = 0.017453292519943

// E: Earth's axial tilt (obliquity of the ecliptic) in radians
// WHAT IS EARTH'S AXIAL TILT?
// - Earth doesn't spin perfectly upright - it's tilted about 23.44 degrees
// - This tilt causes seasons (summer when tilted toward Sun, winter away)
// - In astronomy calculations, we need this tilt in radians (0.40910 radians)
// - This affects where the Sun appears to be in the sky throughout the year
@Field static final double E = 0.409099940679715

// =============================================================================
// TIME CONVERSION FUNCTIONS
// =============================================================================
// Astronomy calculations use a special time system called "Julian Date"
// which is just a count of days since a specific date in history.
// These functions convert from regular time to Julian time.

/**
 * Converts current time to Julian Date
 *
 * WHAT IS A JULIAN DATE?
 * - Astronomers use a continuous count of days to avoid calendar complexity
 * - Julian Date is the number of days since January 1, 4713 BC (a very long time ago!)
 * - This makes it easy to calculate time differences and positions
 * - Example: Today might be Julian Date 2459945.5
 *
 * HOW THIS FUNCTION WORKS:
 * - Calls nowDays() which gets current time in days since 1970 (Unix epoch)
 * - Adds 2440587.5 to convert Unix days to Julian Date
 * - The .5 accounts for the fact Julian Days start at noon, not midnight
 *
 * RETURNS: Current date and time as a Julian Date number
 */
@CompileStatic
BigDecimal toJulian() { return nowDays() + 2440587.5}

/**
 * Converts Julian Date to days since J2000
 *
 * WHAT IS J2000?
 * - J2000 is a standard reference point: January 1, 2000, at 12:00 noon
 * - Astronomers use "days since J2000" in many modern calculations
 * - Makes formulas simpler because it's a recent, well-defined reference
 *
 * HOW THIS FUNCTION WORKS:
 * - Takes the Julian Date (from toJulian() function)
 * - Subtracts 2451545 (the Julian Date of J2000)
 * - Result is the number of days since January 1, 2000
 *
 * RETURNS: Number of days since J2000 (can be positive or negative)
 */
@CompileStatic
BigDecimal toDays() { return toJulian() - 2451545 }

// =============================================================================
// CELESTIAL COORDINATE CONVERSION FUNCTIONS
// =============================================================================
// The Sun's position can be described in different coordinate systems.
// These functions convert between coordinate systems.

/**
 * Calculates Right Ascension (celestial longitude)
 *
 * WHAT IS RIGHT ASCENSION?
 * - Imagine the Earth surrounded by a huge sphere with stars painted on it
 * - Right Ascension is like longitude on this celestial sphere
 * - It's measured in hours, minutes, seconds (or degrees, or radians)
 * - It tells us the east-west position of the Sun on the sky
 *
 * PARAMETERS:
 * - l (ecliptic longitude): The Sun's position along its yearly path (in radians)
 * - b (ecliptic latitude): How far north/south from the ecliptic (usually 0 for Sun)
 *
 * HOW THIS WORKS:
 * - Uses trigonometry to convert ecliptic coordinates to equatorial coordinates
 * - Takes into account Earth's tilt (E) because the coordinate systems are tilted
 * - atan2 is a math function that finds an angle from x,y coordinates
 *
 * RETURNS: Right Ascension in radians
 */
@CompileStatic
BigDecimal rightAscension(double l, double b) { return Math.atan2(Math.sin(l) * Math.cos(E) - Math.tan(b) * Math.sin(E), Math.cos(l)) }

/**
 * Calculates Declination (celestial latitude)
 *
 * WHAT IS DECLINATION?
 * - Declination is like latitude on the celestial sphere
 * - It measures how far north or south the Sun is from the celestial equator
 * - Ranges from +90° (directly above North Pole) to -90° (above South Pole)
 * - Changes throughout the year due to Earth's tilted axis (causes seasons!)
 * - At spring/fall equinox: declination ≈ 0° (Sun over equator)
 * - At summer solstice: declination ≈ +23.4° (Sun farthest north)
 * - At winter solstice: declination ≈ -23.4° (Sun farthest south)
 *
 * PARAMETERS:
 * - l (ecliptic longitude): The Sun's position along its yearly path
 * - b (ecliptic latitude): How far north/south from the ecliptic (usually 0 for Sun)
 *
 * HOW THIS WORKS:
 * - Uses trigonometry to project the Sun's ecliptic position onto the equatorial plane
 * - Accounts for Earth's tilt (E) which is why we have seasons
 * - asin is the "arc sine" function that finds an angle
 *
 * RETURNS: Declination in radians
 */
@CompileStatic
BigDecimal declination(double l, double b) { return Math.asin(Math.sin(b) * Math.cos(E) + Math.cos(b) * Math.sin(E) * Math.sin(l)) }

// =============================================================================
// LOCAL SKY POSITION FUNCTIONS
// =============================================================================
// These functions calculate where the Sun appears in YOUR local sky
// based on your location and the time.

/**
 * Calculates Azimuth (compass direction to the Sun)
 *
 * WHAT IS AZIMUTH?
 * - Azimuth is the compass direction you'd face to look at the Sun
 * - Measured in degrees clockwise from North
 * - 0° = North, 90° = East, 180° = South, 270° = West
 * - Example: If azimuth is 135°, the Sun is in the Southeast
 *
 * PARAMETERS:
 * - H (hour angle): How far the Sun has moved since crossing your meridian
 *                   (meridian = imaginary line from north to south through directly overhead)
 * - phi (latitude): Your latitude in radians (positive = north, negative = south)
 * - dec (declination): The Sun's declination (celestial latitude) in radians
 *
 * HOW THIS WORKS:
 * - Uses spherical trigonometry to convert celestial coordinates to local horizon coordinates
 * - Takes into account your latitude (phi) because the sky looks different at different latitudes
 * - Uses hour angle (H) which tracks Earth's rotation
 *
 * RETURNS: Azimuth in radians (will be converted to degrees later)
 */
@CompileStatic
BigDecimal azimuth(double H, double phi, double dec)  { return Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi)) }

/**
 * Calculates Altitude (angle above horizon)
 *
 * WHAT IS ALTITUDE?
 * - Altitude is how high above the horizon the Sun appears
 * - Measured in degrees from the horizon
 * - 0° = On the horizon (sunrise or sunset)
 * - 90° = Directly overhead (rare, only happens near equator)
 * - Positive = Above horizon (Sun is visible)
 * - Negative = Below horizon (Sun is not visible - nighttime)
 * - Example: 30° altitude means look up 30° from the horizon
 *
 * PARAMETERS:
 * - H (hour angle): How far the Sun has moved since crossing your meridian
 * - phi (latitude): Your latitude in radians
 * - dec (declination): The Sun's declination in radians
 *
 * HOW THIS WORKS:
 * - Uses spherical trigonometry to calculate the angle above the horizon
 * - Considers both your latitude (phi) and the Sun's declination (dec)
 * - At higher latitudes, the Sun never gets as high in winter
 * - asin function finds the altitude angle
 *
 * RETURNS: Altitude in radians (will be converted to degrees later)
 */
@CompileStatic
BigDecimal altitude(double H, double phi, double dec) { return Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H)) }

/**
 * Calculates Sidereal Time (star time)
 *
 * WHAT IS SIDEREAL TIME?
 * - Regular time (solar time) is based on the Sun's position
 * - Sidereal time is based on the stars' positions
 * - One sidereal day is slightly shorter than a solar day (23h 56m vs 24h)
 * - Why? Because Earth orbits the Sun while rotating
 * - Sidereal time tells us which stars/coordinates are directly overhead
 * - Essential for converting celestial coordinates to your local sky view
 *
 * PARAMETERS:
 * - d: Days since J2000 (from toDays() function)
 * - lw: Your longitude in radians (negative because formula convention)
 *
 * HOW THIS WORKS:
 * - Calculates Greenwich Sidereal Time (star time at longitude 0°)
 * - Formula: 280.16 + 360.9856235 × days (gives sidereal rotation)
 * - Adjusts for your longitude (lw) to get local sidereal time
 * - Converts result to radians using RAD constant
 *
 * RETURNS: Sidereal time in radians
 */
@CompileStatic
BigDecimal siderealTime(double d, double lw) { return RAD * (280.16 + 360.9856235 * d) - lw }

// =============================================================================
// SOLAR ORBIT CALCULATION FUNCTIONS
// =============================================================================
// These functions calculate where Earth is in its orbit around the Sun,
// which determines where the Sun appears to be from Earth's perspective.

/**
 * Calculates Solar Mean Anomaly
 *
 * WHAT IS MEAN ANOMALY?
 * - Describes where Earth is in its orbit around the Sun
 * - "Mean" means it's an average/simplified version (ignores orbit's slight oval shape)
 * - 0° = Earth at perihelion (closest to Sun, around January 3)
 * - 180° = Earth at aphelion (farthest from Sun, around July 4)
 * - Earth completes one orbit (360°) per year
 *
 * PARAMETERS:
 * - d: Days since J2000 (from toDays() function)
 *
 * HOW THIS WORKS:
 * - Formula: 357.5291 + 0.98560028 × days
 * - 357.5291 is the mean anomaly at J2000 (year 2000)
 * - 0.98560028 is degrees per day (≈360°/365.25 days)
 * - Converts result to radians using RAD constant
 *
 * RETURNS: Solar mean anomaly in radians
 */
@CompileStatic
BigDecimal solarMeanAnomaly(double d) { return RAD * (357.5291 + 0.98560028 * d) }

/**
 * Calculates Ecliptic Longitude (Sun's position on its yearly path)
 *
 * WHAT IS ECLIPTIC LONGITUDE?
 * - The ecliptic is the Sun's apparent yearly path across the sky
 * - Ecliptic longitude is the Sun's position along this path
 * - 0° = Spring equinox (March 20-21)
 * - 90° = Summer solstice (June 20-21)
 * - 180° = Fall equinox (Sept 22-23)
 * - 270° = Winter solstice (Dec 21-22)
 *
 * PARAMETERS:
 * - M: Solar mean anomaly (from solarMeanAnomaly() function) in radians
 *
 * HOW THIS WORKS:
 * - Starts with mean anomaly (M) which is a simplified orbit position
 * - Calculates "equation of center" (C) to correct for Earth's elliptical orbit
 *   - Earth's orbit isn't a perfect circle, so we need corrections
 *   - Uses three terms (sin M, sin 2M, sin 3M) for increasing accuracy
 *   - 1.9148 is the main correction, 0.02 and 0.0003 are smaller refinements
 * - Adds P (102.9372°) = perihelion position (direction to closest Sun approach)
 * - Adds π (180°) for reference frame adjustment
 * - Result: True ecliptic longitude of the Sun
 *
 * RETURNS: Ecliptic longitude in radians
 */
@CompileStatic
BigDecimal eclipticLongitude(double M) {
  // Calculate equation of center (correction for elliptical orbit)
  double C = RAD * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M))

  // Perihelion position (Earth's closest approach to Sun)
  double P = RAD * 102.9372

  // Combine everything: mean anomaly + correction + perihelion + reference adjustment
  return M + C + P + Math.PI
}

/**
 * Calculates Sun's Celestial Coordinates
 *
 * WHAT ARE CELESTIAL COORDINATES?
 * - Just like we use latitude/longitude on Earth, astronomers use coordinates in the sky
 * - Right Ascension (RA): Like longitude on the celestial sphere
 * - Declination (Dec): Like latitude on the celestial sphere
 * - These coordinates tell us where the Sun is positioned in space
 *
 * PARAMETERS:
 * - d: Days since J2000 (from toDays() function)
 *
 * HOW THIS WORKS:
 * - Step 1: Calculate solar mean anomaly (M) - Earth's position in orbit
 * - Step 2: Calculate ecliptic longitude (L) - Sun's position along ecliptic path
 * - Step 3: Convert ecliptic position to celestial coordinates
 *   - Calls declination(L, 0) to get celestial latitude
 *   - Calls rightAscension(L, 0) to get celestial longitude
 *   - The 0 means we assume Sun is exactly on the ecliptic plane (which it is)
 * - Returns both coordinates in a Map (like a dictionary with named values)
 *
 * RETURNS: Map containing:
 *   - dec: Declination (celestial latitude) in radians
 *   - ra: Right Ascension (celestial longitude) in radians
 */
@CompileStatic
Map<String, BigDecimal> sunCoords(double d) {
  // Calculate where Earth is in its orbit
  double M = solarMeanAnomaly(d)

  // Calculate Sun's position along the ecliptic
  double L = eclipticLongitude(M)

  // Convert to celestial coordinates and return both values
  return [dec: declination(L, 0), ra: rightAscension(L, 0)]
}

// =============================================================================
// MAIN PUBLIC FUNCTION
// =============================================================================
// This is the main function you call to get the Sun's position.
// All the other functions above are helpers that this function uses.

/**
 * Gets the Sun's current position in the sky for a given location
 *
 * THIS IS THE MAIN FUNCTION TO CALL!
 * This is the primary function you'll use from other code.
 * It combines all the helper functions above to calculate the Sun's position.
 *
 * PARAMETERS:
 * - lat: Your latitude in degrees
 *   - Positive numbers = Northern Hemisphere (e.g., 40.7128 for New York)
 *   - Negative numbers = Southern Hemisphere (e.g., -33.8688 for Sydney)
 *   - Range: -90 to +90
 * - lng: Your longitude in degrees
 *   - Positive numbers = East of Prime Meridian (e.g., 139.6917 for Tokyo)
 *   - Negative numbers = West of Prime Meridian (e.g., -74.0060 for New York)
 *   - Range: -180 to +180
 *
 * HOW TO USE:
 *   Map position = getPosition(40.7128, -74.0060)  // New York City
 *   log.debug "Azimuth: ${position.azimuth}°"
 *   log.debug "Altitude: ${position.altitude}°"
 *
 * HOW THIS WORKS (STEP BY STEP):
 * 1. Convert input coordinates from degrees to radians
 *    - lw: Longitude in radians (negative for math convention)
 *    - phi: Latitude in radians
 *
 * 2. Get current time as days since J2000
 *    - d: Current date/time in astronomical time units
 *
 * 3. Calculate Sun's celestial coordinates
 *    - c: Map containing Sun's Right Ascension (ra) and Declination (dec)
 *
 * 4. Calculate Hour Angle
 *    - H: How far the Sun has moved from your meridian due to Earth's rotation
 *    - Combines sidereal time and Sun's Right Ascension
 *
 * 5. Calculate Azimuth (compass direction)
 *    - az: Initial azimuth in radians
 *    - Convert radians to degrees: multiply by (180/π)
 *    - Add 180° to adjust reference (formula gives -180 to +180, we want 0 to 360)
 *
 * 6. Calculate Altitude (angle above horizon)
 *    - al: Altitude in radians
 *    - Convert radians to degrees: multiply by (180/π)
 *
 * 7. Return both values as BigDecimal numbers in a Map
 *
 * RETURNS: Map containing:
 *   - azimuth: Compass direction to Sun (0° = North, 90° = East, etc.)
 *   - altitude: Angle above horizon (positive = daytime, negative = nighttime)
 *
 * EXAMPLE OUTPUT:
 *   [azimuth: 156.73, altitude: 45.21]
 *   This means: Sun is at 156.73° (South-Southeast), 45.21° above horizon
 */
@CompileStatic
Map<String, BigDecimal> getPosition(BigDecimal lat, BigDecimal lng) {
  // Step 1: Convert input coordinates from degrees to radians
  // lw = longitude in radians (negative for formula convention)
  double lw  = RAD * -lng

  // phi = latitude in radians
  double phi = RAD * lat

  // Step 2: Get current time as days since year 2000
  double d   = toDays()

  // Step 3: Calculate Sun's celestial coordinates (where Sun is in space)
  Map<String, BigDecimal> c  = sunCoords(d)

  // Step 4: Calculate hour angle (accounts for Earth's rotation)
  // Hour angle = sidereal time - right ascension
  // This tells us how far the Sun has rotated across our local sky
  double H  = siderealTime(d, lw) - c.ra

  // Step 5: Calculate azimuth (compass direction to Sun)
  double az = azimuth(H, phi, c.dec as double)
  // Convert from radians to degrees and adjust to 0-360° range
  az = (az * 180 / Math.PI) + 180

  // Step 6: Calculate altitude (height above horizon)
  double al = altitude(H, phi, c.dec as double)
  // Convert from radians to degrees
  al = al * 180 / Math.PI

  // Step 7: Return both values as a Map
  // Using BigDecimal for precise decimal numbers
  return [
      azimuth: new BigDecimal(az),    // Compass bearing: 0° = North
      altitude: new BigDecimal(al),   // Height: positive = above horizon
  ]
}

// =============================================================================
// END OF LIBRARY
// =============================================================================
// That's it! To use this library:
// 1. Include it in your Hubitat driver or app
// 2. Call getPosition(yourLatitude, yourLongitude)
// 3. Use the returned azimuth and altitude values in your automations
//
// Common use cases:
// - if (position.altitude < 0) { /* Sun is down, turn on lights */ }
// - if (position.azimuth > 90 && position.azimuth < 270) { /* Sun is in south, close blinds */ }
// - if (position.altitude > 30) { /* Sun is high enough, no need for lights */ }
// =============================================================================
