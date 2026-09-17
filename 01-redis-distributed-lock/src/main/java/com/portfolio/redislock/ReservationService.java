package com.portfolio.redislock;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates the real production problem: external partners send CREATE and
 * MODIFY events for the same reservation near-simultaneously. Without locking,
 * both can pass the "does this exist?" check before either writes, producing
 * two records for what should be one reservation.
 */
@Service
public class ReservationService {

    // Simulates the reservations "table" — externalId -> internal CRS id
    private final Map<String, String> reservationStore = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);
    private final DistributedLockService lockService;

    public ReservationService(DistributedLockService lockService) {
        this.lockService = lockService;
    }

    /**
     * UNSAFE version — reproduces the original bug.
     * Two threads can both pass the containsKey check before either calls put(),
     * creating two internal records for the same externalId.
     */
    public String upsertReservationUnsafe(String externalId) {
        if (!reservationStore.containsKey(externalId)) {
            simulateProcessingDelay();
            String crsId = "CRS-" + idGenerator.getAndIncrement();
            reservationStore.put(externalId, crsId);
            return crsId;
        }
        return reservationStore.get(externalId);
    }

    /**
     * SAFE version — the actual fix. Locking scoped tightly around just the
     * check-and-write, not broader validation/computation, to minimize
     * contention time (mirrors the real production design: lock only wraps
     * the write, since validation/computation was already done beforehand).
     */
    public String upsertReservationSafe(String externalId) {
        return lockService.withLock(
                "reservation:external:" + externalId,
                5, 10, TimeUnit.SECONDS,
                () -> {
                    if (!reservationStore.containsKey(externalId)) {
                        simulateProcessingDelay();
                        String crsId = "CRS-" + idGenerator.getAndIncrement();
                        reservationStore.put(externalId, crsId);
                        return crsId;
                    }
                    return reservationStore.get(externalId);
                }
        );
    }

    private void simulateProcessingDelay() {
        try {
            // Simulates real-world processing time between the existence check
            // and the write — this is the window where the race condition occurs.
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getDistinctCrsIdCount(String externalId) {
        return (int) reservationStore.values().stream().distinct().count();
    }

    public Map<String, String> getStore() {
        return reservationStore;
    }
}
