package com.portfolio.redislock;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the bug and the fix with real concurrent execution against a real
 * Redis instance (via Testcontainers) — not a mocked/simulated lock.
 *
 * Run: mvn test (requires Docker running locally for Testcontainers)
 */
@Testcontainers
class ReservationServiceRaceConditionTest {

    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedissonClient buildRedissonClient() {
        redis.start();
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        return Redisson.create(config);
    }

    @Test
    void unsafeUpsert_underConcurrency_createsDuplicateRecords() throws InterruptedException {
        RedissonClient redisson = buildRedissonClient();
        ReservationService service = new ReservationService(new DistributedLockService(redisson));

        String externalId = "PARTNER-EXT-001";
        int concurrentRequests = 10;
        runConcurrently(concurrentRequests, () -> service.upsertReservationUnsafe(externalId));

        // BUG REPRODUCED: multiple concurrent CREATE/MODIFY events for the same
        // external ID produce MORE THAN ONE internal CRS id — exactly the
        // production issue this was built to fix.
        long distinctIds = service.getStore().values().stream().distinct().count();
        System.out.println("[UNSAFE] Distinct CRS IDs created for one externalId: " + distinctIds);
        // Intentionally not asserting == 1 here — this test exists to DEMONSTRATE
        // the bug is real under concurrency, which the safe version below fixes.
    }

    @Test
    void safeUpsert_underConcurrency_createsExactlyOneRecord() throws InterruptedException {
        RedissonClient redisson = buildRedissonClient();
        ReservationService service = new ReservationService(new DistributedLockService(redisson));

        String externalId = "PARTNER-EXT-002";
        int concurrentRequests = 10;
        runConcurrently(concurrentRequests, () -> service.upsertReservationSafe(externalId));

        long distinctIds = service.getStore().values().stream().distinct().count();
        System.out.println("[SAFE] Distinct CRS IDs created for one externalId: " + distinctIds);
        assertEquals(1, distinctIds, "Distributed lock must prevent duplicate reservation creation");
    }

    private void runConcurrently(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    task.run();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
    }
}
