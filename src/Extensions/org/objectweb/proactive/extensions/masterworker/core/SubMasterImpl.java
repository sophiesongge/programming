package org.objectweb.proactive.extensions.masterworker.core;

import org.objectweb.proactive.api.PAFuture;
import org.objectweb.proactive.extensions.masterworker.TaskException;
import org.objectweb.proactive.extensions.masterworker.interfaces.SubMaster;
import org.objectweb.proactive.extensions.masterworker.interfaces.Task;
import org.objectweb.proactive.extensions.masterworker.interfaces.internal.MasterIntern;
import org.objectweb.proactive.extensions.masterworker.interfaces.internal.ResultIntern;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


/**
 * <i><font size="-1" color="#FF0000">**For internal use only** </font></i><br>
 * The SubMasterImpl class is acting as a client on the worker side to talk to the master, 
 * submit tasks, wait results, etc... <br>
 *
 * @author The ProActive Team
 */
public class SubMasterImpl implements SubMaster<Task<Serializable>, Serializable>, Serializable {

    private final MasterIntern master;
    private final String originatorName;
    private boolean initCalled = false;
    private AOWorker parentWorker;

    public SubMasterImpl(MasterIntern master, String originatorName, AOWorker parentWorker) {
        this.master = master;
        this.originatorName = originatorName;
        this.parentWorker = parentWorker;
    }

    public void setResultReceptionOrder(OrderingMode mode) {
        master.setResultReceptionOrder(originatorName, mode);
    }

    /**
    * {@inheritDoc}
    */
    public void solve(List<Task<Serializable>> tasks) {
        if (tasks.size() == 0) {
            throw new IllegalArgumentException("empty list");
        }
        master.solveIntern(originatorName, tasks);
        initCalled = true;
    }

    /**
    * {@inheritDoc}
    */
    @SuppressWarnings("unchecked")
    public List<Serializable> waitAllResults() throws TaskException {
        if (!initCalled) {
            throw new IllegalStateException("A call to solve should occur before this call.");
        }

        List<Serializable> results = null;
        Object future = master.waitAllResults(originatorName);
        //parentWorker.resumeWork();
        try {
            results = (List<Serializable>) PAFuture.getFutureValue(future);
        } catch (RuntimeException e) {
            Throwable texp = findTaskException(e);
            if (texp != null) {
                throw (TaskException) texp;
            } else
                throw e;
        }
        //parentWorker.suspendWork();

        return results;
    }

    /**
    * {@inheritDoc}
    */
    @SuppressWarnings("unchecked")
    public Serializable waitOneResult() throws TaskException {
        if (!initCalled) {
            throw new IllegalStateException("A call to solve should occur before this call.");
        }
        Serializable result = null;
        Object future = master.waitOneResult(originatorName);
        //parentWorker.resumeWork();
        try {
            result = (Serializable) PAFuture.getFutureValue(future);
        } catch (RuntimeException e) {
            Throwable texp = findTaskException(e);
            if (texp != null) {
                throw (TaskException) texp;
            } else
                throw e;
        }

        //parentWorker.suspendWork();
        return result;

    }

    public List<Serializable> waitSomeResults() throws TaskException {
        if (!initCalled) {
            throw new IllegalStateException("A call to solve should occur before this call.");
        }

        List<Serializable> results = null;
        Object future = master.waitSomeResults(originatorName);
        // parentWorker.resumeWork();
        try {
            results = (List<Serializable>) PAFuture.getFutureValue(future);
        } catch (RuntimeException e) {
            Throwable texp = findTaskException(e);
            if (texp != null) {
                throw (TaskException) texp;
            } else
                throw e;
        }
        // parentWorker.suspendWork();
        return results;
    }

    /**
    * {@inheritDoc}
    */
    @SuppressWarnings("unchecked")
    public List<Serializable> waitKResults(int k) throws TaskException {
        if (!initCalled) {
            throw new IllegalStateException("A call to solve should occur before this call.");
        }
        if (master.countPending(originatorName) < k) {
            throw new IllegalStateException("Number of tasks submitted previously is strictly less than " +
                k + ": call to this method will wait forever");
        }
        List<Serializable> results = null;
        Object future = master.waitKResults(originatorName, k);
        //  parentWorker.resumeWork();
        try {
            results = (List<Serializable>) PAFuture.getFutureValue(future);
        } catch (RuntimeException e) {
            Throwable texp = findTaskException(e);
            if (texp != null) {
                throw (TaskException) texp;
            } else
                throw e;
        }
        //  parentWorker.suspendWork();
        return results;
    }

    /**
    * {@inheritDoc}
    */
    @SuppressWarnings("unchecked")
    public boolean isEmpty() {
        if (!initCalled) {
            throw new IllegalStateException("A call to solve should occur before this call.");
        }
        return master.isEmpty(originatorName);
    }

    /**
    * {@inheritDoc}
    */
    @SuppressWarnings("unchecked")
    public int countAvailableResults() {
        if (!initCalled) {
            throw new IllegalStateException("A call to solve should occur before this call.");
        }
        return master.countAvailableResults(originatorName);
    }

    private Throwable findTaskException(Throwable e) {
        if (e instanceof TaskException) {
            return e;
        }
        if (e.getCause() != null) {
            return findTaskException(e.getCause());
        } else {
            return null;
        }
    }
}
