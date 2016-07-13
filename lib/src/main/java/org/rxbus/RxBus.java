package org.rxbus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * A EventBus implements by RxJava/RxAndroid.
 * @author John Kenrinus Lee
 * @version 2016-07-10
 */
// TODO if use for-i or for-each instead rxjava-stream on next version or not?
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

    private final Subject<Object, Object> bus;
    private final ConcurrentHashMap<SubscriberKey, Set<Subscription>> subscriberMap;
    private final ConcurrentHashMap<Integer, Scheduler> customSchedulerMap;
    private boolean validateParametersMatches;

    RxBus() {
        bus = new SerializedSubject<>(PublishSubject.create());
        subscriberMap = new ConcurrentHashMap<>();
        customSchedulerMap = new ConcurrentHashMap<>();
        validateParametersMatches = true;
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
     * post a event for no null parameter, if use null parameter, no any callback happened
     * @param code event code or command code or a message type
     * @param events target callback method parameters,
     *               must ensure that any parameter cannot be null, like null, (Object[])null, (String)null
     * @see #postWithType(int, Object...)
     */
    public void post(int code, Object...events) {
        bus.onNext(new Message(code, false, events));
    }

    /**
     * post a event for which has null parameter
     * @param code event code or command code or a message type
     * @param events target callback method types and parameters, like:
     *               target: @Subscribe(..) doSomething(String a, String b)
     *               origin: post((String)null, "Lee") // don't work
     *               apply:  postWithType(String.class, null, String.class, "Lee") // work
     * @see #post(int, Object...)
     */
    public void postWithType(int code, Object...events) {
        bus.onNext(new Message(code, true, events));
    }

    /**
     * register a event/message/command receiver async
     * @param subscriber callback target, must be not null
     */
    public void register(final Object subscriber) {
        doRegister(subscriber, Schedulers.computation());
    }

    /**
     * register a event/message/command receiver sync
     * @param subscriber callback target, must be not null
     */
    public void registerSync(final Object subscriber) {
        doRegister(subscriber, Schedulers.immediate());
    }

    /**
     * cancel register a event/message/command receiver async
     * @param subscriber callback target, can be null
     */
    public void unregister(final Object subscriber) {
        doUnregister(subscriber, Schedulers.computation());
    }

    /**
     * cancel register a event/message/command receiver sync
     * @param subscriber callback target, can be null
     */
    public void unregisterSync(final Object subscriber) {
        doUnregister(subscriber, Schedulers.immediate());
    }

    private void doRegister(final Object subscriber, final Scheduler scheduler) {
        final SubscriberKey subscriberKey = new SubscriberKey(subscriber);
        if (subscriberMap.containsKey(subscriberKey)) {
            return;
        }
        final Class<?> subscriberClass = subscriber.getClass();
        Observable.from(subscriberClass.getDeclaredMethods())
            .observeOn(scheduler)
            .filter(new Func1<Method, Boolean>() {
                @Override
                public Boolean call(Method method) {
                    return method.isAnnotationPresent(Subscribe.class);
                }
            })
            .forEach(new Action1<Method>() {
                @Override
                public void call(Method method) {
                    method.setAccessible(true);
                    final Subscribe subscribe = method.getAnnotation(Subscribe.class);
                    final int code = subscribe.code();
                    final int scheduler = subscribe.scheduler();
                    final SubscribeEntry entry = new SubscribeEntry(code, scheduler,
                            subscriberClass, method, method.getParameterTypes());
                    Subscription subscription = bus.filter(new Func1<Object, Boolean>() {
                            @Override
                            public Boolean call(Object message) {
                                return Message.class.isInstance(message) && ((Message) message).code == code;
                            }
                        })
                        .observeOn(getScheduler(scheduler))
                        .subscribe(new Action1<Object>() {
                            @Override
                            public void call(Object message) {
                                onEvent((Message) message, entry, subscriber);
                            }
                        });
                    final Set<Subscription> newScriptionSet = Collections
                            .synchronizedSet(new HashSet<Subscription>());
                    final Set<Subscription> oldScriptionSet = subscriberMap
                            .putIfAbsent(subscriberKey, newScriptionSet);
                    final Set<Subscription> subscriptionSet;
                    if (oldScriptionSet != null) {
                        subscriptionSet = oldScriptionSet;
                    } else {
                        subscriptionSet = newScriptionSet;
                    }
                    subscriptionSet.add(subscription);
                }
            });
    }

    private void doUnregister(final Object subscriber, final Scheduler scheduler) {
        final SubscriberKey subscriberKey = new SubscriberKey(subscriber);
        final Set<Subscription> subscriptionSet = subscriberMap.remove(subscriberKey);
        if (subscriptionSet != null) {
            Observable.from(subscriptionSet).observeOn(scheduler).forEach(new Action1<Subscription>() {
                @Override
                public void call(Subscription subscription) {
                    subscription.unsubscribe();
                }
            });
        }
    }

    private void onEvent(Message message, SubscribeEntry subscribeEntry, Object subscriber) {
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
            subscribeEntry.method.invoke(subscriber, parameters);
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

        SubscribeEntry(int code, int scheduler, Class<?> instanceClass, Method method, Class<?>[] parametersClasses) {
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
}
