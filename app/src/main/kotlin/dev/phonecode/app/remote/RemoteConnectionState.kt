package dev.phonecode.app.remote

/** 远程 host 连接态（摘要缓存用；权威状态在 RN / paseo client）。 */
enum class RemoteConnectionState { Connected, Disconnected, Unknown }
