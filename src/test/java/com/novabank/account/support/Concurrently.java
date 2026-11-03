package com.novabank.account.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

public final class Concurrently {

    private Concurrently() {
    }

    /**
     * Runs the tasks on separate threads, released together so they really contend for the
     * same rows. Returns one entry per task: null on success, otherwise what it threw.
     */
    public static List<Throwable> run(int count, IntFunction<Runnable> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Runnable work = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        work.run();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            start.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> f : futures) {
                outcomes.add(f.get(2, TimeUnit.MINUTES));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
