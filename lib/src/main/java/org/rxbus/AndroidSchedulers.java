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

import android.os.Looper;

/**
 * @author John Kenrinus Lee
 * @version 2016-07-16
 */
public final class AndroidSchedulers {
    private AndroidSchedulers() {}

    private static volatile AndroidLooperScheduler mainScheduler;

    public static Scheduler mainThread() {
        if (mainScheduler == null) {
            synchronized(AndroidSchedulers.class) {
                if (mainScheduler == null) {
                    mainScheduler = new AndroidLooperScheduler(Looper.getMainLooper());
                }
            }
        }
        return mainScheduler;
    }

    public static Scheduler from(Looper looper) {
        return new AndroidLooperScheduler(looper);
    }
}