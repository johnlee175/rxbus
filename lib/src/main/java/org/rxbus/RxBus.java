package org.rxbus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A EventBus implements by RxJava/RxAndroid.
 * @author John Kenrinus Lee
 * @version 2016-07-10
 */
public enum RxBus {
    singleInstance;

    private static final List<Class<?>> builtinBoxingClasses = createBuiltinBoxingClasses();

    private static List<Class<?>> createBuiltinBoxingClasses() {
        ArrayList<Class<?>> classes = new ArrayList<>(8);
        classes.add(Integer.class);
        classes.add(Long.class);
        classes.add(Byte.class);
        classes.add(Character.class);
        classes.add(Boolean.class);
        classes.add(Short.class);
        classes.add(Float.class);
        classes.add(Double.class);
        return Collections.unmodifiableList(classes);
    }

    private final byte[] subscribeLock = new byte[0];
    private final Map<Integer, Set<SubscribeEntry>> codeSubscribeMethodsMap;
    private final Map<SubscriberKey, Set<Integer>> subscriberCodesMap;
    private final ConcurrentHashMap<Integer, Scheduler> customSchedulerMap;
    private final Scheduler scheduler;
    private boolean validateParametersMatches;

    RxBus() {
        codeSubscribeMethodsMap = new HashMap<>();
        subscriberCodesMap = new HashMap<>();
        customSchedulerMap = new ConcurrentHashMap<>();
        scheduler = Schedulers.io();
        validateParametersMatches = true;
    }

    /** destroy resource if don't use this class again */
    public void destroySync() {
        synchronized(subscribeLock) {
            codeSubscribeMethodsMap.clear();
            subscriberCodesMap.clear();
        }
        customSchedulerMap.clear();
        scheduler.die();
    }

    /** if set true, will check parameters before call target callback method. */
    public void setValidateParametersMatches(boolean validateParametersMatches) {
        this.validateParametersMatches = validateParametersMatches;
    }

    /**
     * add a custom scheduler use for {@code org.rxbus.Subscribe#scheduler()}
     * @param schedulerId the custom scheduler type code
     * @param scheduler the Scheduler, must not be null
     */
    public void addSchedulerWithId(int schedulerId, Scheduler scheduler) {
        if (schedulerId >= Subscribe.SCHEDULER_FOR_FIRST_CUSTOM && scheduler != null) {
            customSchedulerMap.put(schedulerId, scheduler);
        }
    }

    /**
     * remove a custom scheduler had used for {@code org.rxbus.Subscribe#scheduler()}
     * @param schedulerId he custom scheduler type code
     */
    public Scheduler removeSchedulerWithId(int schedulerId) {
        return customSchedulerMap.remove(schedulerId);
    }

    /**
     * post a event sync for no null parameter, if use null parameter, no any callback happened
     * @param code event code or command code or a message type
     * @param events target callback method parameters,
     *               must ensure that any parameter cannot be null, like null, (Object[])null, (String)null
     * @see #postWithTypeSync(int, Object...)
     */
    public void postSync(final int code, final Object...events) {
        doPostMessage(new Message(code, false, events));
    }

