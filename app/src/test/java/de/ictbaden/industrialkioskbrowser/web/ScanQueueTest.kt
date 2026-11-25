package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.ScanQueueSettings
import de.ictbaden.industrialkioskbrowser.web.ScanQueue.DropReason
import de.ictbaden.industrialkioskbrowser.web.ScanQueue.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The queue's state logic with a fixed test clock: nothing here waits for real time. */
class ScanQueueTest {

    private val settings = ScanQueueSettings(maxPending = 3, timeoutMs = 5_000, navigationGraceMs = 400, settleMs = 200)

    /** Records deliveries and drops, runs the requested wake-up when the clock passes it. */
    private inner class Harness(s: ScanQueueSettings = settings) : ScanQueue.Host<String> {
        var now = 1_000L
        var wakeAt = -1L
        val delivered = mutableListOf<String>()
        val retries = mutableListOf<String>()
        val dropped = mutableListOf<Pair<List<String>, DropReason>>()
        val queue = ScanQueue(s, { now }, this)

        override fun deliver(scan: String, retry: Boolean) {
            delivered += scan
            if (retry) retries += scan
        }

        override fun dropped(scans: List<String>, reason: DropReason) {
            dropped += scans to reason
        }

        override fun wakeUpIn(delayMs: Long) {
            wakeAt = if (delayMs < 0) -1 else now + delayMs
        }

        /** Moves the clock forward, running every wake-up that becomes due on the way. */
        fun advance(ms: Long) {
            val end = now + ms
            while (wakeAt in 0..end) {
                now = maxOf(now, wakeAt)
                wakeAt = -1
                queue.pump()
            }
            now = end
        }

        /** A page that loaded and settled. */
        fun loadedPage() {
            queue.pageStarted()
            queue.pageLoaded(acceptsScans = true)
            advance(settings.settleMs.toLong())
        }
    }

    @Test
    fun `a ready page with an empty queue gets the scan at once`() {
        val h = Harness()
        h.loadedPage()
        assertTrue(h.queue.isReady)
        h.queue.offer("A")
        assertEquals(listOf("A"), h.delivered)
        assertEquals(0, h.queue.pending)
    }

    @Test
    fun `scans wait while the page loads and for the settle time after it`() {
        val h = Harness()
        h.queue.navigationRequested()
        h.queue.offer("A")
        h.queue.pageStarted()
        h.queue.offer("B")
        assertEquals(emptyList<String>(), h.delivered)
        assertEquals(2, h.queue.pending)
        assertFalse(h.queue.isReady)

        h.queue.pageLoaded(acceptsScans = true)
        h.advance(199)
        assertEquals(emptyList<String>(), h.delivered)
        h.advance(1)
        assertEquals(listOf("A"), h.delivered)
        h.queue.delivered(Outcome.DONE)
        assertEquals(listOf("A", "B"), h.delivered)
    }

