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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author John Kenrinus Lee
 * @version 2016-07-16
 */
public final class ExecutorServiceScheduler implements Scheduler {
    private final ExecutorService executorService;

    public ExecutorServiceScheduler(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public synchronized Subscription schedule(Runnable runnable) {
        final Future<?> future = executorService.submit(runnable);
        return new FutureSubscription(future, runnable);
    }

    @Override
    public synchronized void die() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(1000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static final class FutureSubscription implements Subscription {
        private final Future<?> future;
        private final Runnable runnable;

        public FutureSubscription(Future<?> future, Runnable runnable) {
            this.future = future;
            this.runnable = runnable;
        }

        @Override
        public void unsubscribe() {
            future.cancel(true);
        }

        @Override
        public boolean isUnsubscribed() {
            return future.isCancelled();
        }
    }
}
