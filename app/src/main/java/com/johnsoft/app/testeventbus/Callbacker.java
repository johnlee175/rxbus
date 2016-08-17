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
package com.johnsoft.app.testeventbus;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * @author John Kenrinus Lee
 * @version 2016-08-17
 */
public final class Callbacker {
    private static Handler handler;
    private static Callback callback;

    private Callbacker() {}

    public static void post(final Context context, final Intent intent) {
        if (handler != null) {
            System.out.println("worker-thread========================");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onReceive(context, intent);
                    }
                }
            });
        } else {
            System.out.println("current-thread========================");
            if (callback != null) {
                callback.onReceive(context, intent);
            }
        }
    }

    public static void setCallback(Callback pCallback) {
        callback = pCallback;
        if (callback == null && handler != null) {
            handler.getLooper().quit();
            handler = null;
        } else if (callback != null && handler == null) {
            HandlerThread handlerThread = new HandlerThread("MyReceiver");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        } else {
            System.out.println("No-Op===========================");
        }
    }

    public interface Callback {
        void onReceive(Context context, Intent intent);
    }
}
