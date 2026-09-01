package org.gribok777.lab;

import java.util.Map;
import java.util.Objects;

public record Configuration(String databaseUrl, String databaseUsername, String databasePassword) {
    public static final ScopedValue<Configuration> CONTEXT = ScopedValue.newInstance();

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5432;
    private static final String DEFAULT_DATABASE = "lab";
    private static final String DEFAULT_USERNAME = "lab";
    private static final String DEFAULT_PASSWORD = "lab";

    public Configuration {
        Objects.requireNonNull(databaseUrl, "databaseUrl");
        Objects.requireNonNull(databaseUsername, "databaseUsername");
        databasePassword = Objects.requireNonNull(databasePassword, "databasePassword");
        if (databaseUrl.isBlank() || databaseUsername.isBlank()) {
            throw new IllegalArgumentException("database URL and username must not be blank");
        }
    }

    public static Configuration fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static Configuration fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        String host = environment.get("POSTGRES_HOST");
        if (host == null || host.isBlank()) {
            host = DEFAULT_HOST;
        }
        String portValue = environment.get("POSTGRES_PORT");
        int port = portValue == null || portValue.isBlank() ? DEFAULT_PORT : port(portValue);
        String database = environment.get("POSTGRES_DB");
        if (database == null || database.isBlank()) {
            database = DEFAULT_DATABASE;
        }
        String url = environment.get("POSTGRES_URL");
        String username = environment.get("POSTGRES_USER");
        if (username == null || username.isBlank()) {
            username = DEFAULT_USERNAME;
        }
        String password = environment.get("POSTGRES_PASSWORD");
        if (password == null) {
            password = DEFAULT_PASSWORD;
        }
        if (url == null || url.isBlank()) {
            var connectionInfo = new StringBuilder(host.length() + database.length() + 24);
            connectionInfo.append("host=");
            appendQuoted(connectionInfo, host);
            connectionInfo.append(" port=").append(port).append(" dbname=");
            appendQuoted(connectionInfo, database);
            url = connectionInfo.toString();
        }
        return new Configuration(
            url,
            username,
            password);
    }

    public static void withContext(Configuration configuration, Runnable task) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(task, "task");
        ScopedValue.where(CONTEXT, configuration).run(task);
    }

    public static Configuration current() {
        return CONTEXT.orElseThrow(() -> new IllegalStateException("configuration context is not bound"));
    }

    @Override
    public String toString() {
        return "Configuration[databaseUrl=" + databaseUrl
            + ", databaseUsername=" + databaseUsername
            + ", databasePassword=<redacted>]";
    }

    private static int port(String value) {
        try {
            return switch (Integer.parseInt(value)) {
                case int port when port >= 1 && port <= 65_535 -> port;
                case int _ -> throw new IllegalArgumentException(
                    "POSTGRES_PORT must be between 1 and 65535");
            };
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("POSTGRES_PORT must be a number");
        }
    }

    private static void appendQuoted(StringBuilder output, String value) {
        output.append('\'');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '\'') {
                output.append('\\');
            }
            output.append(character);
        }
        output.append('\'');
    }
}
