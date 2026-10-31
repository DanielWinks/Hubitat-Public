# App executor

- **ID:** `app-preferences`
- **Section:** Apps
- **Class:** `com.hubitat.hub.executor.AppExecutor`

> Use these methods directly in app code; the hub supplies this execution context.

## Inheritance

- Extends `com.hubitat.hub.executor.BaseExecutor`
- [HTTP requests](../shared/http.md)
- [HTTP requests](../shared/http.md)
- [Date and time](../shared/shared-date-time.md)
- [Hub files](../shared/shared-files.md)
- [Network utilities](../shared/shared-network.md)
- [Scheduling](../shared/shared-scheduling.md)
- [Shared utilities](../shared/shared-utilities.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `buttonLink` | `String buttonLink(String btnName, String linkText, String color = "#1A77C9", String stateAttribute = "", String ariaLabel = "", String font = "1em", String tooltip = "" )` | Builds an app action link that changes to a disabled appearance after it is clicked. Parameters: btnName - submitted button identifier used in the generated form fields linkText -  |
| `buttonLinkDisabled` | `String buttonLinkDisabled(String linkText, String ariaLabel = "", String tooltip = "" )` | Builds non-interactive link content for an action that is temporarily unavailable. Parameters: linkText - link label or HTML content ariaLabel - accessible action label tooltip - t |
| `buttonLinkWithConfirm` | `String buttonLinkWithConfirm(String btnName, String linkText, String confirmationMessage, String stateAttribute = "", String ariaLabel = "", String tooltip = "" )` | Builds an app action link that requests confirmation before submitting through the shared UI handler. Parameters: btnName - submitted button identifier used in the generated form f |
| `buttonLinkWithTooltip` | `String buttonLinkWithTooltip(String btnName, String linkText, String tooltip)` | Builds an app action link with the supplied tooltip and the standard link style. Parameters: btnName - submitted button identifier used in the generated form fields linkText - link |
| `component` | `def component(Map options)` | Adds a component-selection item to the current section body, using default values and optional overrides. Parameters: options - values that override the default component-selector  |
| `definition` | `def definition(Map parameters)` | Populates this app type from metadata and returns null after successful population. Parameters: parameters - app type metadata properties populated into AppType Returns: null after |
| `definition` | `def definition(Map parameters, Closure closure)` | Populates this app type from metadata, then returns the supplied closure's result. Parameters: parameters - app type metadata properties populated into AppType closure - closure ru |
| `dynamicPage` | `Map dynamicPage(Map options, Closure closure)` | Builds and returns a dynamic page map with a sections list after invoking its closure. Parameters: options - page values copied into the returned dynamic page map closure - closure |
| `href` | `def href(Map options)` | Adds a page link to the current section body, using default link values and optional overrides. Parameters: options - values that override the default page-link fields Returns: the |
| `href` | `def href(Map options, String page)` | Adds a page link to the current section body, using default link values and optional overrides. Parameters: options - values that override the default page-link fields page - desti |
| `href` | `def href(String page)` | Adds a page link to the current section body, using default link values and optional overrides. Parameters: page - destination preference page name Returns: the Boolean result of a |
| `input` | `def input(Map options)` | Adds a preference input to the current section and body, applying the default input values before overrides. Parameters: options - input values merged into the default preference i |
| `input` | `def input(Map options, String name, String type)` | Adds a preference input to the current section and body, applying the default input values before overrides. Parameters: options - input values merged into the default preference i |
| `input` | `def input(String name, String type)` | Adds a preference input to the current section and body, applying the default input values before overrides. Parameters: name - preference setting name type - preference input type |
| `label` | `def label(Map options)` | Adds a label input to the current section body, using default label values and optional overrides. Parameters: options - values that override the default label-input fields Returns |
| `library` | `void library(Map parameters)` | Accepts a library declaration; this app executor intentionally performs no work for it. Parameters: parameters - metadata or library parameter map |
| `mappings` | `def mappings(Closure cls)` | Runs the mappings closure in the app script context. Parameters: cls - content-building closure Returns: the value returned by the mappings closure. |
| `mode` | `def mode(Map options)` | Adds a mode-selection item to the current section body, using default values and optional overrides. Parameters: options - values that override the default mode-selector fields Ret |
| `page` | `def page(Map options)` | Creates a static or dynamic preference page, optionally applying page options and running its content closure. Parameters: options - page values merged into the default page map Re |
| `page` | `def page(Map options, Closure cls)` | Creates a static or dynamic preference page, optionally applying page options and running its content closure. Parameters: options - page values merged into the default page map cl |
| `page` | `def page(Map options, String name, String title, Closure closure)` | Creates a static or dynamic preference page, optionally applying page options and running its content closure. Parameters: options - page values merged into the default page map na |
| `page` | `def page(String name, String title, Closure closure)` | Creates a static or dynamic preference page, optionally applying page options and running its content closure. Parameters: name - page name title - page or preference section title |
| `paragraph` | `def paragraph(Map options, String description)` | Adds paragraph content to the current section body, with optional caller-supplied overrides. Parameters: options - values that override the default paragraph fields description - p |
| `paragraph` | `def paragraph(String description)` | Adds paragraph content to the current section body, with optional caller-supplied overrides. Parameters: description - paragraph text shown in the section Returns: the Boolean resu |
| `path` | `def path(String pathStr, Closure closure)` | Runs the route closure and stores its result under the supplied path in this app’s mappings. Parameters: pathStr - route path key stored in appMappings closure - route closure whos |
| `preferences` | `def preferences(Closure cls)` | Executes the preferences closure, optionally merging preference options before execution. Parameters: cls - closure that declares preference settings Returns: the value returned by |
| `preferences` | `def preferences(Map options, Closure cls)` | Executes the preferences closure, optionally merging preference options before execution. Parameters: options - preference-level values merged into the preferences map cls - closur |
| `render` | `def render(Map options = [:])` | Marks the supplied rendering options as a render-method response and returns the same map. Parameters: options - render response options; this method adds renderMethod=true Returns |
| `section` | `def section(Closure cls)` | Creates a preference section, optionally applying its options and title, then runs its closure. Parameters: cls - closure that adds inputs and body elements to the section Returns: |
| `section` | `def section(Map options, Closure closure)` | Creates a preference section, optionally applying its options and title, then runs its closure. Parameters: options - section values merged into the default section map closure - c |
| `section` | `def section(Map options, String title, Closure closure)` | Creates a preference section, optionally applying its options and title, then runs its closure. Parameters: options - section values merged into the default section map title - pag |
| `section` | `def section(String title, Closure closure)` | Creates a preference section, optionally applying its options and title, then runs its closure. Parameters: title - page or preference section title closure - closure that adds inp |

## Properties

| Name | Type | Description |
|---|---|---|
| `app` | `InstalledAppWrapper` | — |
