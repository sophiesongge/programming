/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.objectweb.proactive.core.mop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.objectweb.proactive.api.PAFuture;
import org.objectweb.proactive.core.exceptions.ExceptionHandler;
import org.objectweb.proactive.core.mop.MethodCallInfo.SynchronousReason;
import org.objectweb.proactive.core.util.converter.ObjectToByteConverter;
import org.objectweb.proactive.core.util.converter.ProActiveByteToObjectConverter;
import org.objectweb.proactive.core.util.log.Loggers;
import org.objectweb.proactive.core.util.log.ProActiveLogger;


/**
 * Instances of this class represent method calls performed on reified
 * objects. They are generated by a <I>stub object</I>, whose role is to act
 * as a representative for the reified object.
 *
 * @author The ProActive Team
 */
public class MethodCall implements java.io.Serializable, Cloneable {
    //
    // --- STATIC MEMBERS -----------------------------------------------------------------------
    //

    /**
     * The hashtable that caches Method/isAsynchronousCall
     * This dramatically improves performances, since we do not have to call
     * isAsynchronousCall for every call, but only once for a given method
     */
    private static transient java.util.Hashtable<String, ReifiableAndExceptions> REIF_AND_EXCEP = new java.util.Hashtable<String, ReifiableAndExceptions>();

    static Logger logger = ProActiveLogger.getLogger(Loggers.MOP);

    /**
     *        The size of the pool we use for recycling MethodCall objects.
     */
    private static int RECYCLE_POOL_SIZE = 30;

    /**
     * The pool of recycled methodcall objects
     */
    private static MethodCall[] recyclePool;

    /**
     * Position inside the pool
     */
    private static int index;

    /**        Indicates if the recycling of MethodCall object is on. */
    private static boolean recycleMethodCallObject;

    private static java.util.Hashtable<String, Method> reifiedMethodsTable = new java.util.Hashtable<String, Method>();

    static {
        MethodCall.setRecycleMethodCallObject(true);
    }

    //
    // --- PRIVATE MEMBERS -----------------------------------------------------------------------
    //

    /**
     * The array holding the arguments of the method call
     */
    private Object[] effectiveArguments;

    /**
     * The list of tags for barrier
     */
    private List<String> tagsForBarrier = null;

    /**
     * The method corresponding to the call
     */
    private transient Method reifiedMethod;

    private String key;

    private transient MethodCallExceptionContext exceptioncontext;

    private transient Map<TypeVariable<?>, Class<?>> genericTypesMapping = null;

    /**
     * byte[] to store effectiveArguments. Required to optimize multiple serialization
     * in some case (such as group communication) or to create a stronger
     * asynchronism (serialization of parameters then return to the thread of
     * execution before the end of the rendez-vous).
     */
    private byte[] serializedEffectiveArguments = null;

    /**
     * transform the effectiveArguments into a byte[]
     * */
    public void transformEffectiveArgumentsIntoByteArray() {
        if ((this.serializedEffectiveArguments == null) && (this.effectiveArguments != null)) {
            try {
                this.serializedEffectiveArguments = ObjectToByteConverter.MarshallStream.convert(this.effectiveArguments);
            } catch (Exception e) {
                e.printStackTrace();
            }

            this.effectiveArguments = null;
        }
    }

    /**
     * Sets recycling of MethodCall objects on/off. Note that turning the recycling
     * off and on again results in the recycling pool being flushed, thus damaging
     * performances.
     * @param value        sets the recycling on if <code>true</code>, otherwise turns it off.
     */
    public static synchronized void setRecycleMethodCallObject(boolean value) {
        if (recycleMethodCallObject == value) {
            return;
        } else {
            recycleMethodCallObject = value;
            if (value) {
                // Creates the recycle poll for MethodCall objects
                recyclePool = new MethodCall[RECYCLE_POOL_SIZE];
                index = 0;
            } else {
                // If we do not want to recycle MethodCall objects anymore,
                // let's free some memory by permitting the reyclePool to be
                // garbage-collecting
                recyclePool = null;
            }
        }
    }

