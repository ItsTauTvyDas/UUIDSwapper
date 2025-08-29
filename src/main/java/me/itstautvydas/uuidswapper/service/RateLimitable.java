package me.itstautvydas.uuidswapper.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public abstract class RateLimitable {
    private final transient Deque<Long> hits = new ArrayDeque<>();
    private final transient ReentrantLock lock = new ReentrantLock();

    private final transient long windowNanos = TimeUnit.MINUTES.toNanos(1);

    protected abstract Integer getMaxRequestsPerMinute();

    public boolean isRateLimited() {
        Integer max = getMaxRequestsPerMinute();
        if (max == null || max < 1)
            return false;
        var now = System.nanoTime();
        var floor = now - windowNanos;
        lock.lock();
        try {
            evictOld(floor);
            return hits.size() >= max;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean canSendRequest() {
        var max = getMaxRequestsPerMinute();
        if (max == null || max < 1)
            return true;
        var now = System.nanoTime();
        var floor = now - windowNanos;
        lock.lock();
        try {
            evictOld(floor);
            if (hits.size() < max) {
                hits.addLast(now);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public int getRemainingRequests() {
        var max = getMaxRequestsPerMinute();
        if (max == null || max < 1)
            return Integer.MAX_VALUE;
        var now = System.nanoTime();
        var floor = now - windowNanos;
        lock.lock();
        try {
            evictOld(floor);
            return Math.max(0, max - hits.size());
        } finally {
            lock.unlock();
        }
    }

    public long getTimeToWaitForNextRequest() {
        var max = getMaxRequestsPerMinute();
        if (max == null || max < 1)
            return 0L;
        var now = System.nanoTime();
        var floor = now - windowNanos;
        lock.lock();
        try {
            evictOld(floor);
            if (hits.size() < max) return 0L;
            var oldest = hits.peekFirst();
            var waitNanos = (oldest + windowNanos) - now;
            return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(waitNanos));
        } finally {
            lock.unlock();
        }
    }

    private void evictOld(long floor) {
        while (!hits.isEmpty() && hits.peekFirst() < floor)
            hits.removeFirst();
    }
}
