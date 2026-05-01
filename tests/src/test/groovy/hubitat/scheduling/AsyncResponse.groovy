package hubitat.scheduling

/**
 * Test stub for the AsyncResponse delivered to asynchttp* callbacks. Tests
 * construct one of these directly and pass it to the callback method to
 * exercise success and error paths.
 */
class AsyncResponse {
  Integer status = 200
  Map headers = [:]
  String data
  String errorMessage
  boolean error = false

  Integer getStatus()         { status }
  String  getData()           { data }
  String  getErrorMessage()   { errorMessage }
  String  getErrorData()      { errorMessage }
  Map     getHeaders()        { headers }

  /** Hubitat's AsyncResponse exposes hasError() rather than a getter. */
  boolean hasError() { error || (status != null && status >= 400) }
}
