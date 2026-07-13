package dev.phonecode.app

class ForegroundLeaseManager(
    private val start: () -> Unit,
    private val stop: () -> Unit,
) {
    private val owners = mutableSetOf<String>()
    private val stopHandlers = mutableMapOf<String, () -> Unit>()

    @Synchronized
    fun acquire(owner: String) {
        if (!owners.add(owner) || owners.size != 1) return
        try {
            start()
        } catch (error: Throwable) {
            owners.remove(owner)
            throw error
        }
    }

    @Synchronized
    fun release(owner: String) {
        if (owners.remove(owner) && owners.isEmpty()) stop()
    }

    @Synchronized
    fun registerStopHandler(owner: String, handler: () -> Unit) {
        stopHandlers[owner] = handler
    }

    @Synchronized
    fun unregisterStopHandler(owner: String) {
        stopHandlers.remove(owner)
    }

    fun stopAll() {
        val handlers: List<() -> Unit>
        val wasActive: Boolean
        synchronized(this) {
            handlers = stopHandlers.values.toList()
            wasActive = owners.isNotEmpty()
            owners.clear()
        }
        handlers.forEach { runCatching(it) }
        if (wasActive) stop()
    }
}
