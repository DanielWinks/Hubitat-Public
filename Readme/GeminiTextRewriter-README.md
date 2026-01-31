# Gemini Text Rewriter - Comprehensive Documentation

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [Requirements](#requirements)
4. [Installation](#installation)
5. [Child Device (Optional)](#child-device-optional)
6. [Initial Setup](#initial-setup)
7. [Configuration Guide](#configuration-guide)
8. [Rewriting Modes Explained](#rewriting-modes-explained)
9. [HTTP API Endpoints](#http-api-endpoints)
10. [Integration Methods](#integration-methods)
11. [Advanced Usage Examples](#advanced-usage-examples)
12. [Troubleshooting](#troubleshooting)
13. [Performance and Cost Considerations](#performance-and-cost-considerations)
14. [Best Practices](#best-practices)
15. [FAQ](#faq)
16. [Technical Architecture](#technical-architecture)
17. [API Reference](#api-reference)
18. [Version History](#version-history)

---

## Overview

**Gemini Text Rewriter** is a powerful Hubitat Elevation app that leverages Google's Gemini AI to intelligently rewrite, improve, and transform text for use in home automation notifications, announcements, and text-based triggers. It provides seamless integration with Rule Machine, WebCoRE, and other Hubitat apps through multiple interfaces including HTTP endpoints, location events, and global variables.

### What Does It Do?

The app acts as a bridge between your Hubitat hub and Google's Gemini AI service, enabling you to:

- **Improve** notification text to be more professional and readable
- **Shorten** verbose messages to be concise and actionable
- **Lengthen** terse alerts with additional context and detail
- **Formalize** casual text for official announcements
- **Casualize** formal text for friendly, conversational notifications
- **Simplify** complex messages for easier comprehension
- **Custom rewrite** text based on your own specific instructions

### Use Cases

- **Smart Notification Enhancement**: Transform basic sensor alerts into professional, contextual notifications
- **Dynamic Announcements**: Generate varied, natural-sounding TTS announcements that don't sound robotic
- **Message Formatting**: Convert technical device states into human-friendly language
- **Multi-Language Support**: Rewrite text in different tones appropriate for different family members
- **Contextual Alerts**: Expand terse alerts with helpful context and action suggestions
- **Professional Communications**: Format home automation messages for business environments

---

## Features

### Core Capabilities

- ✅ **Six Predefined Rewriting Modes**: Improve, Shorten, Lengthen, Formalize, Casual, Simplify
- ✅ **Custom Mode**: Define your own rewriting instructions via system prompts
- ✅ **Multiple Gemini Models**: Support for all current Gemini models (3 Pro, 3 Flash, 2.5 Pro, 2.5 Flash, 2.5 Flash-Lite, 2.0 Flash)
- ✅ **HTTP API Endpoints**: RESTful endpoints for easy integration with external systems
- ✅ **Location Event System**: Subscribe to rewrite requests/responses for cross-app communication
- ✅ **Global Variable Storage**: Store results in Hubitat connector variables for Rule Machine access
- ✅ **Request History**: Optional tracking of recent rewriting operations
- ✅ **Real-Time Testing**: Built-in test interface to validate results before deployment
- ✅ **Flexible Response Formats**: JSON and plain text responses for different use cases
- ✅ **Format Preservation**: Optional line break and paragraph structure retention
- ✅ **Error Handling**: Robust error handling with fallback text support
- ✅ **Configurable Parameters**: Fine-tune temperature (creativity) and token limits
- ✅ **OAuth Security**: Secure webhook access via OAuth tokens
- ✅ **Comprehensive Logging**: Debug, info, and error logging with auto-disable

### Advanced Features

- **Temperature Control**: Adjust AI creativity from focused (0.0) to highly creative (1.0)
- **Token Limit Configuration**: Control output length from 50 to 8192 tokens
- **Concurrent Request Handling**: Process multiple rewriting requests simultaneously
- **Automatic Error Recovery**: Gracefully handle API failures with informative error messages
- **Cloud and Local Endpoints**: Access via Hubitat cloud or local network
- **Multiple Integration Patterns**: Webhooks, events, variables, and direct method calls

---

## Requirements

### Hubitat Requirements

- **Hubitat Elevation Hub**: Firmware version **2.3.4 or higher**
- **Enabled OAuth**: The app requires OAuth to be enabled in Apps Code settings
- **Internet Connectivity**: Active internet connection for Gemini API access
- **DWinks Utilities and Logging Library**: Required dependency (auto-installed via HPM)

### Google Gemini Requirements

- **Google Cloud Account**: Free or paid account with Google AI Studio access
- **Gemini API Key**: Generated from [Google AI Studio](https://aistudio.google.com/app/apikey)
- **API Quota**: Free tier includes generous limits; paid tiers available for high-volume usage

### Network Requirements

- **Outbound HTTPS**: Hub must be able to reach `generativelanguage.googleapis.com` on port 443
- **DNS Resolution**: Functional DNS for resolving Google API endpoints
- **Firewall Rules**: Allow outbound connections to Google's Gemini API servers

---

## Installation

### Method 1: Hubitat Package Manager (HPM) - Recommended

1. **Install HPM** (if not already installed):
   - Navigate to **Apps Code** → Click **New App**
   - Copy code from [HPM Installation](https://raw.githubusercontent.com/dcmeglio/hubitat-packagemanager/master/apps/Package_Manager.groovy)
   - Click **Save** → Return to **Apps** → Click **Add User App** → Select **Hubitat Package Manager**

2. **Install Gemini Text Rewriter**:
   - Open **Hubitat Package Manager** app
   - Select **Install** → **Search by Keywords**
   - Search for: `Gemini Text Rewriter` or `dwinks`
   - Select **Gemini Text Rewriter** from results
   - Click **Next** → **Install** → Confirm installation
   - HPM will automatically install required dependencies

### Method 2: Manual Installation

1. **Install Utilities and Logging Library** (if not present):
   - Navigate to **Apps Code** → Click **New App**
   - Copy code from: [UtilitiesAndLoggingLibrary.groovy](https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Libraries/UtilitiesAndLoggingLibrary.groovy)
   - Paste into code editor
   - Click **Save**

2. **Install Gemini Text Rewriter**:
   - Navigate to **Apps Code** → Click **New App**
   - Copy code from: [GeminiTextRewriter.groovy](https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/GeminiTextRewriter.groovy)
   - Paste into code editor
   - Click **Save**

3. **Enable OAuth**:
   - While viewing the Gemini Text Rewriter app code
   - Scroll to top-right of the code editor
   - Find **OAuth** section → Click **Enable OAuth in App**
   - Click **Update** to generate OAuth credentials

4. **Add the App**:
   - Navigate to **Apps** → Click **Add User App**
   - Select **Gemini Text Rewriter** from the list
   - Click through to configuration page

---

## Child Device (Optional)

The Gemini Text Rewriter app includes an optional **child device driver** that provides a simpler, device-based interface for rewriting text. This is particularly useful for Rule Machine users who prefer using device commands over HTTP endpoints.

### What is the Child Device?

The child device is a virtual device that acts as a convenient interface to the Gemini Text Rewriter app. Instead of making HTTP calls or using location events, you can simply call commands on the device like any other Hubitat device.

**Key Benefits**:

- ✅ **No HTTP endpoints needed** - Use device commands directly in Rule Machine
- ✅ **Simpler configuration** - Set default modes and custom instructions in device preferences
- ✅ **Multiple devices** - Create multiple child devices with different configurations
- ✅ **One-step custom rewrites** - Use `rewriteTextWithPrompt` for custom instructions without pre-configuration
- ✅ **Status tracking** - Device attributes show processing status and results
- ✅ **Rule Machine friendly** - Easy to use in custom commands and actions

### Installing the Child Device Driver

**Via Hubitat Package Manager (HPM)**:

1. The child device driver is automatically included when installing via HPM
2. No additional steps needed

**Manual Installation**:

1. Navigate to **Drivers Code** → Click **New Driver**
2. Copy code from: [GeminiTextRewriterDevice.groovy](https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/GeminiTextRewriterDevice.groovy)
3. Paste into code editor and click **Save**

### Creating a Child Device

1. **Open the Gemini Text Rewriter app**
2. Scroll to the **Child Devices** section (if available in your app version, or create manually)
3. **Manual Creation** (if app doesn't have built-in UI):
   - Navigate to **Devices** → Click **Add Virtual Device**
   - **Device Name**: Choose descriptive name (e.g., "Gemini Rewriter - Announcements")
   - **Device Network Id**: Enter unique ID (e.g., `gemini-rewriter-1`)
   - **Type**: Select **Gemini Text Rewriter Device**
   - **Parent App**: Select your Gemini Text Rewriter app instance
   - Click **Save Device**

### Configuring the Child Device

1. **Navigate to the device page** (Devices → Your child device)
2. **Configure preferences**:
   - **Default Rewrite Mode**: Select the mode used when calling `rewriteText()` command
     - Options: improve, shorten, lengthen, formalize, casual, simplify, custom
   - **Enable Logging**: Turn on for debugging
   - **Enable Debug Logging**: Turn on for detailed diagnostics
3. Click **Save Preferences**

### Using the Child Device - Basic Commands

#### Command: `rewriteText`

Rewrites text using the device's configured default mode.

**Parameters**:

- `text` (required): The text to rewrite

**Example in Rule Machine**:

```
Action: Custom Command
Device: [Your Gemini child device]
Command: rewriteText
Parameters:
  - text: "Motion detected in living room"
```

**Result**: Text is rewritten using the device's default mode, result stored in `lastRewrittenText` attribute

#### Command: `rewriteTextWithPrompt`

Rewrites text with a directly specified custom prompt (one-step custom rewriting).

**Parameters**:

- `text` (required): The text to rewrite
- `customPrompt` (required): Custom instructions for how to rewrite

**Example in Rule Machine**:

```
Action: Custom Command
Device: [Your Gemini child device]
Command: rewriteTextWithPrompt
Parameters:
  - text: "Temperature is 85 degrees"
  - customPrompt: "Make this sound urgent and include a suggestion to check the AC"
```

**Result**: Text is rewritten according to your custom prompt

**Use Cases**:

- Dynamic custom prompts based on conditions
- Different rewrite styles for different triggers
- Context-specific transformations without pre-configuration

#### Command: `setCustomInstructions`

Stores custom rewrite instructions in the device for later use with `custom` mode.

**Parameters**:

- `instructions` (required): Custom prompt/instructions

**Example in Rule Machine**:

```
Action: Custom Command
Device: [Your Gemini child device]
Command: setCustomInstructions
Parameters:
  - instructions: "Rewrite as if explaining to a child, use simple words"
```

**Use Case**: Configure device for specific rewriting style, then use `rewriteText()` with `custom` mode

#### Command: `clearCustomInstructions`

Clears any stored custom instructions.

**Parameters**: None

**Example**: Simply select this command in Rule Machine to reset custom instructions

### Device Attributes

The child device exposes several attributes you can use in conditions and actions:

- **`lastRewrittenText`**: The most recent rewritten text result
- **`lastOriginalText`**: The original text that was rewritten
- **`lastMode`**: The mode used for the last rewrite operation
- **`status`**: Current status (processing, success, error, etc.)
- **`customInstructions`**: Currently stored custom instructions
- **`defaultMode`**: The configured default rewrite mode

### Complete Rule Machine Example

**Scenario**: Motion detected → Rewrite announcement → Speak on Alexa

```
Trigger:
  Motion Sensor (Living Room) active

Actions:
  1. Custom Command:
     Device: Gemini Rewriter - Announcements
     Command: rewriteText
     Parameters:
       - text: "Motion detected in %device.label% at %time%"

  2. Wait for Condition:
     Condition: Gemini Rewriter status = "success"
     Timeout: 5 seconds

  3. Speak on Alexa:
     Message: %Gemini Rewriter:lastRewrittenText%
```

### Advanced Rule Machine Example with Custom Prompt

**Scenario**: Temperature alert with dynamic urgency based on temperature

```
Trigger:
  Temperature Sensor > 78°F

Actions:
  IF (Temperature > 85) THEN
    Custom Command:
      Device: Gemini Rewriter
      Command: rewriteTextWithPrompt
      Parameters:
        - text: "Temperature is %Temperature Sensor:temperature%"
        - customPrompt: "Make this URGENT and suggest checking AC immediately"

  ELSE IF (Temperature > 80) THEN
    Custom Command:
      Device: Gemini Rewriter
      Command: rewriteTextWithPrompt
      Parameters:
        - text: "Temperature is %Temperature Sensor:temperature%"
        - customPrompt: "Make this moderately concerning and suggest opening windows"

  ELSE
    Custom Command:
      Device: Gemini Rewriter
      Command: rewriteText
      Parameters:
        - text: "Temperature is %Temperature Sensor:temperature%"
  END IF

  Wait for Condition: Gemini Rewriter status = "success" (5 sec timeout)
  Notify: %Gemini Rewriter:lastRewrittenText%
```

### Tips for Using Child Devices

**Multiple Devices for Different Purposes**:
Create separate child devices with different default modes:

- "Gemini - Casual" (default mode: casual) for friendly announcements
- "Gemini - Formal" (default mode: formalize) for guest-mode notifications
- "Gemini - Short" (default mode: shorten) for SMS/text notifications
- "Gemini - Custom" (default mode: custom) with specific instructions

**Best Practices**:

1. **Use `rewriteText`** when you want consistent, mode-based rewriting
2. **Use `rewriteTextWithPrompt`** when you need dynamic, context-specific rewrites
3. **Set custom instructions** once if you'll use the same custom prompt repeatedly
4. **Wait for status** before using the result to ensure processing completed
5. **Check status attribute** in conditionals to handle errors gracefully

**Error Handling in Rules**:

```
Actions:
  1. Custom Command: rewriteText [...]
  2. Wait for Condition: status != "processing" (timeout 10 sec)
  3. IF (status = "success") THEN
       Use: %lastRewrittenText%
     ELSE
       Use fallback: "Alert from sensor"
     END IF
```

---

## Initial Setup

### Step 1: Obtain Google Gemini API Key

1. **Visit Google AI Studio**:
   - Navigate to: [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)
   - Sign in with your Google account

2. **Create API Key**:
   - Click **"Create API Key"**
   - Select or create a Google Cloud project
   - Copy the generated API key
   - **Important**: Store this key securely - you won't be able to view it again

3. **Verify Access**:
   - Check that your project has Gemini API enabled
   - Review quota limits on the [Google Cloud Console](https://console.cloud.google.com/)

### Step 2: Configure the App

1. **Open App Configuration**:
   - Navigate to **Apps** → Select your **Gemini Text Rewriter** instance
   - If OAuth isn't enabled, you'll see a red warning message - return to Apps Code to enable it

2. **Enter API Credentials**:
   - **Gemini API Key**: Paste your API key from Google AI Studio
   - **Gemini Model**: Select your preferred model (default: `gemini-2.5-flash`)
     - **gemini-3-pro-preview**: Most powerful, best for complex reasoning
     - **gemini-3-flash-preview**: Fast, high-scale inference
     - **gemini-2.5-flash**: Fast and balanced (recommended default)
     - **gemini-2.5-pro**: Advanced reasoning for complex tasks
     - **gemini-2.5-flash-lite**: Fastest, most cost-efficient
     - **gemini-2.0-flash**: Legacy model

3. **Configure Generation Parameters**:
   - **Maximum Output Tokens** (50-8192): Controls max length of generated text
     - **50-200**: Very brief responses
     - **200-500**: Short to medium responses (default: 1000)
     - **500-2000**: Medium to long responses
     - **2000+**: Extended responses with detailed context

   - **Temperature** (0.0-1.0): Controls creativity vs. consistency
     - **0.0-0.3**: Very focused and deterministic (technical docs)
     - **0.4-0.6**: Balanced creativity (general use)
     - **0.7**: Good default for varied but consistent output
     - **0.8-1.0**: Highly creative and varied (creative writing)

### Step 3: Configure Rewriting Options

1. **Default Rewriting Mode**:
   - Select your most commonly used mode
   - This mode applies when no specific mode is provided in requests
   - Can be overridden per-request via API or UI

2. **Custom System Prompt** (optional):
   - Define instructions for the "custom" mode
   - Example: `"Rewrite text to be more empathetic and emotionally supportive"`
   - Example: `"Convert technical jargon into layman's terms"`
   - Example: `"Add helpful next-step suggestions to alerts"`

3. **Preserve Line Breaks** (optional):
   - Enable to maintain original text formatting structure
   - Useful for multi-paragraph content or formatted lists
   - Disable for maximum AI freedom in restructuring

4. **Global Variable Storage** (optional):
   - **Store Result in Global Variable**: Enable to save results in Hubitat connector variables
   - **Global Variable Name**: Set custom variable name (default: `geminiRewrittenText`)
   - Access in Rule Machine via: `%geminiRewrittenText%`

### Step 4: Test the Configuration

1. **Run a Test Rewrite**:
   - Scroll to **Testing** section
   - **Test Text**: Enter sample text, e.g., `"temp sensor detected high value"`
   - **Test Mode**: Select mode to test (e.g., `improve`)
   - Click **"Test Rewrite"** button

2. **Review Results**:
   - **Last Result** displays the rewritten text
   - **Original** shows your input
   - **Mode** and **Timestamp** confirm execution details

3. **Verify Success**:
   - Check that result is reasonable and matches expected behavior
   - If errors occur, see [Troubleshooting](#troubleshooting) section
   - Adjust temperature/tokens if output is too creative or too constrained

### Step 5: Configure Logging

1. **Enable Logging** (recommended for initial setup):
   - **Enable Logging**: ON (general info messages)
   - **Enable Debug Logging**: ON (detailed API request/response logs)
   - **Enable Description Text**: ON (human-readable status messages)

2. **Auto-Disable**:
   - Logs automatically disable after 30 minutes
   - Prevents log file bloat during normal operation
   - Re-enable as needed for troubleshooting

3. **Click Initialize** button to apply all settings

---

## Configuration Guide

### Detailed Setting Explanations

#### Google Gemini API Configuration

**Gemini API Key**

- **Type**: Text input (required)
- **Purpose**: Authenticates requests to Google's Gemini API
- **Security**: Stored in app settings; never logged or exposed via endpoints
- **Validation**: App checks for presence but doesn't validate format until first API call
- **Troubleshooting**: If API calls fail with 401/403 errors, verify key is correct

**Gemini Model**

- **Type**: Dropdown selection (required)
- **Default**: `gemini-2.5-flash`
- **Options**:
  - `gemini-3-pro-preview`: Cutting-edge reasoning, highest quality, slower
  - `gemini-3-flash-preview`: Fast inference with excellent quality
  - `gemini-2.5-flash`: Best balance of speed and quality (recommended)
  - `gemini-2.5-pro`: Advanced reasoning for complex transformations
  - `gemini-2.5-flash-lite`: Maximum speed and cost efficiency
  - `gemini-2.0-flash`: Legacy model for compatibility
- **Cost Impact**: More advanced models may have higher API costs
- **Access**: Ensure your Google Cloud project has access to selected model

**Maximum Output Tokens**

- **Type**: Number input (50-8192)
- **Default**: 1000
- **Purpose**: Limits length of generated text
- **Calculation**: ~4 characters per token in English
  - 100 tokens ≈ 75 words
  - 500 tokens ≈ 375 words
  - 1000 tokens ≈ 750 words
- **Cost Impact**: Higher limits increase API costs proportionally
- **Recommendation**: Start with 1000; adjust based on typical message lengths

**Temperature**

- **Type**: Decimal input (0.0-1.0)
- **Default**: 0.7
- **Purpose**: Controls randomness and creativity
- **Effects**:
  - **Low (0.0-0.3)**: Highly deterministic, same input → same output, literal interpretations
  - **Medium (0.4-0.7)**: Balanced, reasonable variation, maintains consistency
  - **High (0.8-1.0)**: Very creative, unpredictable, experimental phrasing
- **Use Cases**:
  - **0.1-0.3**: Technical documentation, legal text, consistent templates
  - **0.5-0.7**: General notifications, alerts, announcements
  - **0.8-1.0**: Creative writing, varied TTS announcements

#### Rewriting Options

**Default Rewriting Mode**

- **Type**: Dropdown selection (required)
- **Default**: `improve`
- **Purpose**: Mode used when API requests don't specify a mode
- **Modes**: See [Rewriting Modes Explained](#rewriting-modes-explained)
- **Override**: Can be overridden per-request via `mode` parameter

**Custom System Prompt**

- **Type**: Text input (optional)
- **Default**: `"Rewrite the following text to be more engaging and interesting."`
- **Purpose**: Provides AI instructions when `custom` mode is selected
- **Examples**:
  ```
  "Rewrite as if explaining to a 10-year-old child"
  "Convert to bullet points with action items"
  "Add emoji and make it fun and playful"
  "Translate tone to be urgent and commanding"
  "Include helpful troubleshooting suggestions"
  ```
- **Tips**: Be specific and clear; AI interprets instructions literally

**Preserve Line Breaks**

- **Type**: Boolean checkbox
- **Default**: Enabled (true)
- **Purpose**: Instructs AI to maintain original paragraph and line break structure
- **Effect When Enabled**: AI attempts to preserve formatting; may not be perfect
- **Effect When Disabled**: AI freely restructures content for optimal readability
- **Use Cases**:
  - **Enable**: Formatted lists, multi-paragraph content, structured data
  - **Disable**: Single sentences, short alerts, maximum AI freedom

**Store Result in Global Variable**

- **Type**: Boolean checkbox
- **Default**: Enabled (true)
- **Purpose**: Saves rewritten text to Hubitat connector variable
- **Benefits**: Easy access from Rule Machine, WebCoRE, and other apps
- **Performance**: Minimal overhead; safe to keep enabled

**Global Variable Name**

- **Type**: Text input (conditional)
- **Default**: `geminiRewrittenText`
- **Visible**: Only when "Store Result in Global Variable" is enabled
- **Purpose**: Sets the name of the connector variable
- **Naming Rules**: No spaces, alphanumeric and underscores only
- **Access in Rule Machine**: Use `%variableName%` syntax
- **Multiple Instances**: Use unique names for multiple app instances

#### HTTP Endpoints Section

**Purpose**: Displays webhooks for external integration

**Rewrite Text Endpoint (JSON Response)**

- **Format**: `/rewrite?access_token=YOUR_TOKEN`
- **Methods**: GET, POST
- **Returns**: JSON object with success flag and rewritten text
- **Use Case**: Integration with external systems, Node-RED, Home Assistant

**Rewrite Text Endpoint (Plain Text Response)**

- **Format**: `/rewriteText?access_token=YOUR_TOKEN`
- **Methods**: GET, POST
- **Returns**: Plain text (just the rewritten string)
- **Use Case**: Simple curl commands, quick testing, lightweight integrations

**Get Last Result Endpoint (JSON)**

- **Format**: `/lastResult?access_token=YOUR_TOKEN`
- **Method**: GET
- **Returns**: JSON with most recent rewrite operation details
- **Use Case**: Polling for results, status checks, debugging

**Get Last Result Endpoint (Plain Text)**

- **Format**: `/lastResultText?access_token=YOUR_TOKEN`
- **Method**: GET
- **Returns**: Plain text of most recent rewrite
- **Use Case**: Quick retrieval in scripts or automations

**Access Token**: Automatically generated when OAuth is enabled; unique per app instance

#### Request History

**Keep Request History**

- **Type**: Boolean checkbox
- **Default**: Disabled (false)
- **Purpose**: Maintains a log of recent rewriting operations
- **Storage**: Stored in app state (limited to last 20 entries)
- **Use Cases**: Debugging, reviewing AI behavior patterns, audit trails
- **Memory Impact**: Minimal; ~5-10KB per 20 entries
- **Recommendation**: Enable during testing; disable in production if not needed

**Recent Requests Counter**

- **Visible**: Only when history is enabled and contains entries
- **Display**: Shows count of stored history items
- **Max Size**: Automatically limited to 20 most recent entries

**Clear History Button**

- **Purpose**: Removes all stored history entries
- **Effect**: Frees app state memory; operation is immediate and irreversible
- **Use Case**: Clean up after testing or before deployment

#### Logging Options

**Enable Logging**

- **Type**: Boolean checkbox
- **Default**: Enabled (true)
- **Purpose**: Logs general info messages (rewrite success, configuration changes)
- **Auto-Disable**: Automatically disables after 30 minutes
- **Log Location**: Hubitat Logs page

**Enable Debug Logging**

- **Type**: Boolean checkbox
- **Default**: Disabled (false)
- **Purpose**: Logs detailed diagnostic info (API requests, response parsing, internal state)
- **Performance**: Minimal impact; useful for troubleshooting
- **Auto-Disable**: Automatically disables after 30 minutes
- **Warning**: May log API URLs (with masked keys) and text content

**Enable Description Text**

- **Type**: Boolean checkbox
- **Default**: Enabled (true)
- **Purpose**: Logs human-readable event descriptions
- **Audience**: Designed for end-users; less technical than debug logs
- **Use Case**: Understanding app behavior without diving into technical details

**Initialize Button**

- **Purpose**: Manually reinitializes the app
- **When to Use**:
  - After making configuration changes
  - To reset internal state
  - To clear errors and restart fresh
  - After network connectivity issues
- **Effect**: Clears stale state, validates configuration, restarts logging timers

---

## Rewriting Modes Explained

### Improve Mode

**Purpose**: Enhance grammar, clarity, and flow while preserving original meaning

**Behavior**:

- Corrects grammatical errors
- Improves sentence structure and readability
- Enhances word choice without changing meaning
- Maintains original tone and style

**Best For**:

- Raw sensor data alerts
- Quick messages that need polish
- User-generated text with typos

**Example**:

```
Input:  "temp sensor detect high value need check now"
Output: "The temperature sensor has detected an elevated reading. Please check it now."
```

### Shorten Mode

**Purpose**: Create concise, punchy messages by removing fluff

**Behavior**:

- Eliminates unnecessary words
- Condenses multi-sentence paragraphs
- Preserves all critical information
- Results in brief, actionable text

**Best For**:

- Mobile notifications
- TTS announcements (shorter = faster)
- Dashboard displays with space constraints

**Example**:

```
Input:  "The motion sensor in the living room has detected movement and triggered the automation rule."
Output: "Living room motion detected. Rule triggered."
```

### Lengthen Mode

**Purpose**: Expand text with additional detail and context

**Behavior**:

- Adds descriptive details
- Provides clearer explanations
- Elaborates on the core message
- Makes content more comprehensive

**Best For**:

- Enhancing terse alerts with context
- Creating detailed announcements
- Adding helpful information to notifications

**Example**:

```
Input:  "Front door unlocked"
Output: "The front door has been unlocked. This may indicate someone has arrived home or left the building. Please verify this action was authorized."
```

### Formalize Mode

**Purpose**: Convert to professional, business-appropriate language

**Behavior**:

- Uses formal vocabulary
- Avoids contractions (don't → do not)
- Eliminates slang and casual phrasing
- Suitable for official communications

**Best For**:

- Office automation announcements
- Guest notifications
- Professional environments
- Email-style alerts

**Example**:

```
Input:  "Hey, the AC's busted and it's getting hot in here"
Output: "Please be advised that the air conditioning system is currently experiencing a malfunction, resulting in elevated indoor temperatures."
```

### Casual Mode

**Purpose**: Make text friendly, conversational, and relaxed

**Behavior**:

- Uses contractions and simple language
- Conversational tone
- Approachable and friendly
- Suitable for family/personal use

**Best For**:

- Home/family notifications
- Personal reminders
- Friendly announcements
- Reducing formality of device messages

**Example**:

```
Input:  "Your laundry cycle has completed. Please remove items from the washing machine."
Output: "Hey! Your laundry's done. Time to grab those clothes from the washer."
```

### Simplify Mode

**Purpose**: Rewrite using basic vocabulary and simple sentence structures

**Behavior**:

- Uses elementary vocabulary
- Short, clear sentences
- Avoids technical jargon
- Aims for 5th-grade reading level

**Best For**:

- Accessibility
- Children's notifications
- Complex technical messages
- Users with reading difficulties

**Example**:

```
Input:  "The HVAC system has initiated its cooling cycle due to the ambient temperature exceeding the configured threshold."
Output: "The air conditioner turned on because it got too hot inside."
```

### Custom Mode

**Purpose**: Apply user-defined rewriting instructions

**Behavior**:

- Follows instructions in "Custom System Prompt" setting
- Fully customizable transformation
- Limited only by AI capabilities

**Best For**:

- Specialized use cases
- Unique formatting requirements
- Experimental transformations
- Domain-specific rewrites

**Example Custom Prompts**:

```
"Add a relevant emoji at the beginning and make it playful"
"Convert to a haiku poem"
"Rewrite as a pirate would say it"
"Add specific next-step instructions"
"Translate technical terms to layman explanations"
```

**Example (with prompt "Add relevant emoji and make playful")**:

```
Input:  "Battery low on smoke detector"
Output: "🔋 Uh-oh! Your smoke detector's battery is running low. Time to swap it out! 🔧"
```

---

## HTTP API Endpoints

### Authentication

All endpoints require OAuth authentication via the `access_token` parameter:

```
?access_token=YOUR_GENERATED_TOKEN
```

**Token Location**: Displayed in app configuration page after enabling OAuth

**Security**: Tokens are unique per app instance; treat as sensitive credentials

### Endpoint Reference

#### 1. Rewrite Text (JSON Response)

**Endpoint**: `/rewrite`

**Methods**: GET, POST

**GET Request Format**:

```
GET http://[hub-ip]/apps/api/[app-id]/rewrite?access_token=[token]&text=[text-to-rewrite]&mode=[mode]
```

**GET Parameters**:

- `text` (required): URL-encoded text to rewrite
- `mode` (optional): Rewriting mode (`improve`, `shorten`, `lengthen`, `formalize`, `casual`, `simplify`, `custom`)
- `access_token` (required): OAuth token

**POST Request Format**:

```
POST http://[hub-ip]/apps/api/[app-id]/rewrite?access_token=[token]
Content-Type: application/json

{
  "text": "text to rewrite",
  "mode": "improve"
}
```

**Success Response** (200):

```json
{
  "success": true,
  "original": "temp sensor detected high value",
  "rewritten": "The temperature sensor has detected an elevated reading.",
  "mode": "improve"
}
```

**Error Response** (400/500):

```json
{
  "success": false,
  "error": "No text provided"
}
```

**cURL Example**:

```bash
curl -X POST "http://192.168.1.100/apps/api/123/rewrite?access_token=abc123" \
  -H "Content-Type: application/json" \
  -d '{"text":"door open","mode":"improve"}'
```

#### 2. Rewrite Text (Plain Text Response)

**Endpoint**: `/rewriteText`

**Methods**: GET, POST

**Purpose**: Returns just the rewritten text as plain text (no JSON wrapper)

**GET Request Format**:

```
GET http://[hub-ip]/apps/api/[app-id]/rewriteText?access_token=[token]&text=[text]&mode=[mode]
```

**POST Request Format**:

```
POST http://[hub-ip]/apps/api/[app-id]/rewriteText?access_token=[token]
Content-Type: application/json

{
  "text": "text to rewrite",
  "mode": "shorten"
}
```

**Success Response** (200):

```
The temperature sensor has detected an elevated reading.
```

_(Plain text - no JSON)_

**Error Response** (400/500):

```
Error: No text provided
```

**cURL Example**:

```bash
TEXT=$(curl -s "http://192.168.1.100/apps/api/123/rewriteText?access_token=abc123&text=door%20open&mode=improve")
echo $TEXT
```

#### 3. Get Last Result (JSON)

**Endpoint**: `/lastResult`

**Method**: GET

**Purpose**: Retrieves the most recent rewriting operation details

**Request Format**:

```
GET http://[hub-ip]/apps/api/[app-id]/lastResult?access_token=[token]
```

**Success Response** (200):

```json
{
  "success": true,
  "original": "temp sensor detected high value",
  "rewritten": "The temperature sensor has detected an elevated reading.",
  "mode": "improve",
  "timestamp": "2026-01-31 14:23:45"
}
```

**No Results Response** (404):

```json
{
  "error": "No results available yet"
}
```

**Use Case**: Polling for results after async operations, debugging, status dashboards

#### 4. Get Last Result (Plain Text)

**Endpoint**: `/lastResultText`

**Method**: GET

**Purpose**: Retrieves just the rewritten text from the last operation

**Request Format**:

```
GET http://[hub-ip]/apps/api/[app-id]/lastResultText?access_token=[token]
```

**Success Response** (200):

```
The temperature sensor has detected an elevated reading.
```

**No Results Response** (404):

```
Error: No results available yet
```

**Use Case**: Quick retrieval in scripts, automation chains, variable assignment

---

## Integration Methods

### Method 1: Rule Machine Integration

Rule Machine can access rewritten text via the global connector variable.

**Setup**:

1. Enable "Store Result in Global Variable" in app settings
2. Set variable name (e.g., `geminiRewrittenText`)
3. Create a Rule Machine rule

**Example Rule**:

```
Trigger: Contact sensor opens
Actions:
  1. HTTP GET: http://[hub-ip]/apps/api/[app-id]/rewriteText?
                access_token=[token]&
                text=Front door opened&
                mode=improve
  2. Delay: 2 seconds
  3. Notify: %geminiRewrittenText%
```

**Advanced Example with Variable**:

```
Trigger: Motion detected
Actions:
  1. Set Variable: originalText = "Motion in %device.label%"
  2. HTTP GET: rewriteText endpoint with text=%originalText%&mode=casual
  3. Delay: 3 seconds
  4. Speak on Sonos: %geminiRewrittenText%
```

### Method 2: WebCoRE Integration

WebCoRE can call endpoints via HTTP requests and use the response.

**Basic Piston**:

```
When
  Temperature sensor > 75°F
Then
  With
    HTTP GET
    URL: http://[hub]/apps/api/[id]/rewriteText
    Parameters:
      access_token: [token]
      text: High temperature detected in {$location}
      mode: formalize
  Wait 2 seconds
  Send notification: {$response}
```

**Advanced Piston with JSON Parsing**:

```
When
  Any contact sensor changes to open
Then
  Set variable originalMsg = "{$currentEventDevice} opened"
  With
    HTTP POST
    URL: http://[hub]/apps/api/[id]/rewrite?access_token=[token]
    Body: {"text": "{originalMsg}", "mode": "improve"}
    Content-Type: application/json
  Set variable rewritten = {$json.rewritten}
  Send push notification: {rewritten}
```

### Method 3: Location Events (Cross-App Communication)

Apps can communicate without HTTP calls using Hubitat location events.

**Sending a Request (from another app)**:

```groovy
// Import
import groovy.json.JsonOutput

// Send rewrite request
Map requestData = [
  mode: 'improve',
  requestId: 'my-unique-id-123',
  fallbackText: originalText  // Used if rewrite fails
]

sendLocationEvent(
  name: 'geminiRewriteRequest',
  value: originalText,  // Text to rewrite
  data: JsonOutput.toJson(requestData)
)
```

**Receiving the Response (in requesting app)**:

```groovy
// In your app's initialize()
subscribe(location, 'geminiRewriteResponse', 'handleRewriteResponse')

// Handler method
void handleRewriteResponse(evt) {
  Map data = parseJson(evt.data)

  if (data.success) {
    String rewrittenText = data.rewritten
    // Use the rewritten text
    log.info("Rewrite successful: ${rewrittenText}")
  } else {
    String errorMsg = data.error
    String fallbackText = data.rewritten  // Will be fallback if provided
    log.warn("Rewrite failed: ${errorMsg}, using fallback")
  }
}
```

**Benefits**:

- No HTTP overhead
- Built-in fallback mechanism
- Asynchronous processing
- Ideal for app-to-app integration

### Method 4: Node-RED Integration

Node-RED can easily integrate via HTTP request nodes.

**Flow Configuration**:

1. **Inject Node**: Trigger (timestamp, device event, etc.)

2. **Function Node** (prepare request):

```javascript
msg.payload = {
  text: "Temperature is 85 degrees",
  mode: "casual",
};
msg.headers = {
  "Content-Type": "application/json",
};
return msg;
```

3. **HTTP Request Node**:
   - Method: POST
   - URL: `http://[hub-ip]/apps/api/[app-id]/rewrite?access_token=[token]`
   - Return: JSON object

4. **Function Node** (parse response):

```javascript
if (msg.payload.success) {
  msg.payload = msg.payload.rewritten;
} else {
  msg.payload = "Error: " + msg.payload.error;
}
return msg;
```

5. **Output Node**: Send to notification service, dashboard, etc.

### Method 5: Direct HTTP Calls (curl, wget, Python)

**Bash/curl**:

```bash
#!/bin/bash
HUB="http://192.168.1.100"
APP_ID="123"
TOKEN="your-token-here"
TEXT="door sensor triggered"
MODE="improve"

RESULT=$(curl -s -X POST \
  "${HUB}/apps/api/${APP_ID}/rewriteText?access_token=${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"text\":\"${TEXT}\",\"mode\":\"${MODE}\"}")

echo "Rewritten: $RESULT"
```

**Python**:

```python
import requests
import json

HUB_IP = "192.168.1.100"
APP_ID = "123"
TOKEN = "your-token-here"

def rewrite_text(text, mode="improve"):
    url = f"http://{HUB_IP}/apps/api/{APP_ID}/rewrite"
    params = {"access_token": TOKEN}
    payload = {"text": text, "mode": mode}

    response = requests.post(url, params=params, json=payload)

    if response.status_code == 200:
        data = response.json()
        if data["success"]:
            return data["rewritten"]
        else:
            return f"Error: {data['error']}"
    else:
        return f"HTTP Error: {response.status_code}"

# Usage
result = rewrite_text("motion detected in living room", "casual")
print(result)
```

---

## Advanced Usage Examples

### Example 1: Dynamic TTS Announcements

Create varied, natural-sounding announcements that don't sound robotic.

**Scenario**: Announce when someone arrives home, but vary the message each time.

**Rule Machine Rule**:

```
Trigger: Presence sensor arrives
Actions:
  1. HTTP GET rewriteText endpoint:
     text="[%device.displayName%] arrived home"
     mode=casual
     temperature=0.9 (via custom app setting for variety)
  2. Delay 2 seconds
  3. Speak on all speakers: %geminiRewrittenText%
```

**Results** (will vary each time):

- "Hey! Looks like John just got home!"
- "Welcome back, John!"
- "John's home everyone!"
- "Guess who's back? John just arrived!"

### Example 2: Context-Aware Security Alerts

Enhance basic sensor alerts with helpful context.

**Custom System Prompt**:

```
"Rewrite security alerts to include the detected issue, potential causes, and suggested actions. Be urgent but not alarming."
```

**Rule**:

```
Trigger: Water sensor detects wet
Actions:
  1. HTTP GET rewriteText endpoint:
     text="Water detected by [%device.label%]"
     mode=custom
  2. Delay 2 seconds
  3. Send push notification (critical): %geminiRewrittenText%
```

**Result**:

```
"ALERT: Water has been detected by the basement sensor. This could indicate a leak or flooding. Please investigate immediately and shut off water if necessary."
```

### Example 3: Multi-Language Household Notifications

Rewrite notifications to suit different family members' preferences.

**Setup**: Create multiple app instances with different settings:

- Instance 1: "Dad" - defaultMode=formalize, temperature=0.3
- Instance 2: "Kids" - defaultMode=casual, temperature=0.8, custom="make it fun with emoji"

**Rule**:

```
Trigger: Bedtime (9 PM)
Actions:
  1. Call Dad's instance: "It is time to prepare for sleep"
     → "It is now 9:00 PM. Please begin your evening routine."

  2. Call Kids' instance: "It is time to prepare for sleep"
     → "🌙 Hey kiddos! It's bedtime! Time to brush teeth and get cozy! 😴"
```

### Example 4: Device Status Summarization

Convert technical device states into human-friendly summaries.

**WebCoRE Piston**:

```
When: Button pressed (or scheduled time)
Then:
  Set statusList = ""
  For each door sensor:
    If open: Append "{device.label} is open"
  For each window:
    If open: Append "{device.label} is open"
  For each lock:
    If unlocked: Append "{device.label} is unlocked"

  HTTP POST rewrite endpoint:
    text: {statusList}
    mode: lengthen

  Wait 3 seconds
  Announce on speakers: %geminiRewrittenText%
```

**Input**:

```
Front door open. Kitchen window open. Back door unlocked.
```

**Output**:

```
"Your home security check shows the following: The front door is currently open, the kitchen window is open allowing ventilation, and the back door is unlocked. You may want to secure these access points before leaving."
```

### Example 5: Professional Office Announcements

Format home automation for business environments.

**Scenario**: Conference room automation

**Rule**:

```
Trigger: Motion in conference room + Time between 8 AM - 6 PM
Actions:
  1. HTTP POST rewrite endpoint:
     text="Conference room occupied. Temperature is [%device.temperature%] degrees."
     mode=formalize
  2. Delay 2 seconds
  3. Update dashboard tile: %geminiRewrittenText%
```

**Result**:

```
"The conference room is currently occupied. The ambient temperature is 72 degrees Fahrenheit, which is within the comfortable range."
```

### Example 6: Batch Processing Multiple Messages

Process multiple alerts in sequence.

**Node-RED Flow**:

```javascript
// Input: Array of messages
let messages = ["Door opened", "Motion detected", "Temperature high"];

// Function node
let results = [];
for (let msg of messages) {
  // Make HTTP POST to rewriteText endpoint
  // Collect results
}

// Output combined rewritten messages
```

### Example 7: Conditional Rewriting Based on Context

Apply different modes based on time of day or location.

**Rule Machine Rule**:

```
Trigger: Sensor event
Actions:
  IF Time is Night Mode (9 PM - 7 AM)
    Set rewriteMode = "shorten"  // Brief night alerts
  ELSE IF Location is Home
    Set rewriteMode = "casual"   // Friendly during day
  ELSE
    Set rewriteMode = "formalize" // Professional when away
  END IF

  HTTP GET rewriteText:
    text=[alert message]
    mode=%rewriteMode%

  Notify: %geminiRewrittenText%
```

---

## Troubleshooting

### Common Issues and Solutions

#### Issue 1: "OAuth is not enabled for app"

**Symptom**: Red warning message on configuration page

**Solution**:

1. Navigate to **Apps Code**
2. Select **Gemini Text Rewriter**
3. Find **OAuth** section in top-right of code editor
4. Click **"Enable OAuth in App"**
5. Click **Update**
6. Return to **Apps** and reload configuration page

---

#### Issue 2: API Returns 401/403 Errors

**Symptom**: Logs show "HTTP request failed: 401" or "403 Forbidden"

**Causes**:

- Invalid or expired API key
- API key doesn't have access to selected model
- Google Cloud project issues

**Solutions**:

1. **Verify API Key**:
   - Visit [Google AI Studio](https://aistudio.google.com/app/apikey)
   - Regenerate API key if needed
   - Copy and paste carefully (no extra spaces)

2. **Check Model Access**:
   - Verify your Google Cloud project has Gemini API enabled
   - Try switching to `gemini-2.5-flash` (most widely available)
   - Check [Google Cloud Console](https://console.cloud.google.com/) for API status

3. **Verify Quota**:
   - Check API quotas in Google Cloud Console
   - Free tier has generous limits but can be exceeded
   - Consider upgrading to paid tier if hitting limits

---

#### Issue 3: API Returns 404 Errors

**Symptom**: "404 Not Found" when calling API

**Causes**:

- Selected model doesn't exist or isn't available in your region
- Typo in model name

**Solutions**:

1. Switch to a stable, widely-available model:
   - Recommended: `gemini-2.5-flash`
   - Alternative: `gemini-2.0-flash`

2. Check Google AI Studio for available models in your region

3. Verify model name spelling in app configuration

---

#### Issue 4: Rewritten Text is Too Short/Long

**Symptom**: Output doesn't match expected length

**Solutions**:

1. **Adjust Maximum Output Tokens**:
   - Too short? Increase from 1000 to 2000+
   - Too long? Decrease to 200-500

2. **Try Different Mode**:
   - Want shorter? Use `shorten` mode explicitly
   - Want longer? Use `lengthen` mode

3. **Adjust Custom Prompt**:
   - Add length guidance: "Rewrite in 2-3 sentences"
   - Be specific: "Expand to approximately 100 words"

---

#### Issue 5: Output is Too Creative/Unpredictable

**Symptom**: AI generates unexpected or off-topic rewrites

**Solutions**:

1. **Lower Temperature**:
   - Change from 0.7 to 0.3 or lower
   - Produces more deterministic, focused outputs

2. **Use More Specific Mode**:
   - Instead of "custom", use predefined modes
   - Add constraints to custom prompt: "Rewrite exactly this message but fix grammar only"

3. **Simplify Input**:
   - Break complex messages into smaller chunks
   - Rewrite one sentence at a time

---

#### Issue 6: HTTP Endpoint Not Responding

**Symptom**: Webhook calls timeout or return no data

**Causes**:

- Incorrect URL or access token
- Hub networking issues
- App not fully initialized

**Solutions**:

1. **Verify Endpoint URL**:
   - Copy exact URL from app configuration page
   - Ensure no extra characters or spaces
   - Verify access token is included

2. **Test from Hub's Network**:

   ```bash
   curl "http://[hub-ip]/apps/api/[app-id]/lastResult?access_token=[token]"
   ```

3. **Check Hub Connectivity**:
   - Verify hub has internet access
   - Test other HTTP-based apps

4. **Reinitialize App**:
   - Click **Initialize** button in app settings
   - Check logs for error messages

---

#### Issue 7: Global Variable Not Updating

**Symptom**: `%geminiRewrittenText%` shows old or empty value in Rule Machine

**Solutions**:

1. **Verify Setting Enabled**:
   - Check "Store Result in Global Variable" is ON
   - Confirm variable name matches what you're using

2. **Check Variable in Hub**:
   - Navigate to **Settings** → **Hub Variables** (if available)
   - Or check via logs after rewrite operation

3. **Add Delay**:
   - API calls take 1-3 seconds
   - Add 2-3 second delay before reading variable

4. **Test Directly**:
   - Use test interface in app to trigger rewrite
   - Verify "Last Result" section shows text
   - If that works, issue is in your rule logic

---

#### Issue 8: App Not Appearing in Apps List

**Symptom**: Can't find "Gemini Text Rewriter" in Add User App menu

**Solutions**:

1. **Verify Installation**:
   - Navigate to **Apps Code**
   - Confirm "Gemini Text Rewriter" is present
   - Click **Save** to refresh

2. **Check Dependencies**:
   - Ensure "DWinks Utilities and Logging Library" is installed in Apps Code
   - Resave both library and app

3. **Refresh Browser**:
   - Hard refresh (Ctrl+F5 or Cmd+Shift+R)
   - Clear browser cache
   - Try different browser

---

#### Issue 9: Slow Response Times

**Symptom**: API calls take >10 seconds

**Causes**:

- Network latency to Google servers
- Complex prompts or long input text
- Model selection (Pro models slower than Flash)

**Solutions**:

1. **Switch to Faster Model**:
   - Use `gemini-2.5-flash-lite` (fastest)
   - Or `gemini-2.5-flash` (balanced)

2. **Reduce Token Limits**:
   - Lower max output tokens to 500 or less
   - Forces quicker generation

3. **Shorten Input Text**:
   - Break long messages into smaller chunks
   - Process separately and combine

4. **Check Network**:
   - Test hub's internet speed
   - Consider wired connection if on WiFi

---

#### Issue 10: Formatting Not Preserved

**Symptom**: Line breaks and paragraphs removed in output

**Solutions**:

1. **Enable Setting**:
   - Turn ON "Preserve Line Breaks" in app config

2. **Mode-Specific**:
   - Some modes (like `shorten`) may ignore formatting by design
   - Try `improve` mode which respects structure better

3. **Custom Prompt**:
   - Add explicit instruction: "Maintain all line breaks and paragraph spacing exactly as provided"

---

### Debugging Tips

**Enable Debug Logging**:

```
1. Turn ON "Enable Debug Logging" in app settings
2. Trigger the rewrite operation
3. Check Logs immediately
4. Look for:
   - API URL (verify endpoint and model)
   - Request body (verify text and mode)
   - Response status (200 = success)
   - Response data (verify AI output)
```

**Check API Response Structure**:

```
Debug logs show: "API Response Status: 200"
If you see 400/401/403/404/500, there's an API-level issue
If you see 200 but no text, response parsing issue
```

**Test Isolation**:

```
1. Test via app's built-in Test interface first
   - If this works, app is functioning correctly
   - Issue is in your external integration

2. Test via curl/browser next
   - Confirms OAuth and network connectivity

3. Finally test via Rule Machine/WebCoRE
   - Isolates rule logic issues
```

---

## Performance and Cost Considerations

### Google Gemini API Pricing

**Free Tier** (as of Jan 2026):

- **Gemini 2.5 Flash**: 15 RPM (requests per minute), 1 million TPM (tokens per minute)
- **Gemini 2.5 Pro**: 2 RPM, 32,000 TPM
- **Rate Limits**: Generous for typical home automation usage

**Paid Tier**:

- Pay-per-token pricing
- Rates vary by model (Flash < Pro < Advanced)
- See [Google AI Pricing](https://ai.google.dev/pricing) for current rates

**Typical Usage Estimate**:

```
Scenario: 50 notifications per day, avg 20 words each

Input tokens:  50 * 20 * 1.33 = ~1,300 tokens/day
Output tokens: 50 * 30 * 1.33 = ~2,000 tokens/day
Total:         ~3,300 tokens/day = ~100,000 tokens/month

At $0.01 per 1,000 tokens (hypothetical):
Monthly cost: ~$1.00
```

**Cost Optimization Tips**:

1. Use `gemini-2.5-flash-lite` for simple rewrites (cheapest)
2. Lower `maxTokens` to minimum needed
3. Cache frequently rewritten phrases locally (custom implementation)
4. Batch rewrites when possible (process multiple messages per API call)
5. Use `shorten` mode to reduce output tokens

### Performance Characteristics

**Typical Response Times**:

- **gemini-2.5-flash-lite**: 0.5-1.5 seconds
- **gemini-2.5-flash**: 1-2 seconds
- **gemini-2.5-pro**: 2-4 seconds
- **gemini-3-pro**: 3-6 seconds

**Factors Affecting Speed**:

- Input text length (longer = slower)
- Output token limit (higher = slower)
- Temperature (higher = slightly slower)
- Network latency (hub to Google servers)
- Current API load (Google's infrastructure)

**Optimization Strategies**:

1. **Use Fastest Model**: gemini-2.5-flash-lite for low-latency needs
2. **Reduce Token Limits**: Set maxTokens to realistic minimum
3. **Parallel Processing**: Process multiple independent requests concurrently
4. **Local Caching**: Store frequently used rewrites in app state (custom implementation)
5. **Async Patterns**: Use location events for non-blocking operation

### Hub Resource Usage

**Memory**:

- App footprint: ~50-100 KB
- With history (20 entries): +10-20 KB
- Global variable storage: ~1-5 KB per result
- **Total**: Negligible on Hubitat C-7/C-8 hubs

**CPU**:

- HTTP request processing: Minimal (<1% CPU)
- JSON parsing: Negligible
- No continuous polling or background tasks

**Network**:

- Per request: ~1-5 KB sent, ~2-10 KB received
- Typical: <1 MB per day even with heavy usage

**Recommendation**: Resource usage is minimal; safe for production use

---

## Best Practices

### Configuration Best Practices

1. **Start with Defaults**:
   - Use `gemini-2.5-flash` model
   - Temperature: 0.7
   - Max tokens: 1000
   - Adjust only if needed

2. **Test Before Deployment**:
   - Use built-in test interface extensively
   - Try edge cases (very short, very long, special characters)
   - Verify behavior matches expectations

3. **Enable Logging Initially**:
   - Turn on logging during setup and testing
   - Review logs to understand behavior
   - Disable or let auto-disable after 30 minutes in production

4. **Use Descriptive App Labels**:
   - Name instances by use case: "Gemini - Security Alerts", "Gemini - TTS Announcements"
   - Helps identify which instance to call in rules

5. **Secure Your Tokens**:
   - Don't share access tokens publicly
   - Regenerate OAuth if compromised (disable/enable OAuth)
   - Use HTTPS when accessing from external networks

### Integration Best Practices

1. **Add Delays in Rules**:
   - Always add 2-3 second delay between API call and reading result
   - Accounts for network latency and processing time

2. **Handle Failures Gracefully**:
   - Provide fallback text in location events
   - Check for errors before using results
   - Log failures for debugging

3. **Use Appropriate Modes**:
   - Don't use `lengthen` for mobile notifications (too verbose)
   - Don't use `shorten` for detailed alerts (loses context)
   - Match mode to output medium

4. **Batch When Possible**:
   - Instead of rewriting every sensor individually, combine into one message
   - Reduces API calls and improves consistency

5. **Cache Common Phrases**:
   - For repeated identical messages, consider caching results manually
   - Example: "Motion detected" always rewrites the same way at low temperature

### Production Deployment Best Practices

1. **Monitor API Usage**:
   - Check Google Cloud Console for quota usage
   - Watch for unexpected spikes
   - Set up billing alerts if using paid tier

2. **Disable Unnecessary Features**:
   - Turn off history if not needed
   - Disable global variable storage if not used
   - Reduces memory footprint

3. **Use Custom Prompts Carefully**:
   - Keep prompts concise and clear
   - Avoid overly complex instructions
   - Test thoroughly before production use

4. **Plan for API Failures**:
   - Google services can have outages
   - Have fallback logic in critical automations
   - Don't rely solely on rewritten text for safety-critical alerts

5. **Version Control Your Rules**:
   - Document which rules use Gemini endpoints
   - Keep backup copies of working rule configurations
   - Update rules if app instance changes

### Security Best Practices

1. **Protect OAuth Tokens**:
   - Don't commit tokens to git repositories
   - Don't share in public forums
   - Rotate tokens periodically

2. **Secure API Keys**:
   - Never log API keys (app masks them automatically)
   - Don't share Google Cloud project access
   - Use least-privilege service accounts if possible

3. **Network Security**:
   - Use local endpoints when possible (stay on LAN)
   - If exposing externally, use VPN or reverse proxy
   - Consider firewall rules limiting access

4. **Content Filtering**:
   - Be aware AI may occasionally produce unexpected output
   - Review results in test environment before production
   - Avoid rewriting sensitive personal information

---

## FAQ

### General Questions

**Q: Do I need a Google Cloud account?**
A: Yes, but the free tier is sufficient for typical home automation usage. No credit card required for API key generation.

**Q: Does this work offline?**
A: No, the app requires internet connectivity to reach Google's Gemini API servers.

**Q: Can I use multiple instances of this app?**
A: Yes! Create multiple instances with different configurations (e.g., one for casual, one for formal).

**Q: Will this slow down my automations?**
A: API calls add 1-4 seconds latency depending on model and configuration. Use async patterns (location events) for non-critical paths.

**Q: What happens if Google's API is down?**
A: The app will return error responses. Use fallback text in location event integration to handle failures gracefully.

### Technical Questions

**Q: Can I change the system prompts for predefined modes?**
A: Not directly through UI, but you can use "custom" mode with your own prompts, or modify app code if comfortable with Groovy.

**Q: How do I process multiple messages at once?**
A: Call the API endpoint multiple times, or combine messages into one text string separated by delimiters, then parse the result.

**Q: Can I get results in languages other than English?**
A: Yes! Gemini supports many languages. Input text in the target language or use custom prompt like "Translate to Spanish and formalize".

**Q: Does temperature affect cost?**
A: No, temperature doesn't affect per-request cost. It only affects output creativity/randomness.

**Q: How many API calls can I make per minute?**
A: Free tier: 15 RPM for Flash models, 2 RPM for Pro models. Paid tier has higher limits. See Google's documentation for current rates.

**Q: Can I see the raw API request/response?**
A: Yes, enable debug logging and check Hubitat logs immediately after making a request.

### Integration Questions

**Q: How do I use this with Alexa/Google Home?**
A: Rewrite the text first, store in global variable, then use Hubitat's Alexa/Google integrations to speak the variable value.

**Q: Can I trigger rewrites from IFTTT?**
A: Yes, use IFTTT's webhook service to POST to the app's `/rewrite` endpoint.

**Q: Does this work with SharpTools?**
A: Yes, SharpTools can execute HTTP requests and access Hubitat variables, enabling full integration.

**Q: Can other Hubitat apps call this directly?**
A: Yes, use location events (geminiRewriteRequest/Response) for app-to-app communication without HTTP overhead.

**Q: How do I use this with Maker API?**
A: Maker API can trigger rules that call Gemini endpoints, or you can call Gemini's endpoints directly from external systems.

### Troubleshooting Questions

**Q: Why does the result show old text?**
A: Add a delay between API call and reading the result. API calls are asynchronous and take 1-3 seconds.

**Q: Why is output inconsistent?**
A: High temperature setting creates randomness. Lower temperature to 0.3 or below for consistent results.

**Q: Why do I get "No text returned from Gemini API"?**
A: Usually caused by content filtering or API errors. Check debug logs for details; try simpler input text.

**Q: Why does it say "OAuth is not enabled"?**
A: OAuth must be enabled in Apps Code editor. See [Initial Setup](#initial-setup) for instructions.

**Q: Can I see what prompts are being sent to Gemini?**
A: Yes, enable debug logging. Logs will show the full system prompt and user text.

---

## Technical Architecture

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Hubitat Ecosystem                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Rule Machine │  │   WebCoRE    │  │  Other Apps  │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
│         │ HTTP / Events    │ HTTP / Events    │ Events       │
│         ▼                  ▼                  ▼              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │        Gemini Text Rewriter App                      │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │   │
│  │  │HTTP Endpoints│  │Event Handler │  │State Mgmt  │ │   │
│  │  └──────┬───────┘  └──────┬───────┘  └─────┬──────┘ │   │
│  │         │                  │                 │        │   │
│  │         └──────────┬───────┴────────┬────────┘        │   │
│  │                    │Core Rewriter   │                 │   │
│  │                    │    Logic       │                 │   │
│  │                    └───────┬────────┘                 │   │
│  └────────────────────────────┼──────────────────────────┘   │
│                                │ HTTPS                        │
└────────────────────────────────┼──────────────────────────────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │   Google Gemini API    │
                    │  (Cloud Service)       │
                    └────────────────────────┘
```

### Data Flow

**Typical Request Flow**:

1. **Trigger**: Rule Machine rule executes HTTP GET/POST
2. **Webhook Handler**: App receives request, validates access token
3. **Parameter Extraction**: Parse text and mode from request
4. **Prompt Building**: Construct system prompt based on mode
5. **API Request**: Build JSON request body with configuration
6. **HTTP Call**: POST to Gemini API with authentication
7. **Response Parsing**: Extract generated text from API response
8. **State Storage**: Save result to app state
9. **Variable Update**: Store in global connector variable if enabled
10. **Event Broadcast**: Send location event for subscribers
11. **HTTP Response**: Return JSON or plain text to caller
12. **History**: Add to request history if enabled

### State Management

**App State Variables**:

- `state.accessToken`: OAuth token for webhook authentication
- `state.lastResult`: Most recent rewritten text
- `state.lastOriginal`: Original input text of last request
- `state.lastMode`: Mode used for last request
- `state.lastTimestamp`: Timestamp of last successful rewrite
- `state.lastError`: Most recent error message (if any)
- `state.history`: Array of recent request records (if enabled)

**Settings Variables**:

- `settings.geminiApiKey`: Google Gemini API key
- `settings.geminiModel`: Selected Gemini model identifier
- `settings.maxTokens`: Maximum output tokens limit
- `settings.temperature`: Creativity/randomness setting
- `settings.defaultMode`: Default rewriting mode
- `settings.customSystemPrompt`: User-defined custom prompt
- `settings.preserveFormatting`: Boolean for line break preservation
- `settings.storeInGlobalVar`: Boolean to enable variable storage
- `settings.globalVarName`: Name of connector variable
- `settings.keepHistory`: Boolean to enable history tracking
- `settings.logEnable`: Boolean for info logging
- `settings.debugLogEnable`: Boolean for debug logging
- `settings.descriptionTextEnable`: Boolean for description text

### API Request Structure

**Gemini API Request Format**:

```json
{
  "contents": [
    {
      "parts": [
        {
          "text": "[SYSTEM PROMPT]\n\nText to rewrite:\n[USER TEXT]"
        }
      ]
    }
  ],
  "generationConfig": {
    "temperature": 0.7,
    "maxOutputTokens": 1000,
    "topP": 0.95,
    "topK": 40
  }
}
```

**Gemini API Response Format**:

```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "[REWRITTEN TEXT]"
          }
        ],
        "role": "model"
      },
      "finishReason": "STOP",
      "index": 0,
      "safetyRatings": [...]
    }
  ],
  "usageMetadata": {
    "promptTokenCount": 50,
    "candidatesTokenCount": 30,
    "totalTokenCount": 80
  }
}
```

### Security Model

**OAuth Token Generation**:

- Automatically generated when OAuth is enabled
- Unique per app instance
- Used to authenticate all HTTP endpoint requests
- Stored in `state.accessToken`

**API Key Protection**:

- Stored in app settings (not state)
- Never logged in plain text (masked in debug logs)
- Transmitted only over HTTPS to Google servers
- Not accessible via webhooks or events

**Access Control**:

- HTTP endpoints require valid OAuth token
- Location events are hub-local only
- No authentication bypass mechanisms

---

## API Reference

### Location Events

#### Event: geminiRewriteRequest

**Purpose**: Request text rewriting from another app without HTTP

**Event Structure**:

```groovy
sendLocationEvent(
  name: 'geminiRewriteRequest',
  value: '[text to rewrite]',
  data: JsonOutput.toJson([
    mode: 'improve',              // Optional: rewriting mode
    requestId: 'unique-id-123',   // Optional: correlation ID
    fallbackText: 'original text' // Optional: used if rewrite fails
  ])
)
```

**Parameters**:

- `name` (required): Must be exactly `'geminiRewriteRequest'`
- `value` (required): String - The text to rewrite
- `data` (optional): JSON string with additional parameters
  - `mode`: Rewriting mode (defaults to app's defaultMode)
  - `requestId`: Unique identifier for tracking (auto-generated if omitted)
  - `fallbackText`: Text to return if rewrite fails (defaults to original)

#### Event: geminiRewriteResponse

**Purpose**: Response sent after processing rewrite request

**Event Structure**:

```groovy
// Received via subscription
subscribe(location, 'geminiRewriteResponse', 'handlerMethod')

void handlerMethod(evt) {
  String rewrittenText = evt.value
  Map data = parseJson(evt.data)
  // data contains: requestId, success, rewritten, error, mode
}
```

**Response Data**:

- `evt.value`: The rewritten text (or fallback if failed)
- `evt.data`: JSON string containing:
  - `requestId`: Original request ID
  - `success`: Boolean - true if rewrite succeeded
  - `rewritten`: The rewritten text
  - `error`: Error message if success=false
  - `mode`: Mode that was used

#### Event: geminiTextRewritten

**Purpose**: Broadcast event sent on every successful rewrite

**Event Structure**:

```groovy
// Automatically sent by app after successful rewrite
// Subscribe to be notified of all rewrites
subscribe(location, 'geminiTextRewritten', 'handlerMethod')

void handlerMethod(evt) {
  String rewrittenText = evt.value
  Map data = parseJson(evt.data)
  // data contains: success, original, rewritten, mode, timestamp
}
```

**Broadcast Data**:

- `evt.value`: The rewritten text
- `evt.data`: JSON string containing:
  - `success`: Always true for this event
  - `original`: Original input text
  - `rewritten`: The rewritten text
  - `mode`: Mode used
  - `timestamp`: When rewrite occurred (yyyy-MM-dd HH:mm:ss)

### Global Variables

#### Variable: geminiRewrittenText (default name)

**Purpose**: Stores most recent rewritten text for Rule Machine access

**Configuration**:

- Enabled via "Store Result in Global Variable" setting
- Name customizable via "Global Variable Name" setting

**Access in Rule Machine**:

```
Variable: %geminiRewrittenText%
```

**Access in WebCoRE**:

```
Global Variable: @geminiRewrittenText
```

**Access Programmatically**:

```groovy
String rewrittenText = getGlobalVar('geminiRewrittenText')
```

### App Methods (Internal)

These methods are used internally but documented for developers who may fork/extend the app.

#### rewriteText(String text, String mode)

**Purpose**: Core method that performs text rewriting

**Parameters**:

- `text`: String to rewrite (required)
- `mode`: Rewriting mode (optional, defaults to settings.defaultMode)

**Returns**: Map

```groovy
[
  success: true/false,
  text: 'rewritten text',  // if success
  error: 'error message'   // if failure
]
```

**Example**:

```groovy
Map result = rewriteText('door opened', 'improve')
if (result.success) {
  log.info("Result: ${result.text}")
}
```

#### buildSystemPrompt(String mode)

**Purpose**: Constructs AI instructions based on mode

**Parameters**:

- `mode`: One of: improve, shorten, lengthen, formalize, casual, simplify, custom

**Returns**: String - The system prompt to send to Gemini

**Example**:

```groovy
String prompt = buildSystemPrompt('improve')
// Returns: "You are an expert editor. Improve the grammar..."
```

#### makeGeminiApiCall(String apiUrl, Map requestBody)

**Purpose**: Executes HTTP POST to Gemini API

**Parameters**:

- `apiUrl`: Full API endpoint URL with key
- `requestBody`: Map containing request structure

**Returns**: Map

```groovy
[
  success: true/false,
  data: [API response data],  // if success
  error: 'error message'      // if failure
]
```

#### extractTextFromResponse(def responseData)

**Purpose**: Parses Gemini API response to extract generated text

**Parameters**:

- `responseData`: Raw API response object

**Returns**: String - Extracted text, or null if not found

---

## Version History

### Version 1.0.0 (2026-01-31)

- Initial release
- Six predefined rewriting modes (improve, shorten, lengthen, formalize, casual, simplify)
- Custom mode with user-defined system prompts
- Support for all current Gemini models (3 Pro, 3 Flash, 2.5 Pro, 2.5 Flash, 2.5 Flash-Lite, 2.0 Flash)
- HTTP API endpoints with JSON and plain text responses
- Location event system for cross-app communication
- Global variable storage for Rule Machine integration
- Request history tracking (optional)
- Built-in testing interface
- Configurable temperature and token limits
- OAuth-secured webhooks
- Comprehensive error handling
- Auto-disabling logs
- Format preservation option

---

## Credits and License

### Author

**Daniel Winks**

- Email: daniel.winks@gmail.com
- GitHub: [DanielWinks/Hubitat-Public](https://github.com/DanielWinks/Hubitat-Public)
- Hubitat Community: @dwinks

### Dependencies

- **DWinks Utilities and Logging Library**: Required for core functionality
  - Provides logging methods: `logDebug()`, `logInfo()`, `logWarn()`, `logError()`
  - Provides utility methods: `tryCreateAccessToken()`, `setGlobalVar()`
  - Provides lifecycle methods: `installed()`, `updated()`, `uninstalled()`

### Powered By

- **Google Gemini AI**: Advanced language model for text transformation
- **Hubitat Elevation**: Local home automation platform

### License

**MIT License**

Copyright 2026 Daniel Winks

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

---

## Support and Contributions

### Getting Help

**Community Forums**:

- Post questions on [Hubitat Community Forums](https://community.hubitat.com/)
- Tag @dwinks for author attention
- Search for "Gemini Text Rewriter" for existing discussions

**GitHub Issues**:

- Report bugs: [GitHub Issues](https://github.com/DanielWinks/Hubitat-Public/issues)
- Feature requests: [GitHub Discussions](https://github.com/DanielWinks/Hubitat-Public/discussions)
- Check existing issues before posting

**Direct Contact**:

- Email: daniel.winks@gmail.com (for private inquiries only)
- Please use community forums for general questions

### Contributing

Contributions are welcome! If you'd like to improve this app:

1. Fork the repository on GitHub
2. Create a feature branch
3. Make your changes with clear commit messages
4. Test thoroughly on your Hubitat hub
5. Submit a pull request with description of changes

**Areas for Contribution**:

- Additional rewriting modes
- UI/UX improvements
- Performance optimizations
- Documentation enhancements
- Bug fixes
- Example integrations

### Donations

If you find this app useful, consider supporting development:

**PayPal**: [paypal.me/winksd](https://paypal.me/winksd?country.x=US&locale.x=en_US)

All donations are appreciated but never required. This software is free and open source.

---

## Changelog

See [Version History](#version-history) for detailed release notes.

---

**End of Documentation**

_Last Updated: January 31, 2026_
_Document Version: 1.0.0_
