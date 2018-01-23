

package com.inesv.ecchain.common.util;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class ReadWriteManager {


    private final ReentrantReadWriteLock shared_Lock = new ReentrantReadWriteLock();


    private final ReentrantLock mutex_Lock = new ReentrantLock();


    private final ThreadLocal<LockCount> lock_Count = ThreadLocal.withInitial(LockCount::new);


    private final ReadLock read_Lock = new ReadLock();


    private final UpdateLock update_Lock = new UpdateLock();


    private final WriteLock write_Lock = new WriteLock();


    public Lock readLock() {
        return read_Lock;
    }


    public Lock updateLock() {
        return update_Lock;
    }


    public Lock writeLock() {
        return write_Lock;
    }


    public interface Lock {

        /**
         * Obtain the lock
         */
        void lock();

        /**
         * Release the lock
         */
        void unlock();

        /**
         * Check if the thread holds the lock
         *
         * @return                  TRUE if the thread holds the lock
         */
        boolean hasLock();
    }


    private class ReadLock implements Lock {

        /**
         * Obtain the lock
         */
        @Override
        public void lock() {
            shared_Lock.readLock().lock();
            lock_Count.get().readCount++;
        }

        /**
         * Release the lock
         */
        @Override
        public void unlock() {
            shared_Lock.readLock().unlock();
            lock_Count.get().readCount--;
        }

        /**
         * Check if the thread holds the lock
         *
         * @return                  TRUE if the thread holds the lock
         */
        @Override
        public boolean hasLock() {
            return lock_Count.get().readCount != 0;
        }
    }


    private class UpdateLock implements Lock {

        /**
         * Obtain the lock
         *
         * Caller must not hold the read or write lock
         */
        @Override
        public void lock() {
            LockCount counts = lock_Count.get();
            if (counts.readCount != 0) {
                throw new IllegalStateException("Update lock cannot be obtained while holding the read lock");
            }
            if (counts.writeCount != 0) {
                throw new IllegalStateException("Update lock cannot be obtained while holding the write lock");
            }
            mutex_Lock.lock();
            counts.updateCount++;
        }

        /**
         * Release the lock
         */
        @Override
        public void unlock() {
            mutex_Lock.unlock();
            lock_Count.get().updateCount--;
        }

        /**
         * Check if the thread holds the lock
         *
         * @return                  TRUE if the thread holds the lock
         */
        @Override
        public boolean hasLock() {
            return lock_Count.get().updateCount != 0;
        }
    }


    private class WriteLock implements Lock {

        /**
         * Obtain the lock
         *
         * Caller must not hold the read lock
         */
        @Override
        public void lock() {
            LockCount counts = lock_Count.get();
            if (counts.readCount != 0) {
                throw new IllegalStateException("Write lock cannot be obtained while holding the read lock");
            }
            boolean lockObtained = false;
            try {
                mutex_Lock.lock();
                counts.updateCount++;
                lockObtained = true;
                shared_Lock.writeLock().lock();
                counts.writeCount++;
            } catch (Exception exc) {
                if (lockObtained) {
                    mutex_Lock.unlock();
                    counts.updateCount--;
                }
                throw exc;
            }
        }

        /**
         * Release the lock
         */
        @Override
        public void unlock() {
            LockCount counts = lock_Count.get();
            shared_Lock.writeLock().unlock();
            counts.writeCount--;
            mutex_Lock.unlock();
            counts.updateCount--;
        }

        /**
         * Check if the thread holds the lock
         *
         * @return                  TRUE if the thread holds the lock
         */
        @Override
        public boolean hasLock() {
            return lock_Count.get().writeCount != 0;
        }
    }


    private class LockCount {

        /** Read lock count */
        private int readCount;

        /** Update lock count */
        private int updateCount;

        /** Write lock count */
        private int writeCount;
    }
}