    /**
     * Indicates if the recycling of MethodCall objects is currently running or not.
     *
     * @return                        <code>true</code> if recycling is on, <code>false</code> otherwise
     */
    public static synchronized boolean getRecycleMethodCallObject() {
        return MethodCall.recycleMethodCallObject;
    }

    /**
     *        Factory method for getting MethodCall objects
     *
     *        @param reifiedMethod a <code>Method</code> object that represents
     *        the method whose invocation is reified
     *        @param effectiveArguments   the effective arguments of the call. Arguments
     *        that are of primitive type need to be wrapped
     *         within an instance of the corresponding wrapper
     *  class (like <code>java.lang.Integer</code> for
     *  primitive type <code>int</code> for example).
     *        @return        a MethodCall object representing an invocation of method
     *        <code>reifiedMethod</code> with arguments <code>effectiveArguments</code>
     */
    public synchronized static MethodCall getMethodCall(Method reifiedMethod,
            Map<TypeVariable<?>, Class<?>> genericTypesMapping, Object[] effectiveArguments,
            MethodCallExceptionContext exceptioncontext) {
        exceptioncontext = MethodCallExceptionContext.optimize(exceptioncontext);

        if (MethodCall.getRecycleMethodCallObject()) {
            // Finds a recycled MethodCall object in the pool, cleans it and
            // eventually returns it
            if (MethodCall.index > 0) {
                // gets the object from the pool
                MethodCall.index--;
                MethodCall result = MethodCall.recyclePool[MethodCall.index];
                MethodCall.recyclePool[MethodCall.index] = null;
                // Refurbishes the object
                result.reifiedMethod = reifiedMethod;
                result.genericTypesMapping = genericTypesMapping;
                result.effectiveArguments = effectiveArguments;
                result.key = buildKey(reifiedMethod, genericTypesMapping);
                result.exceptioncontext = exceptioncontext;
                return result;
            }
        }

        return new MethodCall(reifiedMethod, genericTypesMapping, effectiveArguments, exceptioncontext);
    }

    public synchronized static MethodCall getMethodCall(Method reifiedMethod, Object[] effectiveArguments,
            Map<TypeVariable<?>, Class<?>> genericTypesMapping) {
        MethodCallExceptionContext exceptioncontext = ExceptionHandler.getContextForCall(reifiedMethod);
        return getMethodCall(reifiedMethod, genericTypesMapping, effectiveArguments, exceptioncontext);
    }

    /**
     *        Tells the recycling process that the MethodCall object passed as parameter
     *        is ready for recycling. It is the responsibility of the caller of this
     *        method to make sure that this object can safely be disposed of.
     */
    public synchronized static void setMethodCall(MethodCall mc) {
        if (MethodCall.getRecycleMethodCallObject()) {
            // If there's still one slot left in the pool
            if (MethodCall.recyclePool[MethodCall.index] == null) {
                // Cleans up a MethodCall object
                // It is preferable to do it here rather than at the moment
                // the object is picked out of the pool, because it allows
                // garbage-collecting the objects referenced in here                
                mc.reifiedMethod = null;
                mc.genericTypesMapping = null;
                mc.effectiveArguments = null;
                mc.tagsForBarrier = null;
                mc.key = null;
                mc.exceptioncontext = null;
                // Inserts the object in the pool
                MethodCall.recyclePool[MethodCall.index] = mc;
                MethodCall.index++;
                if (MethodCall.index == RECYCLE_POOL_SIZE) {
                    MethodCall.index = RECYCLE_POOL_SIZE - 1;
                }
            }
        }
    }

    /**
     * Builds a new MethodCall object.
     * Please, consider use the factory method  <code>getMethodCall</code>
     * instead of build a new MethodCall object.
     */

