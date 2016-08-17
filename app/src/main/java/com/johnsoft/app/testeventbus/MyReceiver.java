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

import org.rxbus.Subscribe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * @author John Kenrinus Lee
 * @version 2016-08-17
 */
public class MyReceiver extends BroadcastReceiver implements Callbacker.Callback {
    @Subscribe(code = 40194, scheduler = Subscribe.SCHEDULER_NEW_THREAD)
    public void onReceive(Context context, Intent intent) {
        System.out.println("$$$$$$$$$$$$$$" + intent.getStringExtra("From"));
    }
}
