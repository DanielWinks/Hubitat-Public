# Hex utils

- **ID:** `api-hubitat-helper-hexutils`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.HexUtils`

> Use HexUtils from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `byteArrayToHexString` | `String byteArrayToHexString(byte[] value)` | Format each byte in an array as hexadecimal text. Parameters: value - bytes to format Returns: hexadecimal text from the shared converter |
| `byteToHexString` | `String byteToHexString(byte value)` | Format one byte as hexadecimal text. Parameters: value - byte to format Returns: two-character hexadecimal text from the shared converter |
| `hexStringToASCII` | `String hexStringToASCII(String value)` | Decode hexadecimal byte text as ASCII text. Parameters: value - hexadecimal text to decode Returns: decoded ASCII text from the shared converter |
| `hexStringToByteArray` | `byte[] hexStringToByteArray(String value)` | Parse pairs of hexadecimal characters as bytes. Parameters: value - hexadecimal text to parse Returns: parsed byte array from the shared converter |
| `hexStringToInt` | `int hexStringToInt(String value)` | Parse hexadecimal text as an integer. Parameters: value - hexadecimal text to parse Returns: parsed integer from the shared HexUtils2 converter |
| `hexStringToIntArray` | `int[] hexStringToIntArray(String value)` | Parse hexadecimal text into integer byte values. Parameters: value - hexadecimal text to parse Returns: integer array from the shared converter |
| `intArrayToHexString` | `String intArrayToHexString(int[] value)` | Format each integer in an array as hexadecimal text. Parameters: value - integers to format Returns: concatenated hexadecimal text from the shared converter |
| `integerToHexString` | `String integerToHexString(int value, int minBytes)` | Format an integer as hexadecimal with a minimum byte width. Parameters: value - integer to format minBytes - minimum number of bytes to represent in the returned text Returns: hexa |
| `swapHexString` | `String swapHexString(String value)` | Swap the byte order in hexadecimal text. Parameters: value - hexadecimal text to transform Returns: byte-order-swapped text from the shared converter |