    @Test
    fun `after a terminator key the next scan waits for the navigation it starts`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.offer("B")
        h.queue.offer("C")
        h.queue.delivered(Outcome.SUBMITTED)
        // The form is sent: the request comes in within the grace time, the server takes 2 s.
        h.advance(100)
        h.queue.navigationRequested()
        h.advance(2_000)
        assertEquals(listOf("A"), h.delivered)
        h.queue.pageStarted()
        h.advance(300)
        h.queue.pageLoaded(acceptsScans = true)
        h.advance(200)
        assertEquals(listOf("A", "B"), h.delivered)
        // B is sent too: C waits for its page in turn.
        h.queue.delivered(Outcome.SUBMITTED)
        h.queue.navigationRequested()
        h.queue.pageStarted()
        h.queue.pageLoaded(acceptsScans = true)
        h.advance(199)
        assertEquals(listOf("A", "B"), h.delivered)
        h.advance(1)
        assertEquals(listOf("A", "B", "C"), h.delivered)
    }

    @Test
    fun `without a navigation the page is ready again after the grace time`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.delivered(Outcome.SUBMITTED)
        h.queue.offer("B")
        assertFalse(h.queue.isReady)
        h.advance(399)
        assertEquals(listOf("A"), h.delivered)
        h.advance(1)
        assertEquals(listOf("A", "B"), h.delivered)
    }

    @Test
    fun `handled scans and scans without terminator start no grace time`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.offer("B")
        h.queue.delivered(Outcome.DONE)
        assertEquals(listOf("A", "B"), h.delivered)
        assertFalse(h.queue.isReady) // B is being delivered
        h.queue.delivered(Outcome.DONE)
        assertTrue(h.queue.isReady)
    }

    @Test
    fun `a handled scan holds the next one back when the page reported busy while handling it`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.offer("B")
        h.queue.delivered(Outcome.DONE, busy = true)
        h.advance(3_000)
        assertEquals(listOf("A"), h.delivered)
        h.queue.ready()
        assertEquals(listOf("A", "B"), h.delivered)
    }

    @Test
    fun `scans stay in order while one waits for a field`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.offer("B")
        h.queue.delivered(Outcome.NO_FIELD)
        assertEquals(2, h.queue.pending)
        h.advance(ScanQueue.RETRY_MS)
        assertEquals(listOf("A", "A"), h.delivered)
        assertEquals(listOf("A"), h.retries) // the scan event is not dispatched twice on one page
        h.queue.delivered(Outcome.DONE)
        assertEquals(listOf("A", "A", "B"), h.delivered)
        assertEquals(listOf("A"), h.retries)
    }

    @Test
    fun `a scan that never finds a field is dropped after the timeout`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        while (h.dropped.isEmpty()) {
            h.queue.delivered(Outcome.NO_FIELD)
            h.advance(ScanQueue.RETRY_MS)
        }
        assertEquals(listOf(listOf("A") to DropReason.TIMEOUT), h.dropped)
        // Tried every 250 ms for 5 s: at 0, 250 … 4 750 ms.
        assertEquals((5_000 / ScanQueue.RETRY_MS).toInt(), h.delivered.size)
        assertTrue(h.queue.isReady)
    }

    @Test
    fun `a new page dispatches the scan event again`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.delivered(Outcome.NO_FIELD)
        h.loadedPage()
        assertEquals(listOf("A", "A"), h.delivered)
        assertEquals(emptyList<String>(), h.retries)
    }

    @Test
    fun `without progress only the head is dropped, then the next one gets the full time`() {
        val h = Harness()
        h.queue.pageStarted() // the page hangs while loading
        h.queue.offer("A")
        h.advance(2_000)
        h.queue.offer("B")
        h.advance(2_999)
        assertEquals(emptyList<Pair<List<String>, DropReason>>(), h.dropped)
        h.advance(1)
        assertEquals(listOf(listOf("A") to DropReason.TIMEOUT), h.dropped)
        assertEquals(1, h.queue.pending)
        // B waited 3 s already, but the clock starts again when it becomes the head.
        h.advance(4_999)
        assertEquals(1, h.dropped.size)
        h.advance(1)
        assertEquals(listOf(listOf("A") to DropReason.TIMEOUT, listOf("B") to DropReason.TIMEOUT), h.dropped)
        assertEquals(0, h.queue.pending)
        assertEquals(emptyList<String>(), h.delivered)
    }

    @Test
    fun `a long queue that moves on is never cut short by the timeout`() {
        // 15 scans 0.2 s apart into a form whose every save takes 1 s: the last one waits far longer
        // than timeoutMs (5 s) after it was read, but the queue never stands still that long.
        val h = Harness(settings.copy(maxPending = 20))
        h.loadedPage()
        for (i in 1..15) {
            h.queue.offer("S$i")
            h.advance(200)
        }
        var guard = 0
        while (h.delivered.size < 15 && guard++ < 100) {
            h.queue.delivered(Outcome.SUBMITTED)
            h.advance(100)
            h.queue.navigationRequested()
            h.advance(900)
            h.queue.pageStarted()
            h.queue.pageLoaded(acceptsScans = true)
            h.advance(200)
        }
        assertEquals((1..15).map { "S$it" }, h.delivered)
        assertEquals(emptyList<Pair<List<String>, DropReason>>(), h.dropped)
    }

    @Test
    fun `a field that still holds the previous scan is checked again without dispatching the event`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.delivered(Outcome.SUBMITTED)
        h.queue.offer("B")
        h.advance(400)
        assertEquals(listOf("A", "B"), h.delivered)
        h.queue.delivered(Outcome.FIELD_NOT_CLEARED) // the page has not emptied the field yet
        assertEquals(1, h.queue.pending)
        h.advance(ScanQueue.RETRY_MS)
        assertEquals(listOf("A", "B", "B"), h.delivered)
        assertEquals(emptyList<String>(), h.retries) // the event still has to go out
        h.queue.delivered(Outcome.SUBMITTED)
        assertEquals(emptyList<Pair<List<String>, DropReason>>(), h.dropped)
    }

    @Test
    fun `a field that is never emptied drops the scan after timeoutMs without progress`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.delivered(Outcome.SUBMITTED)
        h.queue.offer("B")
        h.advance(400)
        while (h.dropped.isEmpty()) {
            h.queue.delivered(Outcome.FIELD_NOT_CLEARED)
            h.advance(ScanQueue.RETRY_MS)
        }
        assertEquals(listOf(listOf("B") to DropReason.TIMEOUT), h.dropped)
        // Progress was A's delivery at 1 200 ms; B is tried from 1 600 to 6 100 ms, dropped at 6 200 ms.
        assertEquals(19, h.delivered.count { it == "B" })
    }

    @Test
    fun `a full queue drops the new scan, not the waiting ones`() {
        val h = Harness()
        h.queue.pageStarted()
        listOf("A", "B", "C", "D").forEach(h.queue::offer)
        assertEquals(3, h.queue.pending)
        assertEquals(listOf(listOf("D") to DropReason.FULL), h.dropped)
        h.queue.pageLoaded(acceptsScans = true)
        h.advance(200)
        assertEquals(listOf("A"), h.delivered)
    }

    @Test
    fun `busy holds scans back until ready`() {
        val h = Harness()
        h.loadedPage()
        h.queue.busy(null)
        assertFalse(h.queue.isReady)
        h.queue.offer("A")
        h.advance(1_000)
        assertEquals(emptyList<String>(), h.delivered)
        h.queue.ready()
        assertEquals(listOf("A"), h.delivered)
    }

    @Test
    fun `busy without ready ends after its safety limit`() {
        val h = Harness(settings.copy(timeoutMs = 10_000))
        h.loadedPage()
        h.queue.busy(3_000)
        h.queue.offer("A")
        h.advance(2_999)
        assertEquals(emptyList<String>(), h.delivered)
        h.advance(1)
        assertEquals(listOf("A"), h.delivered)
    }

    @Test
    fun `busy without a limit ends after timeoutMs`() {
        val h = Harness()
        h.loadedPage()
        h.queue.busy(null)
        h.advance(5_000)
        assertTrue(h.queue.isReady)
    }

    @Test
    fun `a new page forgets that the old one was busy`() {
        val h = Harness()
        h.loadedPage()
        h.queue.busy(null)
        h.queue.offer("A")
        h.loadedPage()
        assertEquals(listOf("A"), h.delivered)
    }

    @Test
    fun `an error page drops what waits`() {
        val h = Harness()
        h.queue.pageStarted()
        h.queue.offer("A")
        h.queue.offer("B")
        h.queue.pageLoaded(acceptsScans = false)
        assertEquals(listOf(listOf("A", "B") to DropReason.PAGE_LEFT), h.dropped)
        assertEquals(0, h.queue.pending)
    }

    @Test
    fun `a blocked navigation drops what waits`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.offer("B")
        h.queue.delivered(Outcome.SUBMITTED)
        h.queue.navigationBlocked()
        assertEquals(listOf(listOf("B") to DropReason.PAGE_LEFT), h.dropped)
    }

    @Test
    fun `closing drops what waits and every later scan`() {
        val h = Harness()
        h.queue.pageStarted()
        h.queue.offer("A")
        h.queue.close()
        h.queue.offer("B")
        assertEquals(listOf(listOf("A") to DropReason.CLOSED, listOf("B") to DropReason.CLOSED), h.dropped)
        assertEquals(-1, h.wakeAt)
    }

    @Test
    fun `a request that leads to no page stops blocking`() {
        val h = Harness()
        h.loadedPage()
        h.queue.navigationRequested()
        h.queue.offer("A")
        assertEquals(emptyList<String>(), h.delivered)
        h.queue.navigationAbandoned()
        assertEquals(listOf("A"), h.delivered)
    }

    @Test
    fun `a request that never shows a page blocks at most for timeoutMs`() {
        val h = Harness()
        h.loadedPage()
        h.queue.navigationRequested()
        h.advance(1_000)
        h.queue.offer("A")
        h.advance(3_999)
        assertEquals(emptyList<String>(), h.delivered)
        h.advance(1)
        assertEquals(listOf("A"), h.delivered)
    }

    @Test
    fun `a delivery without an answer does not block the queue for ever`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.advance(1_000)
        h.queue.offer("B")
        h.advance(3_999)
        assertEquals(listOf("A"), h.delivered)
        h.advance(1)
        assertEquals(listOf("A", "B"), h.delivered)
    }

    @Test
    fun `the earliest wake-up is requested`() {
        val h = Harness()
        h.loadedPage()
        h.queue.offer("A")
        h.queue.delivered(Outcome.SUBMITTED)
        h.queue.offer("B")
        assertEquals(h.now + 400, h.wakeAt)
        h.queue.pageStarted()
        // While loading only the deadline of B can end the wait.
        assertEquals(h.now + 5_000, h.wakeAt)
    }
}
