/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2009 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version
 * 2 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ActiveEon Team
 *                        http://www.activeeon.com/
 *  Contributor(s):
 *
 *
 * ################################################################
 * $$ACTIVEEON_INITIAL_DEV$$
 */
package org.objectweb.proactive.core.mop.lock;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;


/**
 * Simple proxy on a remote lock, hosted by a RemoteLocksManager.
 * This object is a POJO object.
 */
public class LockProxy implements Lock, Serializable {

    // lock id
    private int id;

    // remote lock owner
    private RemoteLocksManager rlm;

    /**
     * Create a new proxy.
     * @param rlm the remote manager which hosts the actual lock.
     * @param id the id which identifies the actual lock on the rml.
     */
    public LockProxy(RemoteLocksManager rlm, int id) {
        this.id = id;
        this.rlm = rlm;
    }

    //////////////////// METHODS FROM LOCK ////////////////////

    /* (non-Javadoc)
     * @see java.util.concurrent.locks.Lock#lock()
     */
    public void lock() {
        rlm.lock(this.id);
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.locks.Lock#lockInterruptibly()
     */
    public void lockInterruptibly() throws InterruptedException {
        rlm.lockInterruptibly(this.id);
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.locks.Lock#newCondition()
     */
    public Condition newCondition() {
        return rlm.newCondition(this.id);
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.locks.Lock#tryLock()
     */
    public boolean tryLock() {
        return rlm.tryLock(this.id);
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.locks.Lock#tryLock(long, java.util.concurrent.TimeUnit)
     */
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return rlm.tryLock(this.id, time, unit);
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.locks.Lock#unlock()
     */
    public void unlock() {
        rlm.unlock(this.id);
    }
}
