package org.gribok777j.lab;


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class LoggerTest {
    private LoggerTest() {
    }

    static void run() {
        defaultLevelIsInfo();
        levelsFilterMessages();
        nestedLevelsRestorePreviousContext();
        supplierAndRunnableScopesWork();
        threadKindIsLogged();
        utf8AndMessageOrderArePreserved();
        nullArgumentsAreRejected();
        System.out.println("[OK] LoggerTest passed all tests.");
    }

    private static void defaultLevelIsInfo() {
        String output = capture(() -> {
            Logger.debug("debug-hidden");
            Logger.info("info-visible");
            Logger.error("error-visible");
        });

        check(!output.contains("debug-hidden"), "default level must hide DEBUG");
        check(output.contains("[INFO]") && output.contains("info-visible"),
            "default level must write INFO");
        check(output.contains("[ERROR]") && output.contains("error-visible"),
            "default level must write ERROR");
    }

    private static void levelsFilterMessages() {
        String output = capture(() -> Logger.withLevel(Logger.LogLevel.ERROR, () -> {
            Logger.debug("debug-hidden");
            Logger.info("info-hidden");
            Logger.error("error-visible");
        }));

        check(!output.contains("debug-hidden") && !output.contains("info-hidden"),
            "ERROR level must hide lower-priority messages");
        check(output.contains("error-visible"), "ERROR level must write ERROR");

        output = capture(() -> Logger.withLevel(Logger.LogLevel.DEBUG,
            () -> Logger.debug("debug-visible")));
        check(output.contains("[DEBUG]") && output.contains("debug-visible"),
            "DEBUG level must write DEBUG");
    }

    private static void nestedLevelsRestorePreviousContext() {
        String output = capture(() -> Logger.withLevel(Logger.LogLevel.ERROR, () -> {
            Logger.info("outer-hidden");
            Logger.withLevel(Logger.LogLevel.DEBUG, () -> Logger.info("inner-visible"));
            Logger.info("outer-hidden-again");
        }));

        check(!output.contains("outer-hidden"), "outer level must be restored");
        check(output.contains("inner-visible"), "inner level must override outer level");
    }

    private static void supplierAndRunnableScopesWork() {
        String value = Logger.withLevel(Logger.LogLevel.DEBUG, () -> "returned");
        check(Objects.equals("returned", value), "supplier result must be returned");

        String output = capture(() -> Logger.withLevel(Logger.LogLevel.DEBUG,
            () -> Logger.debug("runnable-visible")));
        check(output.contains("runnable-visible"), "runnable scope must execute task");
    }

    private static void threadKindIsLogged() {
        String platformOutput = capture(() -> Logger.info("platform-message"));
        check(platformOutput.contains("(p) platform-message"),
            "platform thread must be marked with (p)");

        String virtualOutput = capture(() -> {
            Thread virtual = Thread.ofVirtual().start(() -> Logger.info("virtual-message"));
            join(virtual);
        });
        check(virtualOutput.contains("(v) virtual-message"),
            "virtual thread must be marked with (v)");
    }

    private static void utf8AndMessageOrderArePreserved() {
        String first = "Привет, мир! 你好 🌍";
        String second = "after";
        String output = capture(() -> {
            Logger.info(first);
            Logger.info(second);
        });

        check(output.contains(first), "UTF-8 message must be preserved");
        check(output.indexOf(first) < output.indexOf(second), "message order must be preserved");
    }

    private static void nullArgumentsAreRejected() {
        expectNullPointer(() -> Logger.withLevel(null, () -> { }));
        expectNullPointer(() -> Logger.withLevel(Logger.LogLevel.INFO, (Runnable) null));
        expectNullPointer(() -> Logger.withLevel(null, () -> "value"));
        expectNullPointer(() -> Logger.withLevel(Logger.LogLevel.INFO,
            (java.util.function.Supplier<String>) null));
    }

    private static String capture(Runnable task) {
        PrintStream original = System.out;
        var bytes = new ByteArrayOutputStream();
        try (var capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            task.run();
            Logger.flush();
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while joining virtual thread", exception);
        }
    }

    private static void expectNullPointer(Runnable task) {
        try {
            task.run();
        } catch (NullPointerException expected) {
            return;
        }
        throw new AssertionError("expected NullPointerException");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