    // This constructor is private to this class
    // because we want to enforce the use of factory methods for getting fresh
    // instances of this class (see <I>Factory</I> pattern in GoF).
    public MethodCall(Method reifiedMethod, Map<TypeVariable<?>, Class<?>> genericTypesMapping,
            Object[] effectiveArguments, MethodCallExceptionContext exceptionContext) {
        this.reifiedMethod = reifiedMethod;
        this.genericTypesMapping = ((genericTypesMapping != null) && (genericTypesMapping.size() > 0))
                                                                                                       ? genericTypesMapping
                                                                                                       : null;
        this.effectiveArguments = effectiveArguments;
        this.key = buildKey(reifiedMethod, genericTypesMapping);
        this.exceptioncontext = MethodCallExceptionContext.optimize(exceptionContext);
    }

    public MethodCall(Method reifiedMethod, Map<TypeVariable<?>, Class<?>> genericTypesMapping,
            Object[] effectiveArguments) {
        this(reifiedMethod, genericTypesMapping, effectiveArguments, null);
    }

    /**
     * Builds a new MethodCall object.
     */
    protected MethodCall() {
        this.reifiedMethod = null;
        this.effectiveArguments = null;
        this.serializedEffectiveArguments = null;
        this.exceptioncontext = null;
    }

    /**
     * Builds a new MethodCall object that is a <b>shallow</b> copy of this.
     * Fields of the object are not copied.
     * Please, consider use the factory method  <code>getMethodCall</code>
     * instead of build a new MethodCall object.
     * @return a shallow copy of this
     */
    public MethodCall getShallowCopy() {
        MethodCall mc = new MethodCall();
        mc.reifiedMethod = this.getReifiedMethod();
        mc.serializedEffectiveArguments = this.serializedEffectiveArguments;
        mc.effectiveArguments = this.effectiveArguments;
        mc.genericTypesMapping = this.getGenericTypesMapping();
        mc.key = this.key;
        mc.exceptioncontext = this.exceptioncontext;
        return mc;
    }

    /**
     *        Executes the instance method call represented by this object.
     *
     * @param targetObject        the Object the method is called on
     * @throws MethodCallExecutionFailedException thrown if the reflection of the
     * call failed.
     * @throws InvocationTargetException thrown if the execution of the reified
     * method terminates abruptly by throwing an exception. The exception
     * thrown by the execution of the reified method is placed inside the
     * InvocationTargetException object.
     * @return the result of the invocation of the method. If the method returns
     * <code>void</code>, then <code>null</code> is returned. If the method
     * returned a primitive type, then it is wrapped inside the appropriate
     * wrapper object.
     */
    public Object execute(Object targetObject) throws InvocationTargetException, MethodCallExecutionFailedException {
        // A test at how non-public methods can be reflected
        if ((this.serializedEffectiveArguments != null) && (this.effectiveArguments == null)) {
            try {
                this.effectiveArguments = (Object[]) ProActiveByteToObjectConverter.MarshallStream.convert(this.serializedEffectiveArguments);
            } catch (Exception e) {
                e.printStackTrace();
            }

            this.serializedEffectiveArguments = null;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("MethodCall.execute() name = " + this.getName());
            logger.debug("MethodCall.execute() reifiedMethod = " + this.reifiedMethod);
            logger.debug("MethodCall.execute() reifiedMethod.getDeclaringClass() = " +
                         this.reifiedMethod.getDeclaringClass());
            logger.debug("MethodCall.execute() targetObject " + targetObject);
        }

        if (this.reifiedMethod.getParameterTypes().length > 0) {
            this.reifiedMethod.setAccessible(true);
        }

        try {
            targetObject = PAFuture.getFutureValue(targetObject);
            // In order to call from this class protected methods of the Active Object,
            // we need to bypass the Java Runtime security. 
            this.reifiedMethod.setAccessible(true);
            return this.reifiedMethod.invoke(targetObject, this.effectiveArguments);
        } catch (IllegalAccessException e) {
            throw new MethodCallExecutionFailedException("Access rights to the method denied: " + e);
        } catch (IllegalArgumentException e) {
            throw new MethodCallExecutionFailedException("Arguments for the method " + this.getName() +
                                                         " are invalids: " + e + "for the object " + targetObject +
                                                         "(" + targetObject.getClass().getName() + ")", e);
        }
    }

