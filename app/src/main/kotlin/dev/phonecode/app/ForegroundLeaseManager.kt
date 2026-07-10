package dev.phonecode.app

class ForegroundLeaseManager(
    private val start: () -> Unit,
    private val stop: () -> Unit,
) {
    private val owners = mutableSetOf<String>()

    @Synchronized
    fun acquire(owner: String) {
        if (owners.add(owner) && owners.size == 1) start()
    }

    @Synchronized
    fun release(owner: String) {
        if (owners.remove(owner) && owners.isEmpty()) stop()
    }
}
