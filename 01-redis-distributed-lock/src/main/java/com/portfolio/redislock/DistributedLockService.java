package com.portfolio.redislock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Reusable distributed locking wrapper over Redisson.
 *
 * Real-world origin: built to prevent duplicate reservation records when
 * external partners sent near-simultaneous CREATE + MODIFY events for the
 * same entity, arriving on different servers/threads. Wraps lock/unlock
 * lifecycle so calling code never has to manage it directly.
 */
@Service
public class DistributedLockService {

    private final RedissonClient redissonClient;

    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Executes the given task while holding a distributed lock on lockKey.
     * Lock is always released in a finally block, even if the task throws.
     *
     * @param lockKey    unique key identifying what's being locked (e.g. "reservation:external:12345")
     * @param waitTime   how long to wait to acquire the lock before giving up
     * @param leaseTime  how long to hold the lock before auto-releasing (safety net if the
     *                   process crashes mid-task — prevents a permanently stuck lock)
     * @param task       the critical section to execute while the lock is held
     */
    public <T> T withLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Callable<T> task) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, unit);
            if (!acquired) {
                throw new LockAcquisitionException("Could not acquire lock for key: " + lockKey);
            }
            return task.call();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted while acquiring lock for key: " + lockKey, e);
        } catch (Exception e) {
            throw new LockedTaskExecutionException("Task execution failed while holding lock: " + lockKey, e);
        } finally {
            // Only unlock if THIS thread holds it — critical: never release a lock you don't own,
            // e.g. after lease expiry another thread may have legitimately acquired it.
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** No-return-value convenience overload for side-effecting tasks. */
    public void withLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Runnable task) {
        withLock(lockKey, waitTime, leaseTime, unit, () -> {
            task.run();
            return null;
        });
    }
}
