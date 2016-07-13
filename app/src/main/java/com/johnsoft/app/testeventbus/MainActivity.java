package com.johnsoft.app.testeventbus;

import org.rxbus.RxBus;
import org.rxbus.Subscribe;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private Just olds;
    private Just news;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RxBus.singleInstance.register(this);
        RxBus.singleInstance.register(this);
        olds = new Just();
        news = new Just();
    }

    @Override
    protected void onPause() {
        super.onPause();
        RxBus.singleInstance.unregister(this);
        RxBus.singleInstance.unregister(this);
    }

    @Subscribe(code = 2, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    public void onCode2() {
        System.out.println("onCode2: ");
    }

    @Subscribe(code = 3, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    public void onCode3(String message) {
        System.out.println("onCode3: " + message);
    }

    @Subscribe(code = 4, scheduler = Subscribe.SCHEDULER_CURRENT_THREAD)
    private void onCode4(String message, int timeout) {
        System.out.println("onCode4: " + message + ", " + timeout);
    }
}
