package dev.phonecode.app.data

import kotlinx.serialization.json.Json

/**
 * One Json config for every on-device store, so the disk format stays consistent and nothing drifts.
 * ignoreUnknownKeys = forward-compatible reads (a newer field never breaks an older build);
 * encodeDefaults = default-valued fields are written (stable, self-describing files);
 * isLenient = tolerant parsing of slightly-off hand-edited files (e.g. providers.json the agent edits).
 */
internal val storeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}