    @Override
    protected void finalize() {
        MethodCall.setMethodCall(this);
    }

    public Method getReifiedMethod() {
        return this.reifiedMethod;
    }

    /**
     * Returns the name of the method
     * @return the name of the method
     */
    public String getName() {
        return this.reifiedMethod.getName();
    }

    public int getNumberOfParameter() {
        return this.effectiveArguments.length;
    }

    public Object getParameter(int index) {
        return this.effectiveArguments[index];
    }

    public Object[] getParameters() {
        return this.effectiveArguments;
    }

    public void setEffectiveArguments(Object[] o) {
        this.effectiveArguments = o;
    }

    public Object[] getEffectiveArguments() {
        return this.effectiveArguments;
    }

    /**
     * Make a deep copy of all arguments of the constructor
     */
    public void makeDeepCopyOfArguments() throws java.io.IOException {
        this.effectiveArguments = Utils.makeDeepCopy(this.effectiveArguments);
    }

    //
    // --- PRIVATE METHODS -----------------------------------------------------------------------
    //
    private Class<?>[] fixBugRead(FixWrapper[] para) {
        Class<?>[] tmp = new Class<?>[para.length];
        for (int i = 0; i < para.length; i++) {
            //	System.out.println("fixBugRead for " + i + " value is " +para[i]);
            tmp[i] = para[i].getWrapped();
        }

        return tmp;
    }

    private FixWrapper[] fixBugWrite(Class<?>[] para) {
        FixWrapper[] tmp = new FixWrapper[para.length];
        for (int i = 0; i < para.length; i++) {
            //	System.out.println("fixBugWrite for " + i + " out of " + para.length + " value is " +para[i] );
            tmp[i] = new FixWrapper(para[i]);
        }

        return tmp;
    }

    // build a key for uniquely identifying methods, including parameterized ones
    private static String buildKey(Method reifiedMethod, Map<TypeVariable<?>, Class<?>> genericTypesMapping) {
        //TODO It seems genericTypesMapping is always an empty map, It is either useless or not correctly built.
        final StringBuilder sb = new StringBuilder((reifiedMethod.getDeclaringClass().getName()));

        // return type
        final Type returnType = reifiedMethod.getGenericReturnType();
        if ((genericTypesMapping != null) && genericTypesMapping.containsKey(returnType)) {
            sb.append(genericTypesMapping.get(returnType));
        } else {
            sb.append(returnType);
        }

        sb.append(reifiedMethod.getName());

        final Type[] parameters = reifiedMethod.getGenericParameterTypes();

        // extracting conditional test from the loop reduce runtime by 20 to 30 %
        if ((genericTypesMapping != null) && (!genericTypesMapping.isEmpty())) {
            for (Type t : parameters) {
                Class<?> gt = genericTypesMapping.get(t);
                sb.append(gt == null ? t : gt.getName());
            }
        } else {
            for (Type t : parameters) {
                sb.append(t);
            }
        }

        return sb.toString();
    }

