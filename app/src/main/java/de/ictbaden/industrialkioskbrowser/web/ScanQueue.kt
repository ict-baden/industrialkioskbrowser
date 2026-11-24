package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.ScanQueueSettings

/**
 * Holds scans back while the page cannot take them and delivers them one by one, in the order
 * they were read (`scanner.queue`). No Android in here: the time comes from [clock], timers go
 * through [Host.wakeUpIn], so the logic is unit-tested with a fake clock.
 *
 * The page is **not ready** while
 * - a navigation was requested or the page is loading ([navigationRequested], [pageStarted]) and
 *   for [ScanQueueSettings.settleMs] after it has loaded ([pageLoaded]),
 * - a delivered scan sent terminator keys ([Outcome.SUBMITTED]): until the navigation they start has
 *   loaded, or for [ScanQueueSettings.navigationGraceMs] if none starts (single-page apps),
 * - the page reported itself busy ([busy] until [ready], at most a safety limit),
 * - a scan is being delivered.
 *
 * A scan that finds no text field ([Outcome.NO_FIELD]) stays at the head and is tried again every
 * [RETRY_MS]. Scans that cannot be delivered – queue full, [ScanQueueSettings.timeoutMs] over, page
 * left, session closed – go to [Host.dropped], never silently.
 */
