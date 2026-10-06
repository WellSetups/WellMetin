package com.wellsetups.wellmetin.persistence;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One connection owner; queue overflow fails the operation instead of blocking a tick thread. */
final class DatabaseExecutor {
    @FunctionalInterface
    interface Work<T> { T run() throws java.sql.SQLException; }

    private final ThreadPoolExecutor executor;

    DatabaseExecutor(int capacity) {
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), run -> {
            Thread thread = new Thread(run, "WellMetin-database");
            thread.setDaemon(false);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    <T> CompletableFuture<T> submit(Work<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(work.run());
                } catch (java.sql.SQLException | RuntimeException failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    CompletableFuture<Void> shutdown(Runnable cleanup) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        // Shutdown may drain on this worker, but never waits on a server/region thread.
        Thread closer = new Thread(() -> {
            executor.shutdown();
            try {
                while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                }
                cleanup.run();
                completion.complete(null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                completion.completeExceptionally(interrupted);
            } catch (RuntimeException failure) {
                completion.completeExceptionally(failure);
            }
        }, "WellMetin-database-close");
        closer.setDaemon(false);
        closer.start();
        return completion;
    }
}
