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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;

/**
 * @author John Kenrinus Lee
 * @version 2016-08-17
 */
public class TestActivity extends Activity {
    private MyReceiver receiver;
    private Context appContext;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        appContext = getApplicationContext();
        receiver = new MyReceiver();

        final View view = findViewById(R.id.btn);
        if (view != null) {
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (receiver != null) {
                        LocalBroadcastManager.getInstance(appContext).unregisterReceiver(receiver);
                        RxBus.singleInstance.unregisterSync(receiver);
                        Callbacker.setCallback(null);
                        System.out.println("---------unregister---------------------------------");
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(appContext).registerReceiver(receiver, new IntentFilter("abc.test"));
        RxBus.singleInstance.registerSync(receiver);
        Callbacker.setCallback(receiver);
        new Thread("Testing") {
            @Override
            public void run() {
                Intent broadcast = new Intent("abc.test");
                broadcast.putExtra("From", "LocalBroadcastManager");
                Intent rxbus = new Intent("abc.test");
                rxbus.putExtra("From", "RxBus");
                Intent callback = new Intent("abc.test");
                callback.putExtra("From", "Callbacker");

                LocalBroadcastManager.getInstance(appContext).sendBroadcast(broadcast);
                RxBus.singleInstance.postAsync(40194, appContext, rxbus);
                Callbacker.post(appContext, callback);
                System.out.println("----------base line----------------------------------");
            }
        }.start();
    }
}
