package org.rxbus;

import java.util.ArrayList;
import java.util.List;

import org.junit.*;

/**
 * @author John Kenrinus Lee
 * @version 2016-07-12
 */
public class StringCatcher {
    public static final int EVENT_ONE = 1;
    public static final int EVENT_TWO = 2;

    private List<String> events = new ArrayList<String>();

    @Subscribe(code = EVENT_ONE, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    public void hereHaveAString(String string) {
        events.add(string);
    }

    public void methodWithoutAnnotation(String string) {
        Assert.fail("Event bus must not call methods without @Subscribe!");
    }

    public List<String> getEvents() {
        return events;
    }
}