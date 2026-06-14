package groovyx.net.http

/**
 * Minimal test stub for the http-builder exception that Hubitat bundles at
 * runtime but which is not a dependency of the test project. Apps/drivers
 * reference it in catch clauses (e.g. MorningAnnouncement.callOpenRouterDirect),
 * so it must be resolvable for ScriptLoader's real Groovy compile to succeed.
 *
 * Exposes the two members consuming code reads: statusCode and response.
 */
class HttpResponseException extends RuntimeException {
  int statusCode
  Object response

  HttpResponseException(String message = 'stub HttpResponseException') {
    super(message)
  }

  HttpResponseException(int statusCode, Object response, String message = 'stub HttpResponseException') {
    super(message)
    this.statusCode = statusCode
    this.response = response
  }
}
