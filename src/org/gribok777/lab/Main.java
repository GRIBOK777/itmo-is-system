package org.gribok777.lab;

public final class Main {
    public static void main() {
        var configuration = Configuration.fromEnvironment();
        var database = new PostgresDatabase();

        try {
            database.connect(
                    configuration.databaseUrl(),
                    configuration.databaseUsername(),
                    configuration.databasePassword());
        } finally {
            database.disconnect();
            Logger.flush();
        }
    }

}
