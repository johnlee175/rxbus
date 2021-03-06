package org.rxbus;

import static junit.framework.Assert.fail;
import static org.junit.Assert.*;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.junit.runner.*;
import org.robolectric.RobolectricTestRunner;

import rx.schedulers.Schedulers;

/**
 * @author John Kenrinus Lee
 * @version 2016-07-11
 */
@RunWith(RobolectricTestRunner.class)
public class TestRxBus {
    static final long TIME = 300L;
    static final int CODE = 1094922;
    static boolean flag;
    static boolean beforeExecute;

    @Test
    public void doNormalTest() throws Exception {
        StringCatcher catcher = new StringCatcher();
        RxBus.singleInstance.register(catcher);
        Thread.sleep(TIME); // because of register is async
        RxBus.singleInstance.post(StringCatcher.EVENT_ONE, "Hello World");
        assertEquals("Only one String should be delivered.", 1, catcher.getEvents().size());
        assertEquals("Hello World", catcher.getEvents().get(0));
        RxBus.singleInstance.post(StringCatcher.EVENT_ONE, "Welcome");
        assertEquals("Only two String should be delivered.", 2, catcher.getEvents().size());
        assertEquals("Welcome", catcher.getEvents().get(1));
        RxBus.singleInstance.register(catcher); // duplicate register
        Thread.sleep(TIME); // because of register is async
        RxBus.singleInstance.post(StringCatcher.EVENT_ONE, "Shanghai");
        assertEquals("Only three String should be delivered.", 3, catcher.getEvents().size());
        assertEquals("Shanghai", catcher.getEvents().get(2));
        StringCatcher catcher1 = new StringCatcher();
        RxBus.singleInstance.register(catcher1); // the twice instance
        Thread.sleep(TIME); // because of register is async
        RxBus.singleInstance.post(StringCatcher.EVENT_ONE, "Guangzhou");
        assertEquals(1, catcher1.getEvents().size());
        assertEquals("Guangzhou", catcher1.getEvents().get(0));
        int count = 0;
        for (String string : catcher.getEvents()) {
            if (string.equals("Guangzhou")) {
                ++count;
            }
        }
        assertEquals("Only one Guangzhou in catcher", 1, count);
        StringCatcher catcher2 = new StringCatcher() {
            @Subscribe(code = EVENT_TWO, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
            public void str2(String push) {
                getEvents().add(push);
            }
            @Subscribe(code = EVENT_TWO, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
            public void string2(String push) {
                getEvents().add(push);
            }
        }; // inner class
        RxBus.singleInstance.register(catcher2); // two target method in one instance
        Thread.sleep(TIME); // because of register is async
        RxBus.singleInstance.post(StringCatcher.EVENT_TWO, "Two target");
        assertEquals(2, catcher2.getEvents().size());
        assertEquals("Two target", catcher2.getEvents().get(0));
        assertEquals("Two target", catcher2.getEvents().get(1));
        // test multi-param and private method and callback from io thread
        ParamsFetcher paramsFetcher = new ParamsFetcher();
        RxBus.singleInstance.register(paramsFetcher);
        Thread.sleep(TIME); // because of register is async
        RxBus.singleInstance.post(ParamsFetcher.EVENT, "Zhang san", 22, true);
        assertEquals(null, paramsFetcher.getName());
        Thread.sleep(TIME); // because of onParam is async
        assertEquals("Zhang san", paramsFetcher.getName());
        assertEquals(22, paramsFetcher.getAge());
        assertEquals(true, paramsFetcher.isStudent());
        // test unregister
        flag = false;
        Object object = new Object() {
            @Subscribe(code = CODE, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
            public void haha() {
                flag = true;
            }
        };
        RxBus.singleInstance.register(object);
        Thread.sleep(TIME); // because of register is async
        RxBus.singleInstance.post(CODE);
        assertEquals(true, flag);
        flag = false;
        RxBus.singleInstance.unregister(object);
        Thread.sleep(TIME); // because of unregister is async
        RxBus.singleInstance.post(CODE);
        assertEquals(false, flag);
        flag = false;
        RxBus.singleInstance.register(object);
        RxBus.singleInstance.post(CODE);
        assertEquals(false, flag); // message discard because of register finish yet
        Thread.sleep(TIME); // because of register is async
        RxBus.singleInstance.post(CODE);
        assertEquals(true, flag);
        // test sync
        RxBus.singleInstance.unregisterSync(object);
        flag = false;
        RxBus.singleInstance.post(CODE);
        assertEquals(false, flag);
        Thread.sleep(TIME);
        RxBus.singleInstance.post(CODE);
        assertEquals(false, flag);
        flag = false;
        RxBus.singleInstance.registerSync(object);
        RxBus.singleInstance.post(CODE); // no sleep, because of sync
        assertEquals(true, flag);
        // test custom scheduler
        flag = false;
        beforeExecute = false;
        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>()) {
            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                beforeExecute = true;
            }
        };
        RxBus.singleInstance.addSchedulerWithId(Subscribe.SCHEDULER_FOR_FIRST_CUSTOM + 1,
                Schedulers.from(poolExecutor));
        Object customSchedulerObj = new Object() {
            @Subscribe(code = 45530, scheduler = Subscribe.SCHEDULER_FOR_FIRST_CUSTOM + 1)
            public void custom() {
                flag = true;
            }
        };
        RxBus.singleInstance.registerSync(customSchedulerObj);
        RxBus.singleInstance.post(45530);
        Thread.sleep(TIME);
        assertEquals(true, flag);
        assertEquals(true, beforeExecute);
    }

    @Test
    public void doExceptionTest() {
        try {
            RxBus.singleInstance.register(null);
            fail("Should have thrown an NullPointerException on register.");
        } catch (NullPointerException e) {
        }
        try {
            RxBus.singleInstance.registerSync(null);
            fail("Should have thrown an NullPointerException on register.");
        } catch (NullPointerException e) {
        }
        try {
            RxBus.singleInstance.unregister(null);
        } catch (NullPointerException e) {
            fail("Should not have thrown an NullPointerException on unregister.");
        }
        try {
            RxBus.singleInstance.unregisterSync(null);
        } catch (NullPointerException e) {
            fail("Should not have thrown an NullPointerException on unregister.");
        }
        AmbiguousFetcher fetcher = new AmbiguousFetcher();
        RxBus.singleInstance.registerSync(fetcher);
        fetcher.setFromString(null);
        RxBus.singleInstance.post(AmbiguousFetcher.AMBIGUOUS, null);
        assertEquals(null, fetcher.getFromString());
        RxBus.singleInstance.post(AmbiguousFetcher.AMBIGUOUS, (Object[])null);
        assertEquals(null, fetcher.getFromString());
        RxBus.singleInstance.post(AmbiguousFetcher.AMBIGUOUS, (String)null);
        assertEquals(null, fetcher.getFromString());
        fetcher.setFromString(null);
        RxBus.singleInstance.postWithType(AmbiguousFetcher.AMBIGUOUS, String.class, null);
        assertEquals(true, fetcher.getFromString());
        fetcher.setFromString(null);
        RxBus.singleInstance.postWithType(AmbiguousFetcher.AMBIGUOUS, Integer.class, null);
        assertEquals(false, fetcher.getFromString());
        fetcher.setFromString(null);
        RxBus.singleInstance.postWithType(AmbiguousFetcher.AMBIGUOUS, Object[].class, null);
        assertEquals(null, fetcher.getFromString());
        fetcher.setFromString(null);
        RxBus.singleInstance.post(AmbiguousFetcher.AMBIGUOUS, 3);
        assertEquals(false, fetcher.getFromString());
        fetcher.setFromString(null);
        RxBus.singleInstance.post(AmbiguousFetcher.AMBIGUOUS, 3.0f);
        assertEquals(null, fetcher.getFromString());
        fetcher.setFromString(null);
        RxBus.singleInstance.post(AmbiguousFetcher.AMBIGUOUS, "DAO");
        assertEquals(true, fetcher.getFromString());
        RxBus.singleInstance.post(AmbiguousFetcher.NULL, null, "Lee");
        assertEquals("a", fetcher.getA());
        assertEquals("b", fetcher.getB());
        RxBus.singleInstance.postWithType(AmbiguousFetcher.NULL, String.class, null, String.class, "Lee");
        assertEquals(null, fetcher.getA());
        assertEquals("Lee", fetcher.getB());
    }

    private static class ParamsFetcher {
        public static final int EVENT = -20309;
        private String name;
        private int age;
        private boolean isStudent;
        @Subscribe(code = EVENT, scheduler = Subscribe.SCHEDULER_IO_POOL_THREAD)
        private void onParam(String name, int age, boolean isStudent) {
            this.name = name;
            this.age = age;
            this.isStudent = isStudent;
        }

        public int getAge() {
            return age;
        }

        public String getName() {
            return name;
        }

        public boolean isStudent() {
            return isStudent;
        }
    }
}

/**
 * @author John Kenrinus Lee
 * @version 2016-07-12
 */
class AmbiguousFetcher {
    public static final int AMBIGUOUS = -99;
    public static final int NULL = -999;

    private Boolean fromString = null;
    private String a = "a";
    private String b = "b";

    @Subscribe(code = AMBIGUOUS, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    public void doSomething(String string) {
        fromString = true;
    }

    @Subscribe(code = AMBIGUOUS, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    public void doSomething(Integer integer) {
        fromString = false;
    }

    @Subscribe(code = NULL, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    public void doSomething(String a, String b) {
        this.a = a;
        this.b = b;
    }

    public Boolean getFromString() {
        return fromString;
    }

    public void setFromString(Boolean fromString) {
        this.fromString = fromString;
    }

    public String getA() {
        return a;
    }

    public String getB() {
        return b;
    }
}