class ScanQueue<T>(
    private val settings: ScanQueueSettings,
    private val clock: () -> Long,
    private val host: Host<T>,
) {

    interface Host<T> {
        /**
         * Delivers [scan] to the page and answers with [delivered] exactly once, also
         * asynchronously. [retry]: the scan event was dispatched on this page already (insert only).
         */
        fun deliver(scan: T, retry: Boolean)

        fun dropped(scans: List<T>, reason: DropReason)

        /** Call [pump] after [delayMs]; replaces an earlier request. Negative: no wake-up needed. */
        fun wakeUpIn(delayMs: Long)

        fun pendingChanged(pending: Int) {}
    }

    enum class Outcome {
        /** Inserted and terminator keys sent: the page may navigate now. */
        SUBMITTED,

        /** Taken by the page (event handled, inserted without terminator, event only). */
        DONE,

        /** No text field had the focus. */
        NO_FIELD,
    }

    enum class DropReason { FULL, TIMEOUT, PAGE_LEFT, CLOSED }

    private class Entry<T>(val scan: T, val deadline: Long) {
        var tried = false
    }

    private val waiting = ArrayDeque<Entry<T>>()
    private var inFlight: Entry<T>? = null
    private var inFlightSince = 0L

    private var navigationUntil = 0L // requested, page not started yet (safety limit)
    private var loading = false
    private var settleUntil = 0L
    private var graceUntil = 0L
    private var busyUntil = 0L
    private var retryAt = 0L
    private var closed = false

    /** Scans waiting (not the one being delivered right now). */
    val pending: Int get() = waiting.size

    /** Whether a new scan would be delivered right away. */
    val isReady: Boolean get() = blockedUntil(clock()) == null && inFlight == null

    /** A new scan: delivered now when the page is ready and nothing waits, else queued. */
    fun offer(scan: T) {
        if (closed) {
            host.dropped(listOf(scan), DropReason.CLOSED)
            return
        }
        if (waiting.size >= settings.maxPending) {
            host.dropped(listOf(scan), DropReason.FULL)
            return
        }
        waiting.addLast(Entry(scan, clock() + settings.timeoutMs))
        changed()
        pump()
    }

    /** Answer to [Host.deliver]. [busy]: the page called `scanner.busy()` while handling the scan. */
    fun delivered(outcome: Outcome, busy: Boolean = false) {
        val entry = inFlight ?: return
        inFlight = null
        val now = clock()
        when (outcome) {
            Outcome.SUBMITTED -> graceUntil = now + settings.navigationGraceMs
            Outcome.DONE -> Unit
            Outcome.NO_FIELD -> if (entry.deadline <= now) {
                host.dropped(listOf(entry.scan), DropReason.TIMEOUT)
            } else {
                entry.tried = true
                waiting.addFirst(entry)
                retryAt = now + RETRY_MS
            }
        }
        if (busy) busy(null)
        changed()
        pump()
    }

    /** The WebView is about to load a page in the main frame (request seen, nothing shown yet). */
    fun navigationRequested() {
        navigationUntil = clock() + settings.timeoutMs
        graceUntil = 0
        pump()
    }

    /** The request did not lead to a page (download, cancelled). */
    fun navigationAbandoned() {
        navigationUntil = 0
        pump()
    }

    /** A new document replaces the page: busy and retries belong to the old one. */
    fun pageStarted() {
        loading = true
        navigationUntil = 0
        graceUntil = 0
        busyUntil = 0
        retryAt = 0
        waiting.forEach { it.tried = false }
        pump()
    }

    /**
     * The page has loaded (its scripts and meta tags are there). [acceptsScans] is false for error
     * pages and addresses outside the allow list: what waits is dropped.
     */
    fun pageLoaded(acceptsScans: Boolean) {
        loading = false
        navigationUntil = 0
        settleUntil = clock() + settings.settleMs
        if (!acceptsScans) dropAll(DropReason.PAGE_LEFT)
        pump()
    }

    /** A navigation of the page was blocked (allow list): the flow the scans were meant for is broken. */
    fun navigationBlocked() {
        dropAll(DropReason.PAGE_LEFT)
        pump()
    }

    /** `scanner.busy()`: not ready until [ready], at most [timeoutMs] (default [ScanQueueSettings.timeoutMs]). */
    fun busy(timeoutMs: Long?) {
        val limit = (timeoutMs ?: settings.timeoutMs.toLong()).coerceIn(1, MAX_BUSY_MS)
        busyUntil = maxOf(busyUntil, clock() + limit)
        pump()
    }

    /** `scanner.ready()`. */
    fun ready() {
        busyUntil = 0
        pump()
    }

    /** The session ends: everything waiting is dropped, later scans too. */
    fun close() {
        closed = true
        dropAll(DropReason.CLOSED)
        host.wakeUpIn(-1)
    }

    /** Drops what timed out, delivers the head when the page is ready, or asks for the next wake-up. */
    fun pump() {
        if (closed) return
        val now = clock()
        val expired = waiting.filter { it.deadline <= now }
        if (expired.isNotEmpty()) {
            waiting.removeAll(expired)
            host.dropped(expired.map { it.scan }, DropReason.TIMEOUT)
            changed()
        }
        val current = inFlight
        if (current != null) {
            // A delivery whose answer never came (renderer stuck) must not block the queue for ever.
            if (now - inFlightSince < settings.timeoutMs) {
                schedule(now, inFlightSince + settings.timeoutMs)
                return
            }
            inFlight = null
        }
        val head = waiting.firstOrNull() ?: run {
            host.wakeUpIn(-1)
            return
        }
        val blocked = blockedUntil(now) ?: retryAt.takeIf { it > now }
        if (blocked != null) {
            schedule(now, blocked)
            return
        }
        waiting.removeFirst()
        inFlight = head
        inFlightSince = now
        changed()
        host.deliver(head.scan, head.tried)
    }

    private fun dropAll(reason: DropReason) {
        if (waiting.isEmpty()) return
        val scans = waiting.map { it.scan }
        waiting.clear()
        host.dropped(scans, reason)
        changed()
    }

    /** Next time the page may become ready, `null` when it is ready; [Long.MAX_VALUE] while loading. */
    private fun blockedUntil(now: Long): Long? {
        if (loading) return Long.MAX_VALUE
        return listOf(navigationUntil, settleUntil, graceUntil, busyUntil).filter { it > now }.maxOrNull()
    }

    /** Wakes up at [until] or at the next deadline, whatever comes first. */
    private fun schedule(now: Long, until: Long?) {
        val deadline = waiting.minOfOrNull { it.deadline }
        val next = listOfNotNull(until?.takeIf { it != Long.MAX_VALUE }, deadline).minOrNull()
        host.wakeUpIn(next?.let { maxOf(0, it - now) } ?: -1)
    }

    private fun changed() = host.pendingChanged(waiting.size)

    companion object {
        /** Interval for trying a scan again that found no text field. */
        const val RETRY_MS = 250L

        /** Upper limit for `scanner.busy(timeoutMs)`. */
        const val MAX_BUSY_MS = 120_000L
    }
}
