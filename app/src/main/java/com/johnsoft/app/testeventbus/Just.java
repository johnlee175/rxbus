package com.johnsoft.app.testeventbus;

import org.rxbus.RxBus;
import org.rxbus.Subscribe;

/**
 * @author John Kenrinus Lee
 * @version 2016-07-09
 */
public class Just {
    public Just() {
        RxBus.singleInstance.register(this);
    }

    @Subscribe(code = 2, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    public void just2() {
        System.out.println("just2 ~~~~~~~~~~~~~");
    }
}
