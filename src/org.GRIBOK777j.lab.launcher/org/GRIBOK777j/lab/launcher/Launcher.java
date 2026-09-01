package org.GRIBOK777j.lab.launcher;

import org.GRIBOK777j.lab.logger.Logger;
import org.GRIBOK777j.lab.server.Configuration;
import org.GRIBOK777j.lab.server.Server;

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
