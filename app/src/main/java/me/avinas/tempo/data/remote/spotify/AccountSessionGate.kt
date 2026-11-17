package me.avinas.tempo.data.remote.spotify

/** Short storage/state commits share a lock; network requests never hold it. */
internal class AccountSessionGate<C>(private val cancel: (C) -> Unit) {
    private val lock = Any()
    private var generation = 0L
    private val calls = mutableSetOf<C>()

    fun <T> read(action: (Long) -> T): T = synchronized(lock) { action(generation) }

    fun <T> ifCurrent(expected: Long, action: () -> T): T? = synchronized(lock) {
        if (expected == generation) action() else null
    }

    /** Invalidate first, apply local state atomically, then cancel old I/O without waiting for it. */
    fun <T> replace(action: () -> T): T {
        var obsolete = emptyList<C>()
        try {
            return synchronized(lock) {
                generation++
                obsolete = calls.toList()
                calls.clear()
                action()
            }
        } finally {
            obsolete.forEach { runCatching { cancel(it) } }
        }
    }

    fun register(expected: Long, call: C): Boolean {
        val accepted = synchronized(lock) {
            if (expected == generation) { calls += call; true } else false
        }
        if (!accepted) cancel(call)
        return accepted
    }

    fun finish(call: C) { synchronized(lock) { calls -= call } }
}