    /**
     * post a event async for no null parameter, if use null parameter, no any callback happened
     * @param code event code or command code or a message type
     * @param events target callback method parameters,
     *               must ensure that any parameter cannot be null, like null, (Object[])null, (String)null
     * @see #postWithTypeAsync(int, Object...)
     */
    public void postAsync(final int code, final Object...events) {
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                doPostMessage(new Message(code, false, events));
            }
        });
    }

    /**
     * post a event sync for which has null parameter
     * @param code event code or command code or a message type
     * @param events target callback method types and parameters, like:
     *               target: @Subscribe(..) doSomething(String a, String b)
     *               origin: postSync((String)null, "Lee") // don't work
     *               apply:  postWithTypeSync(String.class, null, String.class, "Lee") // work
     * @see #postSync(int, Object...)
     */
    public void postWithTypeSync(final int code, final Object...events) {
        doPostMessage(new Message(code, true, events));
    }

    /**
     * post a event sync for which has null parameter
     * @param code event code or command code or a message type
     * @param events target callback method types and parameters, like:
     *               target: @Subscribe(..) doSomething(String a, String b)
     *               origin: postAsync((String)null, "Lee") // don't work
     *               apply:  postWithTypeAsync(String.class, null, String.class, "Lee") // work
     * @see #postAsync(int, Object...)
     */
    public void postWithTypeAsync(final int code, final Object...events) {
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                doPostMessage(new Message(code, true, events));
            }
        });
    }

    /**
     * register a event/message/command receiver async
     * @param subscriber callback target, must be not null
     */
    public void registerAsync(final Object subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("registerAsync: subscriber is null");
        }
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                doRegister(subscriber);
            }
        });
    }

    /**
     * register a event/message/command receiver sync
     * @param subscriber callback target, must be not null
     */
    public void registerSync(final Object subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("registerSync: subscriber is null");
        }
        doRegister(subscriber);
    }

    /**
     * cancel register a event/message/command receiver async
     * @param subscriber callback target, can be null
     */
    public void unregisterAsync(final Object subscriber) {
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                doUnregister(subscriber);
            }
        });
    }

    /**
     * cancel register a event/message/command receiver sync
     * @param subscriber callback target, can be null
     */
    public void unregisterSync(final Object subscriber) {
        doUnregister(subscriber);
    }

    private void doPostMessage(final Message message) {
        synchronized(subscribeLock) {
            final Set<SubscribeEntry> entrySet = codeSubscribeMethodsMap.get(message.code);
            if (entrySet != null) {
                for (final SubscribeEntry subscribeEntry : entrySet) {
                    getScheduler(subscribeEntry.scheduler).schedule(new Runnable() {
                        @Override
                        public void run() {
                            onEvent(message, subscribeEntry);
                        }
                    });
                }
            }
        }
    }

    private void doRegister(final Object subscriber) {
        synchronized(subscribeLock) {
            final SubscriberKey subscriberKey = new SubscriberKey(subscriber);
            Set<Integer> codes = subscriberCodesMap.get(subscriberKey);
            if (codes != null && !codes.isEmpty()) {
                return;
            }
            final Class<?> subscriberClass = subscriber.getClass();
            final Method[] declaredMethods = subscriberClass.getDeclaredMethods();
            Set<SubscribeEntry> entrySet;
            for (Method method : declaredMethods) {
                if (method.isAnnotationPresent(Subscribe.class)) {
                    method.setAccessible(true);
                    final Subscribe subscribe = method.getAnnotation(Subscribe.class);
                    final int code = subscribe.code();
                    final int schedulerCode = subscribe.scheduler();
                    final SubscribeEntry entry = new SubscribeEntry(code, schedulerCode, subscriber,
                            subscriberClass, method, method.getParameterTypes());
                    entrySet = codeSubscribeMethodsMap.get(code);
                    if (entrySet == null) {
                        entrySet = new HashSet<>();
                        codeSubscribeMethodsMap.put(code, entrySet);
                    }
                    entrySet.add(entry);
                    codes = subscriberCodesMap.get(subscriberKey);
                    if (codes == null) {
                        codes = new HashSet<>();
                        subscriberCodesMap.put(subscriberKey, codes);
                    }
                    codes.add(code);
                }
            }
        }
    }

    private void doUnregister(final Object subscriber) {
        synchronized(subscribeLock) {
            final SubscriberKey subscriberKey = new SubscriberKey(subscriber);
            final Set<Integer> codes = subscriberCodesMap.remove(subscriberKey);
            if (codes != null) {
                for (int code : codes) {
                    final Set<SubscribeEntry> entrySet = codeSubscribeMethodsMap.get(code);
                    if (entrySet != null) {
                        SubscribeEntry[] entries = new SubscribeEntry[0];
                        entries = entrySet.toArray(entries);
                        for (SubscribeEntry subscribeEntry : entries) {
                            if (subscribeEntry.instance == subscriber) {
                                entrySet.remove(subscribeEntry);
                            }
                        }
                    }
                }
            }
        }
    }

    private void onEvent(Message message, SubscribeEntry subscribeEntry) {
        try {
            final Object[] parameters;
            if (!message.isTypeInfoInParameters) {
                parameters = message.parameters;
                if (validateParametersMatches && !validateParameters(subscribeEntry, parameters, null)) {
                    // TODO throw Exception?
                    return;
                }
            } else {
                final Object[] messageParameters = message.parameters;
                if (messageParameters == null || messageParameters.length % 2 != 0) {
                    // TODO throw Exception?
                    return;
                }
                final int len = messageParameters.length / 2;
                parameters = new Object[len];
                if (!validateParametersMatches) {
                    for (int i = 0; i < len; ++i) {
                        parameters[i] = messageParameters[(i << 1) + 1];
                    }
                } else if (!validateParameters(subscribeEntry, parameters, messageParameters, len)) {
                    // TODO throw Exception?
                    return;
                }
            }
            subscribeEntry.method.invoke(subscribeEntry.instance, parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean validateParameters(SubscribeEntry subscribeEntry, Object[] parameters, Class<?>[] classParameters) {
        final Class<?>[] parametersClasses = subscribeEntry.parametersClasses;
        if (parameters == null || parametersClasses == null || parameters.length != parametersClasses.length) {
            return false;
        }
        for (int i = 0; i < parametersClasses.length; ++i) {
            final Class<?> expactClass = parametersClasses[i];
            if (parameters[i] == null) {
                if (classParameters == null || !expactClass.isAssignableFrom(classParameters[i])) {
                    return false;
                }
            } else {
                final Class<?> actualClass = parameters[i].getClass();
                if (expactClass.isPrimitive()) {
                    if (!builtinBoxingClasses.contains(actualClass)) {
                        return false;
                    }
                } else if (actualClass.isPrimitive()) {
                    if (!builtinBoxingClasses.contains(expactClass)) {
                        return false;
                    }
                } else if (!expactClass.isAssignableFrom(actualClass)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean validateParameters(SubscribeEntry subscribeEntry, Object[] parameters,
                                       Object[] messageParameters, int len) {
        final Class<?>[] classParameters = new Class<?>[len];
        for (int i = 0; i < len; ++i) {
            Object clazz = messageParameters[i << 1];
            if (clazz == null || !(clazz instanceof Class)) {
                return false;
            }
            classParameters[i] = (Class<?>)clazz;
            parameters[i] = messageParameters[(i << 1) + 1];
        }
        return validateParameters(subscribeEntry, parameters, classParameters);
    }

    private Scheduler getScheduler(int scheduler) {
        switch (scheduler) {
            case Subscribe.SCHEDULER_CURRENT_THREAD:
                return Schedulers.immediate();
            case Subscribe.SCHEDULER_NEW_THREAD:
                return Schedulers.newThread();
            case Subscribe.SCHEDULER_IO_POOL_THREAD:
                return Schedulers.io();
            case Subscribe.SCHEDULER_COMPUTE_POOL_THREAD:
                return Schedulers.computation();
            default:
                final Scheduler customScheduler = customSchedulerMap.get(scheduler);
                if (customScheduler != null) {
                    return customScheduler;
                } else {
                    throw new UnsupportedOperationException("Unknown scheduler type");
                }
        }
    }

    private static final class SubscribeEntry {
        final int code;
        final int scheduler;
        final Object instance;
        final Class<?> instanceClass;
        final Method method;
        final Class<?>[] parametersClasses;
        private final int hashCode;

        SubscribeEntry(int code, int scheduler, Object instance, Class<?> instanceClass,
                       Method method, Class<?>[] parametersClasses) {
            this.code = code;
            this.scheduler = scheduler;
            this.instance = instance;
            this.instanceClass = instanceClass;
            this.method = method;
            this.parametersClasses = parametersClasses;
            this.hashCode = calculateHashCode();
        }

        private int calculateHashCode() {
            StringBuilder sb = new StringBuilder();
            sb.append(instanceClass.getName()).append('#').append(method.getName()).append('(');
            for (Class<?> clazz : parametersClasses) {
                sb.append(clazz.getName()).append(';');
            }
            String signature = sb.append(')').toString();
            int result = 31 * code + signature.hashCode();
            return 31 * result + System.identityHashCode(instance);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SubscribeEntry that = (SubscribeEntry) o;
            return this.hashCode == that.hashCode;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class Message {
        final int code;
        final boolean isTypeInfoInParameters;
        final Object[] parameters;

        Message(int code, boolean isTypeInfoInParameters, Object[] parameters) {
            this.code = code;
            this.isTypeInfoInParameters = isTypeInfoInParameters;
            this.parameters = parameters;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Message message = (Message) o;
            return code == message.code
                    && isTypeInfoInParameters == message.isTypeInfoInParameters
                    && Arrays.equals(parameters, message.parameters);
        }

        @Override
        public int hashCode() {
            int result = code;
            result = 31 * result + (isTypeInfoInParameters ? 1 : 0);
            result = 31 * result + Arrays.hashCode(parameters);
            return result;
        }
    }

    private static final class SubscriberKey {
        final Object subscriber;

        SubscriberKey(Object subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SubscriberKey that = (SubscriberKey) o;
            return this.subscriber == that.subscriber;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(subscriber);
        }
    }
}
