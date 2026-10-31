# QR helper

- **ID:** `api-hubitat-helper-qrhelper`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.QRHelper`

> Use QRHelper from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `generateQR` | `void generateQR(String uri, String pngFileName)` | Write a 200 by 200 PNG QR code into the local web directory. throws: IllegalArgumentException when the filename contains other characters Parameters: uri - text encoded in the QR c |
