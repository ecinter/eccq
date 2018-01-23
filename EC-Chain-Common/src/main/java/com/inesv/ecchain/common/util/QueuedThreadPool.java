

package com.inesv.ecchain.common.util;

import java.util.concurrent.*;


public class QueuedThreadPool extends ThreadPoolExecutor {

    private int coreSize;

    private int maxSize;

    private final LinkedBlockingQueue<Runnable> pendingECQueue = new LinkedBlockingQueue<>();

    public QueuedThreadPool(int coreSize, int maxSize) {
        super(coreSize, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
        this.coreSize = coreSize;
        this.maxSize = maxSize;
    }

    @Override
    public int getCorePoolSize() {
        return coreSize;
    }

    @Override
    public void setCorePoolSize(int coreSize) {
        super.setCorePoolSize(coreSize);
        this.coreSize = coreSize;
    }

    @Override
    public int getMaximumPoolSize() {
        return maxSize;
    }

    @Override
    public void setMaximumPoolSize(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void execute(Runnable task) throws RejectedExecutionException {
        if (task == null)
            throw new NullPointerException("Null runnable passed to execute()");
        try {
            if (getActiveCount() >= maxSize) {
                pendingECQueue.put(task);
            } else {
                super.execute(task);
            }
        } catch (InterruptedException exc) {
            throw new RejectedExecutionException("Unable to queue task", exc);
        }
    }

    @Override
    public Future<?> submit(Runnable task) throws RejectedExecutionException {
        if (task == null)
            throw new NullPointerException("Null runnable passed to submit()");
        FutureTask<Void> futureTask = new FutureTask<>(task, null);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) throws RejectedExecutionException {
        if (task == null)
            throw new NullPointerException("Null runnable passed to submit()");
        FutureTask<T> futureTask = new FutureTask<>(task, result);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) throws RejectedExecutionException {
        if (callable == null)
            throw new NullPointerException("Null callable passed to submit()");
        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    @Override
    protected void afterExecute(Runnable task, Throwable exc) {
        super.afterExecute(task, exc);
        Runnable newTask = pendingECQueue.poll();
        if (newTask != null)
            super.execute(newTask);
    }
}
