package org.gribok777.lab;

public final class Main {
    void main() {
        var configuration = Configuration.fromEnvironment();
        Configuration.withContext(configuration, () -> {
            try (var server = new Server()) {
                server.start();
            } catch (RuntimeException exception) {
                Logger.error("Server startup failed: " + exception.getMessage());
                throw exception;
            } finally {
                Logger.flush();
            }
        });
    }
}
