package org.rxbus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A EventBus implements by RxJava/RxAndroid.
 * TODO should I support event bus cross multi-process?
 * @author John Kenrinus Lee
 * @version 2016-07-10
 */
public enum RxBus {
    singleInstance;

    private static final List<Class<?>> builtinBoxingClasses = createBuiltinBoxingClasses();
    private static final ClassSubscribeEntryCache cache = new ClassSubscribeEntryCache();

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

    private static List<SubscribeEntry> findSubscribes(final Class<?> subscriberClass) {
        if (subscriberClass == null) {
            return null;
        }
        synchronized(cache) {
            List<SubscribeEntry> subscribeEntries = cache.get(subscriberClass);
            if (subscribeEntries != null) {
                return subscribeEntries;
            } else {
                subscribeEntries = new ArrayList<>();
                final Method[] declaredMethods = subscriberClass.getDeclaredMethods();
                for (Method method : declaredMethods) {
                    if (method.isAnnotationPresent(Subscribe.class)) {
                        method.setAccessible(true);
                        final Subscribe subscribe = method.getAnnotation(Subscribe.class);
                        final int code = subscribe.code();
                        final int schedulerCode = subscribe.scheduler();
                        final SubscribeEntry entry = new SubscribeEntry(code, schedulerCode,
                                subscriberClass, method, method.getParameterTypes());
                        subscribeEntries.add(entry);
                    }
                }
                cache.put(subscriberClass, subscribeEntries);
                return subscribeEntries;
            }
        }
    }

    private final byte[] subscribeLock = new byte[0];
    private final Map<Integer, Set<SubscribeEntry>> codeSubscribeMethodsMap;
    private final Map<SubscriberKey, Set<Integer>> subscriberCodesMap;
    private final Map<SubscribeEntry, Set<SubscriberKey>> entrySubscriberMap;
    private final ConcurrentHashMap<Integer, Scheduler> customSchedulerMap;
    private final Scheduler scheduler;
    private boolean validateParametersMatches;

    RxBus() {
        codeSubscribeMethodsMap = new HashMap<>();
        subscriberCodesMap = new HashMap<>();
        entrySubscriberMap = new HashMap<>();
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

    /**
     * for fast extrac method info which with subscribe annotation, cache the relation class and methods,
     * you can set the cache size, should not be negative, the default value is 16
     */
    public void setClassCacheCapacity(int capacity) {
        if (capacity >= 0) {
            cache.lruCapacity = capacity;
        }
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
                Set<SubscriberKey> keySet;
                for (final SubscribeEntry entry : entrySet) {
                    keySet = entrySubscriberMap.get(entry);
                    if (keySet != null) {
                        for (final SubscriberKey key : keySet) {
                            getScheduler(entry.scheduler).schedule(new Runnable() {
                                @Override
                                public void run() {
                                    onEvent(message, entry, key.subscriber);
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private void doRegister(final Object subscriber) {
        synchronized(subscribeLock) {
            final SubscriberKey subscriberKey = new SubscriberKey(subscriber);
            Set<Integer> codeSet = subscriberCodesMap.get(subscriberKey);
            if (codeSet != null && !codeSet.isEmpty()) {
                return;
            }
            final List<SubscribeEntry> subscribes = findSubscribes(subscriber.getClass());
            Set<SubscribeEntry> entrySet;
            Set<SubscriberKey> keySet;
            for (SubscribeEntry entry : subscribes) {
                final int code = entry.code;
                entrySet = codeSubscribeMethodsMap.get(code);
                if (entrySet == null) {
                    entrySet = new HashSet<>();
                    codeSubscribeMethodsMap.put(code, entrySet);
                }
                entrySet.add(entry);
                codeSet = subscriberCodesMap.get(subscriberKey);
                if (codeSet == null) {
                    codeSet = new HashSet<>();
                    subscriberCodesMap.put(subscriberKey, codeSet);
                }
                codeSet.add(code);
                keySet = entrySubscriberMap.get(entry);
                if (keySet == null) {
                    keySet = new HashSet<>();
                    entrySubscriberMap.put(entry, keySet);
                }
                keySet.add(subscriberKey);
            }
        }
    }

    private void doUnregister(final Object subscriber) {
        synchronized(subscribeLock) {
            final SubscriberKey subscriberKey = new SubscriberKey(subscriber);
            final Set<Integer> codeSet = subscriberCodesMap.remove(subscriberKey);
            if (codeSet != null) {
                for (int code : codeSet) {
                    final Set<SubscribeEntry> entrySet = codeSubscribeMethodsMap.get(code);
                    if (entrySet != null) {
                        SubscribeEntry[] entries = new SubscribeEntry[0];
                        entries = entrySet.toArray(entries);
                        for (SubscribeEntry entry : entries) {
                            final Set<SubscriberKey> keySet = entrySubscriberMap.get(entry);
                            if (keySet != null) {
                                SubscriberKey[] keys = new SubscriberKey[0];
                                keys = keySet.toArray(keys);
                                for (SubscriberKey key : keys) {
                                    if (key.subscriber == subscriber) {
                                        keySet.remove(key);
                                    }
                                }
                                if (keySet.isEmpty()) {
                                    entrySubscriberMap.remove(entry);
                                    entrySet.remove(entry);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void onEvent(Message message, SubscribeEntry subscribeEntry, Object instance) {
        try {
            if (message == null || subscribeEntry == null || instance == null) {
                return;
            }
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
            subscribeEntry.method.invoke(instance, parameters);
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
        final Class<?> instanceClass;
        final Method method;
        final Class<?>[] parametersClasses;
        private final int hashCode;

        SubscribeEntry(int code, int scheduler, Class<?> instanceClass,
                       Method method, Class<?>[] parametersClasses) {
            this.code = code;
            this.scheduler = scheduler;
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
            return  31 * code + signature.hashCode();
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

    private static final class ClassSubscribeEntryCache extends LinkedHashMap<Class<?>, List<SubscribeEntry>> {
        volatile int lruCapacity = 16;
        ClassSubscribeEntryCache() {
            super(32, 0.75F, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Class<?>, List<SubscribeEntry>> eldest) {
//            System.out.println("removeEldestEntry: { " + eldest.getKey() + " : " + eldest.getValue() + " }");
            return size() > lruCapacity;
        }
    }
}
