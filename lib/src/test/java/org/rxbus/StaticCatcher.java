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

/**
 * @author John Kenrinus Lee
 * @version 2016-07-28
 */
public class StaticCatcher {
    public static final int EVENT = -800820;
    public static String staticString = "";

    @Subscribe(code = EVENT, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    public static void onStatic(String str) {
        staticString = "StaticCatcher" + str;
    }
}