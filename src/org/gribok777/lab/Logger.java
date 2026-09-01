package org.gribok777.lab;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public final class Logger {
    private static final ScopedValue<LogLevel> LOG_LEVEL = ScopedValue.newInstance();
    private static final LogLevel DEFAULT_LEVEL = LogLevel.INFO;

    private static final int QUEUE_CAPACITY = 1024;
    private static final BlockingQueue<LogEvent> FREE_EVENTS =
            new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private static final BlockingQueue<LogEvent> READY_EVENTS =
            new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private static final StringBuilder LINE_BUFFER = new StringBuilder(128);
    private static final byte[] OUTPUT_BUFFER = new byte[8192];

    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long SECONDS_PER_HOUR = 3_600L;
    private static final long SECONDS_PER_MINUTE = 60L;

    static {
        for (int index = 0; index < QUEUE_CAPACITY; index++) {
            FREE_EVENTS.add(new LogEvent());
        }
        Thread.ofPlatform().daemon().name("basic-logger").start(Logger::writeLogs);
    }

    private static void log(LogLevel messageLevel, String message) {
        LogLevel currentLimit = LOG_LEVEL.orElse(DEFAULT_LEVEL);

        if (messageLevel.priority >= currentLimit.priority) {
            LogEvent event = FREE_EVENTS.poll();
            if (event != null) {
                event.level = messageLevel;
                event.message = message;
                event.timestamp = System.currentTimeMillis();
                event.virtual = Thread.currentThread().isVirtual();
                event.output = System.out;
                if (!READY_EVENTS.offer(event)) {
                    event.clear();
                    FREE_EVENTS.offer(event);
                }
            }
        }
    }

    public static void flush() {
        CountDownLatch completed = new CountDownLatch(1);
        try {
            LogEvent event = FREE_EVENTS.take();
            event.completed = completed;
            event.output = System.out;
            READY_EVENTS.put(event);
            completed.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while flushing logger", exception);
        }
    }

    private static void writeLogs() {
        try {
            while (true) {
                LogEvent event = READY_EVENTS.take();
                try {
                    if (event.completed != null) {
                        try {
                            event.output.flush();
                        } catch (IOException _) {
                            // Logging must not terminate application threads when output fails.
                        }
                        event.completed.countDown();
                    } else {
                        write(event);
                    }
                } catch (RuntimeException _) {
                    if (event.completed != null) {
                        event.completed.countDown();
                    }
                } finally {
                    event.clear();
                    FREE_EVENTS.put(event);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void write(LogEvent event) {
        LINE_BUFFER.setLength(0);
        long seconds = Math.floorMod(event.timestamp, MILLIS_PER_DAY) / MILLIS_PER_SECOND;
        LINE_BUFFER.append('[').append(event.level.formattedName).append("] [");
        appendTwoDigits(LINE_BUFFER, seconds / SECONDS_PER_HOUR);
        LINE_BUFFER.append(':');
        appendTwoDigits(LINE_BUFFER, (seconds / SECONDS_PER_MINUTE) % SECONDS_PER_MINUTE);
        LINE_BUFFER.append(':');
        appendTwoDigits(LINE_BUFFER, seconds % SECONDS_PER_MINUTE);
        LINE_BUFFER
                .append("] ")
                .append(event.virtual ? "(v)" : "(p)")
                .append(' ')
                .append(event.message)
                .append('\n');
        try {
            writeUtf8(event.output, LINE_BUFFER);
            if (READY_EVENTS.isEmpty()) {
                event.output.flush();
            }
        } catch (IOException _) {
            // Logging must not terminate application threads when output fails.
        }
    }

    private static void writeUtf8(OutputStream output, CharSequence text) throws IOException {
        int buffered = 0;
        for (int index = 0; index < text.length(); index++) {
            int codePoint = text.charAt(index);
            if (Character.isHighSurrogate((char) codePoint)
                    && index + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(index + 1))) {
                codePoint = Character.toCodePoint((char) codePoint, text.charAt(++index));
            } else if (Character.isSurrogate((char) codePoint)) {
                codePoint = 0xfffd;
            }
            switch (codePoint) {
                case int value when value <= 0x7f -> {
                    if (buffered == OUTPUT_BUFFER.length) {
                        output.write(OUTPUT_BUFFER);
                        buffered = 0;
                    }
                    OUTPUT_BUFFER[buffered++] = (byte) value;
                }
                case int value when value <= 0x7ff -> {
                    buffered = appendByte(output, buffered, (byte) (0xc0 | (value >> 6)));
                    buffered = appendByte(output, buffered, (byte) (0x80 | (value & 0x3f)));
                }
                case int value when value <= 0xffff -> {
                    buffered = appendByte(output, buffered, (byte) (0xe0 | (value >> 12)));
                    buffered = appendByte(output, buffered, (byte) (0x80 | ((value >> 6) & 0x3f)));
                    buffered = appendByte(output, buffered, (byte) (0x80 | (value & 0x3f)));
                }
                case int value -> {
                    buffered = appendByte(output, buffered, (byte) (0xf0 | (value >> 18)));
                    buffered = appendByte(output, buffered, (byte) (0x80 | ((value >> 12) & 0x3f)));
                    buffered = appendByte(output, buffered, (byte) (0x80 | ((value >> 6) & 0x3f)));
                    buffered = appendByte(output, buffered, (byte) (0x80 | (value & 0x3f)));
                }
            }
        }
        if (buffered > 0) {
            output.write(OUTPUT_BUFFER, 0, buffered);
        }
    }

    private static int appendByte(OutputStream output, int buffered, byte value)
            throws IOException {
        if (buffered == OUTPUT_BUFFER.length) {
            output.write(OUTPUT_BUFFER);
            buffered = 0;
        }
        OUTPUT_BUFFER[buffered++] = value;
        return buffered;
    }

    private static void appendTwoDigits(StringBuilder output, long value) {
        output.append((char) ('0' + value / 10)).append((char) ('0' + value % 10));
    }

    private static final class LogEvent {
        private LogLevel level;
        private String message;
        private long timestamp;
        private boolean virtual;
        private OutputStream output;
        private CountDownLatch completed;

        private void clear() {
            level = null;
            message = null;
            output = null;
            completed = null;
        }
    }

    public static void withLevel(LogLevel level, Runnable task) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(task, "task");
        ScopedValue.where(LOG_LEVEL, level).run(task);
    }

    public static <T> T withLevel(LogLevel level, Supplier<T> task) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(task, "task");
        try {
            return ScopedValue.where(LOG_LEVEL, level).call(task::get);
        } catch (Exception exception) {
            throw (exception instanceof RuntimeException re) ? re : new RuntimeException(exception);
        }
    }

    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public enum LogLevel {
        DEBUG(0, "DEBUG"),
        INFO(1, "INFO "),
        ERROR(2, "ERROR");

        final int priority;
        final String formattedName;

        LogLevel(int priority, String formattedName) {
            this.priority = priority;
            this.formattedName = formattedName;
        }
    }
}