    //
    // --- PRIVATE METHODS FOR SERIALIZATION --------------------------------------------------------------
    //
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        this.writeTheObject(out);
    }

    protected void writeTheObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        out.defaultWriteObject();
        // The Method object needs to be converted
        out.writeObject(this.reifiedMethod.getDeclaringClass());
        out.writeObject(this.reifiedMethod.getName());
        out.writeObject(fixBugWrite(this.reifiedMethod.getParameterTypes()));
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        this.readTheObject(in);
    }

    protected void readTheObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.reifiedMethod = reifiedMethodsTable.get(this.key);
        if (this.reifiedMethod == null) {
            // Reads several pieces of data that we need for looking up the method
            Class<?> declaringClass = (Class<?>) in.readObject();
            String simpleName = (String) in.readObject();
            Class<?>[] parameters = this.fixBugRead((FixWrapper[]) in.readObject());

            // Looks up the method
            try {
                this.reifiedMethod = declaringClass.getMethod(simpleName, parameters);
                reifiedMethodsTable.put(this.key, this.reifiedMethod);
            } catch (NoSuchMethodException e) {
                throw new InternalException("Lookup for method failed: " + e +
                                            ". This may be caused by having different versions of the same class on different VMs. Check your CLASSPATH settings.");
            }
        }

        if ((this.serializedEffectiveArguments != null) && (this.effectiveArguments == null)) {
            try {
                this.effectiveArguments = (Object[]) ProActiveByteToObjectConverter.MarshallStream.convert(this.serializedEffectiveArguments);
            } catch (Exception e) {
                e.printStackTrace();
            }

            this.serializedEffectiveArguments = null;
        }
    }

    /**
     * Returns a boolean saying whether the method is one-way or not.
     * Being one-way method is equivalent to <UL>
     * <LI>having <code>void</code> as return type
     * <LI>and not throwing any checked exceptions</UL>. If the caller asks
     * for a RuntimeException, then the call is not one way.
     * @return true if and only if the method call is one way
     */
    public boolean isOneWayCall() {
        return getMethodCallInfo().getType() == MethodCallInfo.CallType.OneWay;
    }

    /* Used in the REIF_AND_EXCEP cache */
    static class ReifiableAndExceptions {
        boolean reifiable; // Is the method return type reifiable ?

        boolean exceptions; // Does the method throws exceptions ?

        boolean returnsvoid; // Is the method returning void

        String reason; // Why is the method synchronous ? null if it is asynchronous
    }

    public MethodCallInfo getMethodCallInfo() {
        Method m = this.getReifiedMethod();

        ReifiableAndExceptions cached = getCachedMethodAnalysis(m);

        MethodCallInfo mci = new MethodCallInfo();

        //else
        if (!cached.reifiable) {
            mci.setType(MethodCallInfo.CallType.Synchronous);
            mci.setMessage(cached.reason);
            mci.setReason(MethodCallInfo.SynchronousReason.NotReifiable);
        } else {
            if (cached.exceptions) {
                if (getExceptionContext().isExceptionAsynchronously()) {
                    /* ProActive.tryWithCatch() is used, so this call is asynchronous */
                    mci.setType(MethodCallInfo.CallType.Asynchronous);
                } else {
                    mci.setType(MethodCallInfo.CallType.Synchronous);
                    mci.setReason(SynchronousReason.ThrowsCheckedException);
                    mci.setMessage(cached.reason);
                }
            } else if (cached.returnsvoid) {
                if (getExceptionContext().isRuntimeExceptionHandled()) {
                    mci.setType(MethodCallInfo.CallType.Asynchronous);
                } else {
                    mci.setType(MethodCallInfo.CallType.OneWay);
                }
            } else {
                mci.setType(MethodCallInfo.CallType.Asynchronous);
            }
        }

        return mci;
    }

    private ReifiableAndExceptions getCachedMethodAnalysis(Method m) {
        ReifiableAndExceptions cached = REIF_AND_EXCEP.get(this.key);
        if (cached == null) {
            cached = new ReifiableAndExceptions();

            if (m.getReturnType().equals(java.lang.Void.TYPE)) {
                cached.returnsvoid = true;
                /* void is reifiable even though the check by the MOP would tell otherwise */
                cached.reifiable = true;
            } else {
                try {
                    if ((getGenericTypesMapping() != null) &&
                        getGenericTypesMapping().containsKey(m.getGenericReturnType())) {
                        // if return type is a parameterized type, check with actual parameterizing type
                        MOP.checkClassIsReifiable(getGenericTypesMapping().get(m.getGenericReturnType()));
                    } else {
                        MOP.checkClassIsReifiable(m.getReturnType());
                    }

                    cached.reifiable = true;
                } catch (ClassNotReifiableException e) {
                    cached.reason = e.getMessage();
                }
            }

            cached.exceptions = m.getExceptionTypes().length != 0;
            if (cached.exceptions) {
                cached.reason = "The method can throw a checked exception";
            }

            REIF_AND_EXCEP.put(this.key, cached);
        }

        return cached;
    }

    /**
     * Checks if the <code>Call</code> object can be
     * processed with a future semantics, i-e if its returned object
     * can be a future object.
     *
     * Two conditions must be met : <UL>
     * <LI> The returned object is reifiable
     * <LI> The invoked method does not throw any exceptions or they are catched asynchronously
     * </UL>
     * @return true if and only if the method call is asynchronous
     */
    public boolean isAsynchronousWayCall() {
        return getMethodCallInfo().getType() == MethodCallInfo.CallType.Asynchronous;
    }

    /**
     * Set the tags for barrier to the method call (by copy)
     * @param barrierTags the list of tags
     */
    public void setBarrierTags(LinkedList<String> barrierTags) {
        this.tagsForBarrier = Collections.synchronizedList(new LinkedList<String>());
        Iterator<String> it = barrierTags.iterator();
        while (it.hasNext()) {
            this.tagsForBarrier.add(new String(it.next()));
        }
    }

    /**
     * Get the tags for barrier to the method call (by copy)
     * @return the list of barrier tags
     */
    public LinkedList<String> getBarrierTags() {
        if (this.tagsForBarrier == null) {
            return null;
        } else {
            return new LinkedList<String>(this.tagsForBarrier);
        }
    }

    public MethodCallExceptionContext getExceptionContext() {
        if (this.exceptioncontext == null) {
            return MethodCallExceptionContext.DEFAULT;
        }

        return this.exceptioncontext;
    }

    public Map<TypeVariable<?>, Class<?>> getGenericTypesMapping() {
        return this.genericTypesMapping;
    }

    //
    // --- INNER CLASSES -----------------------------------------------------------------------
    //
    public class FixWrapper implements java.io.Serializable {
        public boolean isPrimitive;

        public Class<?> encapsulated;

        public FixWrapper() {
        }

        /**
         * Encapsulate primitives types into Class<?>
         */
        public FixWrapper(Class<?> c) {
            if (!c.isPrimitive()) {
                this.encapsulated = c;
                return;
            }

            this.isPrimitive = true;
            if (c.equals(Boolean.TYPE)) {
                this.encapsulated = Boolean.class;
            } else if (c.equals(Byte.TYPE)) {
                this.encapsulated = Byte.class;
            } else if (c.equals(Character.TYPE)) {
                this.encapsulated = Character.class;
            } else if (c.equals(Double.TYPE)) {
                this.encapsulated = Double.class;
            } else if (c.equals(Float.TYPE)) {
                this.encapsulated = Float.class;
            } else if (c.equals(Integer.TYPE)) {
                this.encapsulated = Integer.class;
            } else if (c.equals(Long.TYPE)) {
                this.encapsulated = Long.class;
            } else if (c.equals(Short.TYPE)) {
                this.encapsulated = Short.class;
            }
        }

        /**
         * Give back the original class
         */
        public Class<?> getWrapped() {
            if (!this.isPrimitive) {
                return this.encapsulated;
            }

            if (this.encapsulated.equals(Boolean.class)) {
                return Boolean.TYPE;
            }

            if (this.encapsulated.equals(Byte.class)) {
                return Byte.TYPE;
            }

            if (this.encapsulated.equals(Character.class)) {
                return Character.TYPE;
            }

            if (this.encapsulated.equals(Double.class)) {
                return Double.TYPE;
            }

            if (this.encapsulated.equals(Float.class)) {
                return Float.TYPE;
            }

            if (this.encapsulated.equals(Integer.class)) {
                return Integer.TYPE;
            }

            if (this.encapsulated.equals(Long.class)) {
                return Long.TYPE;
            }

            if (this.encapsulated.equals(Short.class)) {
                return Short.TYPE;
            }

            throw new InternalException("FixWrapper encapsulated class unkown " + this.encapsulated);
        }

        @Override
        public String toString() {
            return "FixWrapper: " + this.encapsulated.toString();
        }
    }

    // end inner class FixWrapper
}
