package org.gribok777.lab;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class PostgresConnectionPool implements AutoCloseable {
    private final BlockingQueue<PostgresConnector> idle;
    private final String url;
    private final String username;
    private final String password;
    private volatile boolean closed;

    public PostgresConnectionPool(String url, String username, String password, int size) {
        this.url = Objects.requireNonNull(url, "url");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        if (size < 1) {
            throw new IllegalArgumentException("pool size must be positive");
        }

        idle = new ArrayBlockingQueue<>(size);
        try {
            for (int index = 0; index < size; index++) {
                idle.add(newConnection());
            }
        } catch (Throwable failure) {
            closeAfterFailure(failure);
            throw propagate(failure);
        }
    }

    public <T> T use(Function<? super PostgresConnector, ? extends T> action, Duration timeout) {
        Objects.requireNonNull(action, "action");
        var connection = borrow(timeout);
        try {
            return action.apply(connection);
        } finally {
            giveBack(connection);
        }
    }

    private PostgresConnector borrow(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (closed) {
            throw new IllegalStateException("connection pool is closed");
        }

        PostgresConnector connection;
        try {
            connection = idle.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted while waiting for a connection", exception);
        }
        if (connection == null) {
            throw new IllegalStateException("no database connection available");
        }
        if (closed) {
            connection.close();
            throw new IllegalStateException("connection pool is closed");
        }
        if (!connection.isHealthy()) {
            connection.close();
            connection = newConnection();
            if (closed) {
                connection.close();
                throw new IllegalStateException("connection pool is closed");
            }
        }
        return connection;
    }

    private void giveBack(PostgresConnector connection) {
        if (closed || !connection.isHealthy() || !idle.offer(connection)) {
            connection.close();
        }
    }

    private PostgresConnector newConnection() {
        var connection = new PostgresConnector();
        connection.connect(url, username, password);
        return connection;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        PostgresConnector connection;
        while ((connection = idle.poll()) != null) {
            connection.close();
        }
    }

    private void closeAfterFailure(Throwable failure) {
        closed = true;
        PostgresConnector connection;
        while ((connection = idle.poll()) != null) {
            try {
                connection.close();
            } catch (Throwable closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("failed to initialize connection pool", failure);
    }
}
