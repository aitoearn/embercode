# PhoneCode Privacy Policy

_Last updated: 13 July 2026_

This Privacy Policy explains how PhoneCode handles data. PhoneCode is an on-device AI coding client published by Deyan Todorov. The developer does not operate a general-purpose PhoneCode backend. A narrowly scoped reporting endpoint receives AI-output reports that you deliberately submit in the app.

## 1. Data PhoneCode keeps on your device

PhoneCode stores the following locally so the app can work:

- API keys, Git credentials, and third-party sign-in tokens, encrypted with the Android Keystore. If secure storage is unavailable, PhoneCode does not save credentials.
- Projects, workspace files, linked-folder references, chat history, provider configuration, and app settings.
- A bundled Alpine Linux environment and packages installed by you or the agent.

Android cloud backup is disabled for PhoneCode. You can create a manual export of supported chats and settings. Manual exports are not encrypted, so store and share them carefully. They do not contain API keys or sign-in credentials.

## 2. Files and photos you choose

PhoneCode can access files or photos only after you select them through Android's system picker. If you link a folder, Android grants PhoneCode continuing access to that folder until you unlink it, revoke access in system settings, clear the app's data, or uninstall the app. The agent may read, create, change, rename, or delete content within a linked folder according to your permission setting and instructions.

Files remain on your device unless you ask the agent to send relevant content to an AI provider, Git host, search service, or another destination. Review linked-folder access and agent permission settings before allowing automatic changes.

## 3. Data sent to services you choose

PhoneCode does not contain an AI model. When you use the agent, the app sends your prompt and the context needed for the request directly from your device to the AI provider you selected. This may include text, source code, attached photos or files, tool results, and content the agent reads from a workspace or linked folder.

You may connect services such as OpenAI or ChatGPT, Anthropic, OpenRouter, OpenCode Zen or Go, Google, xAI, DeepSeek, Mistral, a custom provider, or a Git host. PhoneCode sends credentials only to authenticate with the service they belong to. The selected service's privacy policy, retention rules, and terms apply once it receives data. The developer cannot access or delete data held by those services.

These transfers are optional and initiated by features you choose. Do not send confidential, personal, or regulated information unless you are authorized to do so and accept the receiving service's practices.

## 4. Other network requests

Depending on the features you use, PhoneCode may connect directly to:

- Git hosts to clone, fetch, pull, or push repositories.
- DuckDuckGo or a model provider's search service for searches requested by you or the agent.
- models.dev to refresh public provider and model metadata. Prompts and workspace files are not included in this request.
- Alpine Linux repositories and package sources used by `apk`, `pip`, `npm`, or other tools when you or the agent installs software.
- MCP servers or custom endpoints you configure.
- dttdrv.xyz when you deliberately submit an AI-output report.

The receiving service can see normal network information such as your IP address and request metadata. Its own privacy policy applies.

## 5. Data the developer collects

PhoneCode contains no advertising SDK, analytics, telemetry, or remote crash-reporting service. The developer does not sell personal data from the app.

If you choose Report AI response and tap Send, PhoneCode sends the selected category, an optional note you write, the app version, and the platform to dttdrv.xyz. It never attaches the AI response, your prompt, files, credentials, tool activity, chat history, provider or model identifiers, or a device identifier. Reports are stored in a private Cloudflare D1 database and are used to investigate harmful output and improve filtering and moderation. Reports older than 90 days are deleted during subsequent report processing.

To limit abuse, the reporting endpoint converts the connecting IP address into a salted hash that changes daily and stores that hash with a request counter and timestamps. It does not store the raw IP address. Rate-limit records older than 48 hours are deleted during subsequent report processing. Cloudflare processes ordinary network and edge request metadata as the hosting provider under its own terms and privacy practices.

## 6. Security

Credentials are stored using Android Keystore-backed encryption and are excluded from manual exports. Network connections use HTTPS where supported by the service. Custom endpoints and software installed in the local development environment are under your control and may have different security properties. PRoot provides Linux compatibility, not a VM or security sandbox. Installed software can access the workspace and runtime directories mounted into that environment and can use PhoneCode's network access. No security measure is perfect; protect your device and revoke provider credentials if you believe they were exposed.

## 7. Retention and deletion

Local data remains until you delete it in PhoneCode, clear the app's storage, or uninstall the app. Unlinking a folder removes PhoneCode's saved access but does not delete that folder. Uninstalling PhoneCode does not delete remote repositories, provider records, third-party accounts, or a report you already submitted.

A successful report displays a reference you can include in an early-deletion or other privacy request to the developer.

To access or delete data held by an AI provider, Git host, search service, package source, or MCP server, use that service's account and privacy controls. PhoneCode has no account of its own, so there is no PhoneCode account to delete.

## 8. Children

PhoneCode is not directed to children under 13. Users must also meet the age requirements of every third-party service they connect.

## 9. Changes

This policy may change as PhoneCode changes. The current policy is included in the app and will show a new date when updated.

## 10. Contact

For privacy questions, use the contact form at [dttdrv.xyz](https://dttdrv.xyz/#contact). Product information is available at [dttdrv.xyz/phonecode](https://dttdrv.xyz/phonecode), and source code is available at [github.com/dttdrv/phonecode](https://github.com/dttdrv/phonecode).
