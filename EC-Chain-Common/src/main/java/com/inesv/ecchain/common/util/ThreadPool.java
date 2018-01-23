

package com.inesv.ecchain.common.util;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ThreadPool {

    private static volatile ScheduledExecutorService scheduledExecutorService;
    private static Map<Runnable,Long> background_Jobs = new HashMap<>();
    private static List<Runnable> beforeStart_Jobs = new ArrayList<>();
    private static List<Runnable> lastBeforeStart_Jobs = new ArrayList<>();
    private static List<Runnable> afterStart_Jobs = new ArrayList<>();

    public static synchronized void runBeforeStart(Runnable runnable, boolean runLast) {
        if (scheduledExecutorService != null) {
            throw new IllegalStateException("Executor service already started");
        }
        if (runLast) {
            lastBeforeStart_Jobs.add(runnable);
        } else {
            beforeStart_Jobs.add(runnable);
        }
    }

    public static synchronized void runAfterStart(Runnable runnable) {
        afterStart_Jobs.add(runnable);
    }

    public static synchronized void scheduleThread(String name, Runnable runnable, int delay) {
        scheduleThread(name, runnable, delay, TimeUnit.SECONDS);
    }

    public static synchronized void scheduleThread(String name, Runnable runnable, int delay, TimeUnit timeUnit) {
        if (scheduledExecutorService != null) {
            throw new IllegalStateException("Executor service already started, no new jobs accepted");
        }
        if (! PropertiesUtil.getKeyForBoolean("ec.disable" + name + "Thread")) {
            background_Jobs.put(runnable, timeUnit.toMillis(delay));
        } else {
            LoggerUtil.logInfo("Will not run " + name + " thread");
        }
    }

    public static synchronized void start(int timeMultiplier) {
        if (scheduledExecutorService != null) {
            throw new IllegalStateException("Executor service already started");
        }

        LoggerUtil.logDebug("Running " + beforeStart_Jobs.size() + " tasks...");
        runAll(beforeStart_Jobs);
        beforeStart_Jobs = null;

        LoggerUtil.logDebug("Running " + lastBeforeStart_Jobs.size() + " final tasks...");
        runAll(lastBeforeStart_Jobs);
        lastBeforeStart_Jobs = null;

        LoggerUtil.logDebug("Starting " + background_Jobs.size() + " background jobs");
        scheduledExecutorService = Executors.newScheduledThreadPool(background_Jobs.size());
        for (Map.Entry<Runnable,Long> entry : background_Jobs.entrySet()) {
            scheduledExecutorService.scheduleWithFixedDelay(entry.getKey(), 0, Math.max(entry.getValue() / timeMultiplier, 1), TimeUnit.MILLISECONDS);
        }
        background_Jobs = null;

        LoggerUtil.logDebug("Starting " + afterStart_Jobs.size() + " delayed tasks");
        Thread thread = new Thread() {
            @Override
            public void run() {
                runAll(afterStart_Jobs);
                afterStart_Jobs = null;
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    public static void shutdown() {
        if (scheduledExecutorService != null) {
	        LoggerUtil.logInfo("Stopping background jobs...");
            shutdownExecutor("scheduledExecutorService", scheduledExecutorService, 10);
            scheduledExecutorService = null;
            LoggerUtil.logInfo("...Done");
        }
    }

    public static void shutdownExecutor(String name, ExecutorService executor, int timeout) {
        LoggerUtil.logInfo("shutting down " + name);
        executor.shutdown();
        try {
            executor.awaitTermination(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (! executor.isTerminated()) {
            LoggerUtil.logInfo("some threads in " + name + " didn't terminate, forcing shutdown");
            executor.shutdownNow();
        }
    }

    private static void runAll(List<Runnable> jobs) {
        List<Thread> threads = new ArrayList<>();
        final StringBuffer errors = new StringBuffer();
        for (final Runnable runnable : jobs) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        runnable.run();
                    } catch (Throwable t) {
                        errors.append(t.getMessage()).append('\n');
                        throw t;
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (errors.length() > 0) {
            throw new RuntimeException("Errors running startup tasks:\n" + errors.toString());
        }
    }

}
