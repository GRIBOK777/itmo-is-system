package org.gribok777.lab.test;

import org.gribok777.lab.logger.Logger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class LoggerTest {
    private LoggerTest() {
    }

    public static void run() {
        defaultLevelFiltersDebug();
        scopedLevelControlsOutput();
        scopedLevelsNestAndReturnValues();
        threadTypeIsLogged();
        utf8MessagesArePreserved();
        invalidArgumentsAreRejected();
        System.out.println("[OK] " + LoggerTest.class.getSimpleName() + " passed all tests.");
    }

    private static void defaultLevelFiltersDebug() {
        String output = capture(() -> {
            Logger.debug("hidden");
            Logger.info("shown");
            Logger.error("failure");
        });

        check(!output.contains("hidden"), "default level must filter debug messages");
        check(output.contains("[INFO]"), "info message must be written");
        check(output.contains("[ERROR]"), "error message must be written");
    }

    private static void scopedLevelControlsOutput() {
        String output = capture(() -> Logger.withLevel(Logger.LogLevel.DEBUG,
            () -> Logger.debug("visible")));

        check(output.contains("[DEBUG]") && output.contains("visible"),
            "scoped debug level must enable debug messages");
    }

    private static void scopedLevelsNestAndReturnValues() {
        String output = capture(() -> Logger.withLevel(Logger.LogLevel.ERROR, () -> {
            Logger.info("outer-hidden");
            Logger.withLevel(Logger.LogLevel.DEBUG, () -> Logger.info("inner-visible"));
            Logger.info("outer-hidden-again");
        }));

        check(!output.contains("outer-hidden"), "nested scope must restore outer level");
        check(output.contains("inner-visible"), "nested scope must use its own level");
        String value = Logger.withLevel(Logger.LogLevel.DEBUG, () -> "returned");
        check(Objects.equals("returned", value), "supplier result must be returned");
    }

    private static void invalidArgumentsAreRejected() {
        expectNullPointer(() -> Logger.withLevel(null, () -> { }));
        expectNullPointer(() -> Logger.withLevel(Logger.LogLevel.INFO, (Runnable) null));
        expectNullPointer(() -> Logger.withLevel(null, () -> "value"));
        expectNullPointer(() -> Logger.withLevel(Logger.LogLevel.INFO, (java.util.function.Supplier<String>) null));
    }

    private static void threadTypeIsLogged() {
        String platformOutput = capture(() -> Logger.info("platform"));
        check(platformOutput.contains("(p) platform"), "platform thread must be marked with (p)");

        String virtualOutput = capture(() -> {
            Thread thread = Thread.ofVirtual().start(() -> Logger.info("virtual"));
            join(thread);
        });
        check(virtualOutput.contains("(v) virtual"), "virtual thread must be marked with (v)");
    }

    private static void utf8MessagesArePreserved() {
        String message = "Привет, мир! 你好 🌍";
        String output = capture(() -> Logger.info(message));

        check(output.contains(message), "UTF-8 message must be preserved");
    }

    private static String capture(Runnable task) {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            task.run();
            Logger.flush();
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static void expectNullPointer(Runnable task) {
        try {
            task.run();
        } catch (NullPointerException expected) {
            return;
        }
        throw new AssertionError("expected NullPointerException");
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for virtual thread", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
