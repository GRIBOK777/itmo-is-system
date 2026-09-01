package org.gribok777.lab;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class PostgresConnectionPool implements AutoCloseable {
    private final BlockingQueue<PostgresConnector> idle;
    private volatile boolean closed;

    public PostgresConnectionPool(String url, String username, String password, int size) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (size < 1) {
            throw new IllegalArgumentException("pool size must be positive");
        }

        idle = new ArrayBlockingQueue<>(size);
        var created = new ArrayList<PostgresConnector>(size);
        try {
            for (int index = 0; index < size; index++) {
                var database = new PostgresConnector();
                database.connect(url, username, password);
                idle.add(database);
                created.add(database);
            }
        } catch (RuntimeException exception) {
            created.forEach(PostgresConnector::close);
            throw exception;
        }
    }

    public PostgresConnector acquire(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (closed) {
            throw new IllegalStateException("connection pool is closed");
        }

        var database = idle.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
        if (database == null) {
            throw new IllegalStateException("no database connection available");
        }
        if (closed) {
            database.close();
            throw new IllegalStateException("connection pool is closed");
        }
        return database;
    }

    public void release(PostgresConnector database) {
        Objects.requireNonNull(database, "database");
        if (closed || !database.isConnected() || !idle.offer(database)) {
            database.close();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        PostgresConnector database;
        while ((database = idle.poll()) != null) {
            database.close();
        }
    }

}
