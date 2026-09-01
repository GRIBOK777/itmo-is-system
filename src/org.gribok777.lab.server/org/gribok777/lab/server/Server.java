package org.gribok777.lab.server;

import org.gribok777.lab.database.PostgresDatabase;
import org.gribok777.lab.logger.Logger;

import java.util.Objects;

public final class Server {
    private final Configuration configuration;
    private final PostgresDatabase database = new PostgresDatabase();

    public Server(Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public void start() {
        if (database.isConnected()) {
            throw new IllegalStateException("server is already running");
        }
        database.connect(
            configuration.databaseUrl(),
            configuration.databaseUsername(),
            configuration.databasePassword());
        Logger.info("PostgreSQL connection established");
    }

    public void stop() {
        if (!database.isConnected()) {
            return;
        }
        database.disconnect();
        Logger.info("PostgreSQL connection closed");
    }

    public boolean isRunning() {
        return database.isConnected();
    }
}
