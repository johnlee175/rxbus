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

import android.os.Handler;
import android.os.Looper;

/**
 * @author John Kenrinus Lee
 * @version 2016-07-16
 */
public final class AndroidLooperScheduler implements Scheduler {
    private final Handler handler;

    public AndroidLooperScheduler(Looper looper) {
        this.handler = new Handler(looper);
    }

    @Override
    public Subscription schedule(Runnable runnable) {
        if (handler.post(runnable)) {
            return new AndroidHandlerSubscription(handler, runnable);
        } else {
            return null;
        }
    }

    @Override
    public void die() {
        handler.getLooper().quit();
    }

    public static final class AndroidHandlerSubscription implements Subscription {
        private final Handler handler;
        private final Runnable runnable;

        public AndroidHandlerSubscription(Handler handler, Runnable runnable) {
            this.handler = handler;
            this.runnable = runnable;
        }

        @Override
        public void unsubscribe() {
            handler.removeCallbacks(runnable);
        }

        @Override
        public boolean isUnsubscribed() {
            throw new UnsupportedOperationException("Not Implement method: isUnsubscribed");
        }
    }
}
