package org.rxbus;

import java.util.concurrent.Semaphore;

/**
 * @author John Kenrinus Lee
 * @version 2016-07-12
 */
public class TimeHolder {
    private final Semaphore semaphore = new Semaphore(2);
    private long startTime;
    private long endTime;

    public void prepare() {
        semaphore.acquireUninterruptibly(2);
    }

    public void start() {
        startTime = System.nanoTime();
        semaphore.release();
    }

    public void end() {
        endTime = System.nanoTime();
        semaphore.release();
    }

    public long duration() {
        semaphore.acquireUninterruptibly(2);
        try {
            return endTime - startTime;
        } finally {
            semaphore.release(2);
        }
    }
}
