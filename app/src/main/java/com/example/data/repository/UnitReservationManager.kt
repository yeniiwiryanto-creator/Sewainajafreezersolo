package com.example.data.repository

/**
 * Temporary timed reservation manager for freezer units while a transaction form is in progress.
 *
 * Guarantees:
 * 1. Selecting a unit in an active transaction form session reserves it temporarily with a TTL.
 * 2. Another concurrent form session cannot select or save an actively reserved unit.
 * 3. Cancelling/closing the form immediately releases all reservations for that session.
 * 4. Saving a transaction converts the unit to rented status in SQLite and releases the reservation.
 * 5. Stale reservations expire automatically once TTL elapses.
 */
object UnitReservationManager {

    const val DEFAULT_TTL_MILLIS: Long = 15L * 60L * 1000L // 15 minutes

    data class UnitReservation(
        val unitId: Long,
        val sessionId: String,
        val reservedAtMillis: Long,
        val expiresAtMillis: Long
    )

    data class ReservationSyncResult(
        val reservedUnitIds: Set<Long>,
        val rejectedUnitIds: Set<Long>
    )

    private val lock = Any()
    private val reservationsByUnitId = LinkedHashMap<Long, UnitReservation>()

    fun clearExpired(nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            clearExpiredLocked(nowMillis)
        }
    }

    private fun clearExpiredLocked(nowMillis: Long) {
        val iterator = reservationsByUnitId.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.expiresAtMillis <= nowMillis) {
                iterator.remove()
            }
        }
    }

    fun reserveUnit(
        sessionId: String,
        unitId: Long,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (sessionId.isBlank() || unitId <= 0L) return false
        synchronized(lock) {
            clearExpiredLocked(nowMillis)
            val existing = reservationsByUnitId[unitId]
            if (existing != null && existing.sessionId != sessionId) {
                return false
            }
            val safeTtl = ttlMillis.coerceAtLeast(1_000L)
            reservationsByUnitId[unitId] = UnitReservation(
                unitId = unitId,
                sessionId = sessionId,
                reservedAtMillis = nowMillis,
                expiresAtMillis = nowMillis + safeTtl
            )
            return true
        }
    }

    /**
     * Synchronizes a session's active unit reservations:
     * - Releases any unit previously reserved by `sessionId` that is no longer in `desiredUnitIds`.
     * - Reserves or refreshes TTL for all `desiredUnitIds` not held by another active session.
     */
    fun syncSessionReservations(
        sessionId: String,
        desiredUnitIds: Collection<Long>,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        nowMillis: Long = System.currentTimeMillis()
    ): ReservationSyncResult {
        if (sessionId.isBlank()) {
            return ReservationSyncResult(emptySet(), desiredUnitIds.filter { it > 0L }.toSet())
        }
        val cleanDesired = desiredUnitIds.filter { it > 0L }.toSet()
        synchronized(lock) {
            clearExpiredLocked(nowMillis)
            // Release deselected units owned by this session
            val toRelease = reservationsByUnitId.values
                .filter { it.sessionId == sessionId && it.unitId !in cleanDesired }
                .map { it.unitId }
            toRelease.forEach { reservationsByUnitId.remove(it) }

            val reserved = LinkedHashSet<Long>()
            val rejected = LinkedHashSet<Long>()
            val safeTtl = ttlMillis.coerceAtLeast(1_000L)
            for (unitId in cleanDesired) {
                val existing = reservationsByUnitId[unitId]
                if (existing != null && existing.sessionId != sessionId) {
                    rejected.add(unitId)
                } else {
                    reservationsByUnitId[unitId] = UnitReservation(
                        unitId = unitId,
                        sessionId = sessionId,
                        reservedAtMillis = nowMillis,
                        expiresAtMillis = nowMillis + safeTtl
                    )
                    reserved.add(unitId)
                }
            }
            return ReservationSyncResult(reserved, rejected)
        }
    }

    fun releaseUnit(sessionId: String, unitId: Long) {
        if (sessionId.isBlank() || unitId <= 0L) return
        synchronized(lock) {
            val existing = reservationsByUnitId[unitId]
            if (existing != null && existing.sessionId == sessionId) {
                reservationsByUnitId.remove(unitId)
            }
        }
    }

    fun releaseSession(sessionId: String) {
        if (sessionId.isBlank()) return
        synchronized(lock) {
            val iterator = reservationsByUnitId.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value.sessionId == sessionId) {
                    iterator.remove()
                }
            }
        }
    }

    fun isReservedByOtherSession(
        unitId: Long,
        currentSessionId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (unitId <= 0L) return false
        synchronized(lock) {
            clearExpiredLocked(nowMillis)
            val existing = reservationsByUnitId[unitId] ?: return false
            return existing.sessionId != currentSessionId
        }
    }

    fun getReservedUnitIdsByOtherSessions(
        currentSessionId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Set<Long> {
        synchronized(lock) {
            clearExpiredLocked(nowMillis)
            return reservationsByUnitId.values
                .filter { it.sessionId != currentSessionId }
                .map { it.unitId }
                .toSet()
        }
    }

    fun getReservationForUnit(
        unitId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): UnitReservation? {
        synchronized(lock) {
            clearExpiredLocked(nowMillis)
            return reservationsByUnitId[unitId]
        }
    }

    fun getActiveReservationsSnapshot(nowMillis: Long = System.currentTimeMillis()): List<UnitReservation> {
        synchronized(lock) {
            clearExpiredLocked(nowMillis)
            return reservationsByUnitId.values.toList()
        }
    }

    fun clearAll() {
        synchronized(lock) {
            reservationsByUnitId.clear()
        }
    }
}
