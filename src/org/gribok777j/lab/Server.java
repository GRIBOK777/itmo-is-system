package org.gribok777j.lab;


public final class Server implements AutoCloseable {
    private PostgresConnectionPool connections;

    public void start() {
        if (connections != null) {
            throw new IllegalStateException("server is already running");
        }
        var configuration = Configuration.current();
        connections = new PostgresConnectionPool(
            configuration.databaseUrl(),
            configuration.databaseUsername(),
            configuration.databasePassword(),
            4);
        Logger.info("PostgreSQL connection established");
    }

    public void stop() {
        if (connections == null) {
            return;
        }
        var pool = connections;
        connections = null;
        pool.close();
        Logger.info("PostgreSQL connection closed");
    }

    public boolean isRunning() {
        return connections != null;
    }

    @Override
    public void close() {
        boolean running = isRunning();
        stop();
        if (running) {
            Logger.info("Server stopped");
        }
    }
}
