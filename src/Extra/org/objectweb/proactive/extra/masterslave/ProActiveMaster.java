/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2007 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@objectweb.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://www.inria.fr/oasis/ProActive/contacts.html
 *  Contributor(s):
 *
 * ################################################################
 */
package org.objectweb.proactive.extra.masterslave;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.ProActive;
import org.objectweb.proactive.core.descriptor.data.VirtualNode;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.extra.masterslave.core.AOMaster;
import org.objectweb.proactive.extra.masterslave.core.TaskAlreadySubmittedException;
import org.objectweb.proactive.extra.masterslave.core.TaskWrapperImpl;
import org.objectweb.proactive.extra.masterslave.interfaces.Master;
import org.objectweb.proactive.extra.masterslave.interfaces.Task;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.ResultIntern;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.TaskIntern;


/**
 * Entry point of the Master/Slave API.<br/>
 * Here is how the Master/Slave API is used :
 * <ol>
 * <li>Create a ProActiveMaster object through the different constructors</li>
 * <li>Submit tasks through the use of the <b><i>solve</i></b> methods</li>
 * <li>Collect results through the <b><i>wait</i></b> methods</li>
 * </ol>
 * @author fviale
 *
 * @param <T> Task of result R
 * @param <R> Result Object
 */
public class ProActiveMaster<T extends Task<R>, R extends Serializable>
    implements Master<T, R>, Serializable {
    protected AOMaster aomaster = null;

    // IDs management : we keep an internal version of the tasks with an ID internally generated
    protected HashMap<T, TaskIntern> wrappedTasks;
    protected HashMap<TaskIntern, T> wrappedTasksRev;
    protected long taskCounter = 0;

    /**
     * An empty master (you can add resources afterwards)
     */
    public ProActiveMaster() {
        wrappedTasks = new HashMap<T, TaskIntern>();
        wrappedTasksRev = new HashMap<TaskIntern, T>();
    }

    /**
     * Creates a master with a collection of nodes
     * @param nodes
     */
    public ProActiveMaster(Collection<Node> nodes) {
        this();
        try {
            aomaster = (AOMaster) ProActive.newActive(AOMaster.class.getName(),
                    new Object[] {  });

            aomaster.addResources(nodes);
        } catch (ActiveObjectCreationException e) {
            throw new IllegalArgumentException(e);
        } catch (NodeException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Creates a master with a descriptorURL and an array of virtual node names
     * @param descriptorURL
     * @param virtualNodeNames
     */
    public ProActiveMaster(URL descriptorURL, String virtualNodeName) {
        this();
        try {
            aomaster = (AOMaster) ProActive.newActive(AOMaster.class.getName(),
                    new Object[] {  });
            aomaster.addResources(descriptorURL, virtualNodeName);
        } catch (ActiveObjectCreationException e) {
            throw new IllegalArgumentException(e);
        } catch (NodeException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Creates a master with the given virtual node
     * @param virtualNode
     */
    public ProActiveMaster(VirtualNode virtualNode) {
        try {
            aomaster = (AOMaster) ProActive.newActive(AOMaster.class.getName(),
                    new Object[] {  });
            aomaster.addResources(virtualNode);
        } catch (ActiveObjectCreationException e) {
            throw new IllegalArgumentException(e);
        } catch (NodeException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#addResources(java.util.Collection)
     */
    public void addResources(Collection<Node> nodes) {
        aomaster.addResources(nodes);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#addResources(java.net.URL, java.lang.String)
     */
    public void addResources(URL descriptorURL, String virtualNodeName) {
        aomaster.addResources(descriptorURL, virtualNodeName);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#addResources(org.objectweb.proactive.core.descriptor.data.VirtualNode)
     */
    public void addResources(VirtualNode virtualnode) {
        aomaster.addResources(virtualnode);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#areAllResultsAvailable()
     */
    public boolean areAllResultsAvailable() {
        return aomaster.areAllResultsAvailable();
    }

    /**
     * Creates an internal wrapper of the given task
     * This wrapper will identify the task internally via an ID
     * @param task task to be wrapped
     * @return wrapped version
     * @throws IllegalArgumentException if the same task has already been wrapped
     */
    private TaskIntern createWrapping(T task) throws IllegalArgumentException {
        if (wrappedTasks.containsKey(task)) {
            throw new IllegalArgumentException(new TaskAlreadySubmittedException());
        }

        TaskIntern wrapper = new TaskWrapperImpl(taskCounter, task);
        taskCounter = (taskCounter + 1) % (Long.MAX_VALUE - 1);
        wrappedTasks.put(task, wrapper);
        wrappedTasksRev.put(wrapper, task);
        return wrapper;
    }

    /**
     * Creates an internal version of the given collection of tasks
     * This wrapper will identify the task internally via an ID
     * @param tasks collection of tasks to be wrapped
     * @return wrapped version
     * @throws IllegalArgumentException if the same task has already been wrapped
     */
    private Collection<TaskIntern> createWrappings(Collection<T> tasks)
        throws IllegalArgumentException {
        Collection<TaskIntern> wrappings = new ArrayList<TaskIntern>();
        for (T task : tasks) {
            wrappings.add(createWrapping(task));
        }
        return wrappings;
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#isOneResultAvailable()
     */
    public boolean isOneResultAvailable() {
        return aomaster.isOneResultAvailable();
    }

    /**
     * Removes the internal wrapping associated with this task
     * @param task
     * @throws IllegalArgumentException
     */
    private void removeWrapping(T task) throws IllegalArgumentException {
        if (!wrappedTasks.containsKey(task)) {
            throw new IllegalArgumentException("Non existent wrapping.");
        }

        TaskIntern wrapper = wrappedTasks.remove(task);
        wrappedTasksRev.remove(wrapper);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#slavepoolSize()
     */
    public int slavepoolSize() {
        return aomaster.slavepoolSize();
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#solveAll(java.util.Collection, boolean)
     */
    public void solveAll(Collection<T> tasks, boolean ordered) {
        Collection<TaskIntern> wrappers = createWrappings(tasks);
        aomaster.solveAll(wrappers, ordered);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#terminate(boolean)
     */
    public void terminate(boolean freeResources) {
        // we use here the synchronous version
        aomaster.terminateIntern(freeResources);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#waitAllResults()
     */
    public Collection<R> waitAllResults() throws TaskException {
        Collection<ResultIntern> resultsIntern = (Collection<ResultIntern>) ProActive.getFutureValue(aomaster.waitAllResults());
        Collection<R> results = new ArrayList<R>();
        for (ResultIntern res : resultsIntern) {
            results.add((R) res.getResult());
            T task = wrappedTasksRev.get(res.getTask());
            removeWrapping(task);
        }
        return results;
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#waitOneResult()
     */
    public R waitOneResult() throws TaskException {
        ResultIntern res = (ResultIntern) ProActive.getFutureValue(aomaster.waitOneResult());
        TaskIntern wrapper = res.getTask();

        //  we remove the mapping between the task and its wrapper
        T task = wrappedTasksRev.get(wrapper);
        removeWrapping(task);
        if (wrapper.threwException()) {
            throw new TaskException(wrapper.getException());
        }
        return (R) res.getResult();
    }

    public boolean isEmpty() {
        return aomaster.isEmpty();
    }
}
