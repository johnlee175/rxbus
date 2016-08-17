package org.rxbus;

import org.greenrobot.eventbus.EventBus;
import org.junit.*;
import org.junit.runner.*;
import org.robolectric.RobolectricTestRunner;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

/**
 * Try more times
 * @author John Kenrinus Lee
 * @version 2016-07-12
 */
// TODO test memory leak
@RunWith(RobolectricTestRunner.class)
public class TestPerform {
    private static final int LOOP = 80000;

    @Test
    public void loopRegisterUnregister() {
        System.out.println("loopRegisterUnregister-----------------------------------");
        long rxavg = 0;
        RxBus rxBus = RxBus.singleInstance;
        Common catcher1 = new Common();
        Common catcher2 = new Common();
        for (int i = 0; i < LOOP; ++i) {
            long start = System.nanoTime();
            rxBus.registerSync(catcher1);
            rxBus.registerSync(catcher2);
            rxBus.unregisterSync(catcher1);
            rxBus.unregisterSync(catcher2);
            long end = System.nanoTime();
            if (rxavg == 0) {
                rxavg = end - start;
            } else {
                rxavg = (rxavg + (end - start)) / 2;
            }
        }
        System.out.println("rxbus: " + rxavg);

        long ottoavg = 0;
        Bus bus = new Bus(ThreadEnforcer.ANY);
        Common catcher3 = new Common();
        Common catcher4 = new Common();
        for (int i = 0; i < LOOP; ++i) {
            long start = System.nanoTime();
            bus.register(catcher3);
            bus.register(catcher4);
            bus.unregister(catcher3);
            bus.unregister(catcher4);
            long end = System.nanoTime();
            if (ottoavg == 0) {
                ottoavg = end - start;
            } else {
                ottoavg = (ottoavg + (end - start)) / 2;
            }
        }
        System.out.println("otto: " + ottoavg);

        long ebavg = 0;
        EventBus eventBus = EventBus.getDefault();
        Common catcher5 = new Common();
        Common catcher6 = new Common();
        for (int i = 0; i < LOOP; ++i) {
            long start = System.nanoTime();
            eventBus.register(catcher5);
            eventBus.register(catcher6);
            eventBus.unregister(catcher5);
            eventBus.unregister(catcher6);
            long end = System.nanoTime();
            if (ebavg == 0) {
                ebavg = end - start;
            } else {
                ebavg = (ebavg + (end - start)) / 2;
            }
        }
        System.out.println("EventBus: " + ebavg);
    }

    @Test
    public void loopPost() {
        System.out.println("loopPost-----------------------------------");
        long rxavg = 0;
        Common catcher1 = new Common();
        RxBus rxBus = RxBus.singleInstance;
        rxBus.registerSync(catcher1);
        for (int i = 0; i < LOOP; ++i) {
            long start = System.nanoTime();
            rxBus.postSync(100, "Hello");
            long end = System.nanoTime();
            if (rxavg == 0) {
                rxavg = end - start;
            } else {
                rxavg = (rxavg + (end - start)) / 2;
            }
        }
        rxBus.unregisterSync(catcher1);
        System.out.println("rxbus: " + rxavg);

        long ottoavg = 0;
        Common catcher2 = new Common();
        Bus bus = new Bus(ThreadEnforcer.ANY);
        bus.register(catcher2);
        for (int i = 0; i < LOOP; ++i) {
            long start = System.nanoTime();
            bus.post("Hello");
            long end = System.nanoTime();
            if (ottoavg == 0) {
                ottoavg = end - start;
            } else {
                ottoavg = (ottoavg + (end - start)) / 2;
            }
        }
        bus.unregister(catcher2);
        System.out.println("otto: " + ottoavg);

        long ebavg = 0;
        Common catcher3 = new Common();
        EventBus eventBus = EventBus.getDefault();
        eventBus.register(catcher3);
        for (int i = 0; i < LOOP; ++i) {
            long start = System.nanoTime();
            eventBus.post("Hello");
            long end = System.nanoTime();
            if (ebavg == 0) {
                ebavg = end - start;
            } else {
                ebavg = (ebavg + (end - start)) / 2;
            }
        }
        eventBus.unregister(catcher3);
        System.out.println("EventBus: " + ebavg);
    }

    @Test
    public void loopTargetCall() {
        System.out.println("loopTargetCall-----------------------------------");
        long rxavg = 0;
        Common catcher1 = new Common();
        RxBus rxBus = RxBus.singleInstance;
        rxBus.registerSync(catcher1);
        for (int i = 0; i < LOOP; ++i) {
            catcher1.resetRx();
            rxBus.postSync(100, "Hello");
            if (rxavg == 0) {
                rxavg = catcher1.takeRx();
            } else {
                rxavg = (rxavg + catcher1.takeRx()) / 2;
            }
        }
        rxBus.unregisterSync(catcher1);
        System.out.println("rxbus: " + rxavg);

        long ottoavg = 0;
        Common catcher2 = new Common();
        Bus bus = new Bus(ThreadEnforcer.ANY);
        bus.register(catcher2);
        for (int i = 0; i < LOOP; ++i) {
            catcher2.resetOtto();
            bus.post("Hello");
            if (ottoavg == 0) {
                ottoavg = catcher2.takeOtto();
            } else {
                ottoavg = (ottoavg + catcher2.takeOtto()) / 2;
            }
        }
        bus.unregister(catcher2);
        System.out.println("otto: " + ottoavg);

        long ebavg = 0;
        Common catcher3 = new Common();
        EventBus eventBus = EventBus.getDefault();
        eventBus.register(catcher3);
        for (int i = 0; i < LOOP; ++i) {
            catcher3.resetEb();
            eventBus.post("Hello");
            if (ebavg == 0) {
                ebavg = catcher3.takeEb();
            } else {
                ebavg = (ebavg + catcher3.takeEb()) / 2;
            }
        }
        eventBus.unregister(catcher3);
        System.out.println("EventBus: " + ebavg);
    }

    public static final class Common {
        private final TimeHolder holderRx = new TimeHolder();
        private final TimeHolder holderOtto = new TimeHolder();
        private final TimeHolder holderEb = new TimeHolder();

        @Subscribe(code = 100, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
        public void rx(String event) {
            holderRx.end();
        }

        @com.squareup.otto.Subscribe
        public void otto(String event) {
            holderOtto.end();
        }

        @org.greenrobot.eventbus.Subscribe
        public void eb(String event) {
            holderEb.end();
        }

        public void resetRx() {
            holderRx.prepare();
            holderRx.start();
        }

        public long takeRx() {
            return holderRx.duration();
        }

        public void resetOtto() {
            holderOtto.prepare();
            holderOtto.start();
        }

        public long takeOtto() {
            return holderOtto.duration();
        }

        public void resetEb() {
            holderEb.prepare();
            holderEb.start();
        }

        public long takeEb() {
            return holderEb.duration();
        }
    }
}
