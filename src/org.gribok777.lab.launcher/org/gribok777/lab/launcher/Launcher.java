package org.gribok777.lab.launcher;

import org.gribok777.lab.logger.Logger;
import org.gribok777.lab.server.Configuration;
import org.gribok777.lab.server.Server;

public final class Launcher {
    public static void main() {
        var configuration = Configuration.fromEnvironment();
        var server = new Server(configuration);

        try {
            server.start();
        } catch (RuntimeException exception) {
            Logger.error("Server startup failed: " + exception.getMessage());
            throw exception;
        } finally {
            boolean connected = server.isRunning();
            try {
                server.stop();
            } finally {
                if (connected) {
                    Logger.info("Server stopped");
                }
                Logger.flush();
            }
        }
    }

}
