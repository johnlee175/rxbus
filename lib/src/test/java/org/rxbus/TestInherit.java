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

import org.greenrobot.eventbus.EventBus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * @author John Kenrinus Lee
 * @version 2016-08-16
 */
@RunWith(RobolectricTestRunner.class)
public class TestInherit {
    public static class Parent {
        Event event;

        public void doTestEventBus() {
            EventBus.getDefault().register(this);
            Utils.postEventBus();
        }

        public void doTestRxBus() {
            RxBus.singleInstance.registerSync(this);
            Utils.postRxBus();
        }

        @org.greenrobot.eventbus.Subscribe
        public void callback(Event event) {
            synchronized (TestInherit.class) {
                this.event = event;
                TestInherit.class.notifyAll();
            }
        }

        @Subscribe(code = 10, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
        public void on(Event event) {
            synchronized (TestInherit.class) {
                this.event = event;
                TestInherit.class.notifyAll();
            }
        }

        public String getResult() {
            synchronized (TestInherit.class) {
                while (event == null) {
                    try {
                        TestInherit.class.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                return event.name + "=" + getClass();
            }
        }
    }

    public static class Child extends Parent {
    }

    public static class Event {
        public final String name;

        public Event(String name) {
            this.name = name;
        }
    }

    public static class Utils {
        public static void postEventBus() {
            new Thread("EventBus-Poster") {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    EventBus.getDefault().post(new Event("DUANG!!!"));
                }
            }.start();
        }

        public static void postRxBus() {
            new Thread("RxBus-Poster") {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    RxBus.singleInstance.postSync(10, new Event("DUANG!!!"));
                }
            }.start();
        }
    }

    @Test /* single test it */
    public void testParent() {
        Parent parent = new Parent();
        parent.doTestEventBus();
        System.out.println(parent.getResult());
    }

    @Test /* single test it */
    public void testChild() {
        Child child = new Child();
        child.doTestEventBus();
        System.out.println(child.getResult());
    }

    @Test /* single test it */
    public void testParent2() {
        Parent parent = new Parent();
        parent.doTestRxBus();
        System.out.println(parent.getResult());
    }

    @Test /* single test it */
    public void testChild2() {
        Child child = new Child();
        child.doTestRxBus();
        System.out.println(child.getResult());
    }
}
