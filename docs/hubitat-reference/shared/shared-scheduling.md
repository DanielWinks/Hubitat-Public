# Scheduling

- **ID:** `shared-scheduling`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.executor.BaseExecutor`

> Use these methods directly in app or driver code; the hub supplies this execution context.

## Methods

| Name | Signature | Description |
|---|---|---|
| `cancelRunIn` | `boolean cancelRunIn(String key)` | Cancels the one-shot job identified by its key. Parameters: key - One-shot job key returned by runIn or runOnce, in name\|group form. Returns: True if a matching job was cancelled;  |
| `cancelRunOnce` | `boolean cancelRunOnce(String key)` | Cancels the one-shot job identified by its key. Parameters: key - One-shot job key returned by runIn or runOnce, in name\|group form. Returns: True if a matching job was cancelled;  |
| `runEvery10Minutes` | `void runEvery10Minutes(MetaMethod handlerMethod, Map options = null)` | Schedules recurring execution every 10 minutes with a randomized position in the interval. Parameters: handlerMethod - Groovy method reference to invoke. options - Optional schedul |
| `runEvery10Minutes` | `void runEvery10Minutes(String handlerMethod, Map options = null)` | Schedules recurring execution every 10 minutes with a randomized position in the interval. Parameters: handlerMethod - Name of the handler method to invoke. options - Optional sche |
| `runEvery15Minutes` | `void runEvery15Minutes(MetaMethod handlerMethod, Map options = null)` | Schedules recurring execution every 15 minutes with a randomized position in the interval. Parameters: handlerMethod - Groovy method reference to invoke. options - Optional schedul |
| `runEvery15Minutes` | `void runEvery15Minutes(String handlerMethod, Map options = null)` | Schedules recurring execution every 15 minutes with a randomized position in the interval. Parameters: handlerMethod - Name of the handler method to invoke. options - Optional sche |
| `runEvery1Hour` | `void runEvery1Hour(MetaMethod handlerMethod, Map options = null)` | Schedules recurring execution every 1 hour at a random minute and second offset. Parameters: handlerMethod - Groovy method reference to invoke. options - Optional scheduling settin |
| `runEvery1Hour` | `void runEvery1Hour(String handlerMethod, Map options = null)` | Schedules recurring execution every 1 hour at a random minute and second offset. Parameters: handlerMethod - Name of the handler method to invoke. options - Optional scheduling set |
| `runEvery1Minute` | `void runEvery1Minute(MetaMethod handlerMethod, Map options = null)` | Schedules recurring execution every 1 minute with a randomized position in the interval. Parameters: handlerMethod - Groovy method reference to invoke. options - Optional schedulin |
| `runEvery1Minute` | `void runEvery1Minute(String handlerMethod, Map options = null)` | Schedules recurring execution every 1 minute with a randomized position in the interval. Parameters: handlerMethod - Name of the handler method to invoke. options - Optional schedu |
| `runEvery30Minutes` | `void runEvery30Minutes(MetaMethod handlerMethod, Map options = null)` | Schedules recurring execution every 30 minutes with a randomized position in the interval. Parameters: handlerMethod - Groovy method reference to invoke. options - Optional schedul |
| `runEvery30Minutes` | `void runEvery30Minutes(String handlerMethod, Map options = null)` | Schedules recurring execution every 30 minutes with a randomized position in the interval. Parameters: handlerMethod - Name of the handler method to invoke. options - Optional sche |
| `runEvery3Hours` | `void runEvery3Hours(MetaMethod handlerMethod, Map options = null)` | Schedules recurring execution every 3 hours at a random minute and second offset. Parameters: handlerMethod - Groovy method reference to invoke. options - Optional scheduling setti |
| `runEvery3Hours` | `void runEvery3Hours(String handlerMethod, Map options = null)` | Schedules recurring execution every 3 hours at a random minute and second offset. Parameters: handlerMethod - Name of the handler method to invoke. options - Optional scheduling se |
| `runEvery5Minutes` | `void runEvery5Minutes(MetaMethod handlerMethod, Map options = null)` | Schedules recurring execution every 5 minutes with a randomized position in the interval. Parameters: handlerMethod - Groovy method reference to invoke. options - Optional scheduli |
| `runEvery5Minutes` | `void runEvery5Minutes(String handlerMethod, Map options = null)` | Schedules recurring execution every 5 minutes with a randomized position in the interval. Parameters: handlerMethod - Name of the handler method to invoke. options - Optional sched |
| `runIn` | `String runIn(Long delayInSeconds, MetaMethod handlerMethod, Map options = null)` | Schedules the handler to run after a delay in seconds. Parameters: delayInSeconds - Delay before execution in seconds; the value is converted to milliseconds. handlerMethod - Groov |
| `runIn` | `String runIn(Long delayInSeconds, String handlerMethod, Map options = null)` | Schedules the handler to run after a delay in seconds. Parameters: delayInSeconds - Delay before execution in seconds; the value is converted to milliseconds. handlerMethod - Name  |
| `runInMillis` | `String runInMillis(Long delayInMilliSeconds, MetaMethod handlerMethod, Map options = null)` | Schedules the handler to run after a delay in milliseconds. Parameters: delayInMilliSeconds - Delay before execution in milliseconds. Values below -25 are rejected; values from -25 |
| `runInMillis` | `String runInMillis(Long delayInMilliSeconds, String handlerMethod, Map options = null)` | Schedules the handler to run after a delay in milliseconds. Parameters: delayInMilliSeconds - Delay before execution in milliseconds. Values below -25 are rejected; values from -25 |
| `runOnce` | `String runOnce(Date dateTime, MetaMethod handlerMethod, Map options = null)` | Schedules one handler execution at the requested date and time. Parameters: dateTime - Execution date and time. Times more than 50 milliseconds in the past are ignored; near times  |
| `runOnce` | `String runOnce(Date dateTime, String handlerMethod, Map options = null)` | Schedules one handler execution at the requested date and time. Parameters: dateTime - Execution date and time. Times more than 50 milliseconds in the past are ignored; near times  |
| `runOnce` | `String runOnce(String dateTime, MetaMethod handlerMethod, Map options = null)` | Schedules one handler execution at the requested date and time. Parameters: dateTime - Execution date and time. Times more than 50 milliseconds in the past are ignored; near times  |
| `runOnce` | `String runOnce(String dateTime, String handlerMethod, Map options = null)` | Schedules one handler execution at the requested date and time. Parameters: dateTime - Execution date and time. Times more than 50 milliseconds in the past are ignored; near times  |
| `schedule` | `void schedule(Date dateTime, MetaMethod handlerMethod, Map options = null)` | Schedules a daily handler execution at the specified local clock time. Parameters: dateTime - Date whose local clock time is used for a daily recurring schedule. handlerMethod - Ha |
| `schedule` | `void schedule(Date dateTime, String handlerMethod, Map options = null)` | Schedules a daily handler execution at the specified local clock time. Parameters: dateTime - Date whose local clock time is used for a daily recurring schedule. handlerMethod - Ha |
| `schedule` | `void schedule(String expression, MetaMethod handlerMethod, Map options = null)` | Schedules the handler from a date-time string or cron expression. Parameters: expression - ISO date-time string to create a daily schedule, or a Quartz cron expression. handlerMeth |
| `schedule` | `void schedule(String expression, String handlerMethod, Map options = null)` | Schedules the handler from a date-time string or cron expression. Parameters: expression - ISO date-time string to create a daily schedule, or a Quartz cron expression. handlerMeth |
| `sleepForMillis` | `void sleepForMillis(int sleepFor)` | Pauses the current execution thread for a number of milliseconds. Parameters: sleepFor - Duration to sleep in milliseconds. |
| `unschedule` | `void unschedule()` | Removes scheduled jobs for this executor. |
| `unschedule` | `void unschedule(MetaMethod handlerMethod)` | Removes scheduled jobs for this executor. Parameters: handlerMethod - Handler method name or method reference to invoke. |
| `unschedule` | `void unschedule(String handlerMethod)` | Removes scheduled jobs for this executor. Parameters: handlerMethod - Handler method name or method reference to invoke. |
