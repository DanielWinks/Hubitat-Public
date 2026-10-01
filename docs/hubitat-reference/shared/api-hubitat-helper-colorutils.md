# Color utils

- **ID:** `api-hubitat-helper-colorutils`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.ColorUtils`

> Use ColorUtils from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `hexToRGB` | `List hexToRGB(String hexRGB)` | Parse the first three hexadecimal color channels. Parameters: hexRGB - color text in #RRGGBB form Returns: list of red, green, and blue channel integers from 0 through 255; empty f |
| `hsv360ToRGB` | `List hsv360ToRGB(List hsv)` | Convert HSV values using a 0-to-360 hue scale to RGB channels. Parameters: hsv - list of hue in degrees and saturation/value percentages Returns: list of red, green, and blue chann |
| `hsv360ToXY` | `List hsv360ToXY(List hsv)` | Convert HSV values using 0-to-360 hue scale to CIE xy chromaticity and brightness. Parameters: hsv - mutable list of hue in degrees and saturation/brightness percentages; this meth |
| `hsvToRGB` | `List hsvToRGB(List hsv)` | Convert HSV values using a 0-to-100 hue scale to RGB channels. Parameters: hsv - list of hue, saturation, and value, each scaled from 0 to 100 Returns: list of red, green, and blue |
| `hsvToXY` | `List hsvToXY(List hsv)` | Convert HSV values using 0-to-100 hue scale to CIE xy chromaticity and brightness. Parameters: hsv - mutable list of hue, saturation, and brightness values from 0 through 100; this |
| `rgbToHEX` | `String rgbToHEX(List rgb)` | Format three RGB channel values as a hexadecimal color string. Parameters: rgb - list of exactly three channel values in red, green, blue order Returns: #RRGGBB text, or an empty S |
| `rgbToHSV` | `List rgbToHSV(List rgb)` | Convert RGB channels to HSV with hue and saturation scaled from 0 to 100. Parameters: rgb - list of exactly three red, green, blue channel values from 0 through 255 Returns: list o |
| `rgbToHSV360` | `List rgbToHSV360(List rgb)` | Convert RGB channels to HSV with hue scaled from 0 to 360. Parameters: rgb - list of exactly three red, green, blue channel values from 0 through 255 Returns: list of hue in degree |
| `rgbToXY` | `List rgbToXY(List rgb)` | Convert RGB channels to CIE xy chromaticity and brightness. Parameters: rgb - list of exactly three red, green, blue channel values from 0 through 255 Returns: list containing x an |
