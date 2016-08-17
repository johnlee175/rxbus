package com.johnsoft.app.testeventbus;

import org.rxbus.RxBus;
import org.rxbus.Subscribe;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {
    private Just olds;
    private Just news;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RxBus.singleInstance.registerSync(this);
        RxBus.singleInstance.registerSync(this);
        olds = new Just();
        news = new Just();
        final View btn = findViewById(R.id.clickme);
        if (btn != null) {
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, TestActivity.class));
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        RxBus.singleInstance.unregisterSync(this);
        RxBus.singleInstance.unregisterSync(this);
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
