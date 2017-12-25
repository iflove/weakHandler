package com.badoo.mobile.util

import android.support.test.runner.AndroidJUnit4
import android.test.suitebuilder.annotation.SmallTest
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Tests for [com.badoo.mobile.util.WeakHandler.ChainedRef]
 *
 * @author Dmytro Voronkevych
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class WeakHandlerChainedRefTest {

    private var mHeadRunnable: Runnable? = null
    private var mFirstRunnable: Runnable? = null
    private var mSecondRunnable: Runnable? = null
    private var mLock: Lock? = null
    private var mRefHead: WeakHandler.ChainedRef? = null
    private var mSecond: WeakHandler.ChainedRef? = null
    private var mFirst: WeakHandler.ChainedRef? = null
    private var mHeadWeakRunnable: WeakHandler.WeakRunnable? = null
    private var mFirstWeakRunnable: WeakHandler.WeakRunnable? = null
    private var mSecondWeakRunnable: WeakHandler.WeakRunnable? = null

    // Creates linked list refHead <-> first <-> second
    @Before
    fun setUp() {
        mLock = ReentrantLock()

        mHeadRunnable = DummyRunnable()
        mFirstRunnable = DummyRunnable()
        mSecondRunnable = DummyRunnable()

        mRefHead = object : WeakHandler.ChainedRef(mLock!!, mHeadRunnable!!) {
            override fun toString(): String {
                return "refHead"
            }
        }
        mFirst = object : WeakHandler.ChainedRef(mLock!!, mFirstRunnable!!) {
            override fun toString(): String {
                return "second"
            }
        }
        mSecond = object : WeakHandler.ChainedRef(mLock!!, mSecondRunnable!!) {
            override fun toString(): String {
                return "first"
            }
        }

        mRefHead!!.insertAfter(mSecond!!)
        mRefHead!!.insertAfter(mFirst!!)

        mHeadWeakRunnable = mRefHead!!.wrapper
        mFirstWeakRunnable = mFirst!!.wrapper
        mSecondWeakRunnable = mSecond!!.wrapper
    }

    @Test
    fun insertAfter() {
        assertSame(mFirst, mRefHead!!.next)
        assertSame(mSecond, mRefHead!!.next!!.next)
        assertNull(mRefHead!!.next!!.next!!.next)

        assertNull(mRefHead!!.prev)
        assertSame(mFirst, mSecond!!.prev)
        assertSame(mRefHead, mFirst!!.prev)
    }

    @Test
    fun removeFirst() {
        mFirst!!.remove()

        assertNull(mFirst!!.next)
        assertNull(mFirst!!.prev)

        assertSame(mSecond, mRefHead!!.next)
        assertNull(mSecond!!.next)
        assertSame(mRefHead, mSecond!!.prev)
    }

    @Test
    fun removeSecond() {
        mSecond!!.remove()
        assertNull(mSecond!!.next)
        assertNull(mSecond!!.prev)

        assertSame(mFirst, mRefHead!!.next)
        assertSame(mRefHead, mFirst!!.prev)
        assertNull(mFirst!!.next)
    }

    @Test
    fun removeFirstByRunnable() {
        assertSame(mFirstWeakRunnable, mRefHead!!.remove(mFirstRunnable!!))
        assertSame(mRefHead!!.next, mSecond)
        assertSame(mRefHead, mSecond!!.prev)
        assertNull(mFirst!!.next)
        assertNull(mFirst!!.prev)
    }

    @Test
    fun removeSecondByRunnable() {
        assertSame(mSecondWeakRunnable, mRefHead!!.remove(mSecondRunnable!!))
        assertSame(mFirst, mRefHead!!.next)
        assertSame(mRefHead, mFirst!!.prev)
        assertNull(mSecond!!.next)
        assertNull(mSecond!!.prev)
    }

    @Test
    fun removeNonExistentRunnableReturnNull() {
        assertNull(mRefHead!!.remove(DummyRunnable()))
        assertSame(mFirst, mRefHead!!.next)
        assertNull(mSecond!!.next)
        assertSame(mFirst, mSecond!!.prev)
        assertSame(mRefHead, mFirst!!.prev)
    }

    private inner class DummyRunnable : Runnable {
        override fun run() {}
    }
}
