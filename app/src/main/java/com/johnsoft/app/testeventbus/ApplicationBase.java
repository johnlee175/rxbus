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

import org.rxbus.RxBus;
import org.rxbus.Subscribe;

import android.app.Application;

/**
 * @author John Kenrinus Lee
 * @version 2016-07-09
 */
public class ApplicationBase extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RxBus.singleInstance.register(this);
        new Thread("delay") {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                RxBus.singleInstance.post(3, "zhangsan is boy");
                RxBus.singleInstance.post(4, "Welcome", 220);
                RxBus.singleInstance.post(2);
                RxBus.singleInstance.unregister(ApplicationBase.this);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                RxBus.singleInstance.post(3, "lisi is boy");
                RxBus.singleInstance.post(2);
                RxBus.singleInstance.post(4, "Welcome", 220);
            }
        }.start();
    }

    @Subscribe(code = 4, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    public void ifWelcome(String message, int timeout) {
        System.out.println("ifWelcome: " + message + ", " + timeout);
    }
}
