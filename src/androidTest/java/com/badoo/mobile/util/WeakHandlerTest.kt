/*
 * Copyright (c) 2014 Badoo Trading Limited
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.badoo.mobile.util

import android.os.HandlerThread
import android.os.SystemClock
import android.support.test.runner.AndroidJUnit4
import android.test.FlakyTest
import android.test.suitebuilder.annotation.MediumTest
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit tests for [com.badoo.mobile.util.WeakHandler]
 *
 * Created by Dmytro Voronkevych on 17/06/2014.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class WeakHandlerTest {

    private lateinit var mThread: HandlerThread
    private lateinit var mHandler: WeakHandler

    @Before
    fun setup() {
        mThread = HandlerThread("test")
        mThread.start()
        mHandler = WeakHandler(mThread.looper)
    }

    @After
    fun tearDown() {
        mHandler.looper.quit()
    }

    @FlakyTest
    @Test
    @Throws(InterruptedException::class)
    fun postDelayed() {
        val latch = CountDownLatch(1)

        val startTime = SystemClock.elapsedRealtime()
        val executed = AtomicBoolean(false)
        mHandler.postDelayed(Runnable {
            executed.set(true)
            latch.countDown()
        }, 300)

        latch.await(1, TimeUnit.SECONDS)
        assertTrue(executed.get())

        val elapsedTime = SystemClock.elapsedRealtime() - startTime
        assertTrue("Elapsed time should be 300, but was " + elapsedTime, elapsedTime <= 330 && elapsedTime >= 300)
    }

    @Test
    @Throws(InterruptedException::class)
    fun removeCallbacks() {
        val latch = CountDownLatch(1)

        val startTime = SystemClock.elapsedRealtime()
        val executed = AtomicBoolean(false)
        val r = Runnable {
            executed.set(true)
            latch.countDown()
        }
        mHandler.postDelayed(r, 300)
        mHandler.removeCallbacks(r)
        latch.await(1, TimeUnit.SECONDS)
        assertFalse(executed.get())

        val elapsedTime = SystemClock.elapsedRealtime() - startTime
        assertTrue(elapsedTime > 300)
    }

    @Test(timeout = 30000)
    @Throws(Throwable::class)
    fun concurrentRemoveAndExecute() {
        val repeatCount = 100
        val numberOfRunnables = 10000

        // Councurrent cases sometimes very hard to spot, so we will do it by repeating same test 1000 times
        // Problem was reproducing always by this test until I fixed WeakHandler
        for (testNum in 0 until repeatCount) {
            val mExceptionInThread = AtomicReference<Throwable>()

            val thread = HandlerThread("HandlerThread")
            // Concurrent issue can occur inside HandlerThread or inside main thread
            // Catching both of cases
            thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, ex -> mExceptionInThread.set(ex) }
            thread.start()

            val handler = WeakHandler(thread.looper)
            val runnables = arrayOfNulls<Runnable>(numberOfRunnables)
            for (i in runnables.indices) {
                runnables[i] = DummyRunnable()
                handler.post(runnables[i]!!) // Many Runnables been posted
            }

            for (runnable in runnables) {
                handler.removeCallbacks(runnable!!) // All of them now quickly removed
                // Before I fixed impl of WeakHandler it always caused exceptions
            }
            if (mExceptionInThread.get() != null) {
                throw mExceptionInThread.get() // Exception from HandlerThread. Sometimes it occured as well
            }
            thread.looper.quit()
        }
    }

    @Test(timeout = 30000)
    @Throws(NoSuchFieldException::class, IllegalAccessException::class, InterruptedException::class)
    fun concurrentAdd() {
        val executor = ThreadPoolExecutor(10, 50, 10, TimeUnit.SECONDS, LinkedBlockingQueue(100))
        val added = Collections.synchronizedSet(HashSet<SleepyRunnable>())
        val latch = CountDownLatch(999)
        // Adding 1000 Runnables from different threads
        mHandler.post(SleepyRunnable(0))
        for (i in 0..998) {
            val sleepyRunnable = SleepyRunnable(i + 1)
            executor.execute {
                mHandler.post(sleepyRunnable)
                added.add(sleepyRunnable)
                latch.countDown()
            }
        }

        // Waiting until all runnables added
        // Notified by #Notify1
        latch.await()

        var ref = mHandler.mRunnables.next
        while (ref != null) {
            assertTrue("Must remove runnable from chained list: " + ref.runnable, added.remove(ref.runnable))
            ref = ref.next
        }

        assertTrue("All runnables should present in chain, however we still haven't found " + added, added.isEmpty())
    }

    private inner class DummyRunnable : Runnable {
        override fun run() {}
    }

    private inner class SleepyRunnable(private val mNum: Int) : Runnable {

        override fun run() {
            try {
                Thread.sleep(1000000)
            } catch (e: Exception) {
                // Ignored
            }

        }

        override fun toString(): String {
            return mNum.toString()
        }
    }
}
