package com.portfolio.redislock;

public class LockedTaskExecutionException extends RuntimeException {
    public LockedTaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
