/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.rxbus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author John Kenrinus Lee
 * @version 2016-07-16
 */
public final class Schedulers {
    private Schedulers() {}

    private static volatile ExecutorServiceScheduler ioScheduler;
    private static volatile ExecutorServiceScheduler computationScheduler;

    public static Scheduler immediate() {
       return new PostingScheduler();
    }

    public static Scheduler newThread() {
        return new NewThreadScheduler();
    }

    public static Scheduler io() {
        if (ioScheduler == null) {
            synchronized(Schedulers.class) {
                if (ioScheduler == null) {
                    ioScheduler = new ExecutorServiceScheduler(Executors.newCachedThreadPool());
                }
            }
        }
        return ioScheduler;
    }

    public static Scheduler computation() {
        if (computationScheduler == null) {
            synchronized(Schedulers.class) {
                if (computationScheduler == null) {
                    final int c = Runtime.getRuntime().availableProcessors() * 2 + 1;
                    computationScheduler = new ExecutorServiceScheduler(Executors.newFixedThreadPool(c));
                }
            }
        }
        return computationScheduler;
    }

    public static Scheduler from(ExecutorService executorService) {
        return new ExecutorServiceScheduler(executorService);
    }

    private static final class PostingScheduler implements Scheduler {
        @Override
        public Subscription schedule(Runnable runnable) {
            runnable.run();
            return new Subscription() {
                @Override
                public void unsubscribe() {
                    // do nothing
                }
                @Override
                public boolean isUnsubscribed() {
                    return false;
                }
            };
        }
        @Override
        public void die() {
            // do nothing
        }
    }

    private static final class NewThreadScheduler implements Scheduler {
        private Thread thread;
        @Override
        public Subscription schedule(Runnable runnable) {
            thread = new Thread(runnable, "NewThreadScheduler-Worker");
            thread.start();
            return new NewThreadSubscription(thread);
        }

        @Override
        public void die() {
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }
        }

        private static final class NewThreadSubscription implements Subscription {
            private final Thread thread;

            private NewThreadSubscription(Thread thread) {
                this.thread = thread;
            }

            @Override
            public void unsubscribe() {
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            }

            @Override
            public boolean isUnsubscribed() {
                return thread.isAlive();
            }
        }
    }
}
