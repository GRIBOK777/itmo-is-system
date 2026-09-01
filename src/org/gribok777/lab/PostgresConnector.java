package org.gribok777.lab;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class PostgresConnector implements AutoCloseable {
    private static final int CONNECTION_OK = 0;

    private volatile MemorySegment connection;

    public synchronized void connect(String url, String username, String password) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (connection != null) {
            throw new IllegalStateException("database is already connected");
        }

        MemorySegment opened = MemorySegment.NULL;
        try (Arena connectionArena = Arena.ofConfined()) {
            MemorySegment conninfo =
                    connectionArena.allocateFrom(
                            connectionInfo(url, username, password), StandardCharsets.UTF_8);
            opened = (MemorySegment) LibPQ.CONNECT.invokeExact(conninfo);
            if (opened.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("PQconnectdb returned null");
            }
            int status = (int) LibPQ.STATUS.invokeExact(opened);
            if (status != CONNECTION_OK) {
                throw new IllegalStateException(
                        "PostgreSQL connection failed: " + errorMessage(opened));
            }
            connection = opened;
            opened = MemorySegment.NULL;
        } catch (Throwable exception) {
            if (!opened.equals(MemorySegment.NULL)) {
                finishAfterFailure(opened, exception);
            }
            if (exception instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("PostgreSQL connection failed", exception);
        }
    }

    public synchronized void disconnect() {
        if (connection == null) {
            return;
        }
        try {
            LibPQ.FINISH.invokeExact(connection);
        } catch (Throwable exception) {
            throw new IllegalStateException("PostgreSQL disconnect failed", exception);
        } finally {
            connection = null;
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    public boolean isConnected() {
        return connection != null;
    }

    private static String errorMessage(MemorySegment connection) {
        try {
            MemorySegment message = (MemorySegment) LibPQ.ERROR_MESSAGE.invokeExact(connection);
            return message.equals(MemorySegment.NULL)
                    ? "unknown error"
                    : message.reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable _) {
            return "unknown error (could not read native error message)";
        }
    }

    private static void finishAfterFailure(MemorySegment connection, Throwable failure) {
        try {
            LibPQ.FINISH.invokeExact(connection);
        } catch (Throwable closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static String connectionInfo(String url, String username, String password) {
        var result = new StringBuilder(url.length() + username.length() + password.length() + 32);
        if (url.contains("=")) {
            result.append(url);
        } else {
            result.append("host=");
            appendQuoted(result, url);
        }
        result.append(" user=");
        appendQuoted(result, username);
        result.append(" password=");
        appendQuoted(result, password);
        return result.toString();
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

    private static final class LibPQ {
        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup SYMBOLS =
                SymbolLookup.libraryLookup(
                        System.getProperty("org.gribok777.lab.sql.libpq", "libpq.so.5"),
                        Arena.global());
        private static final MethodHandle CONNECT =
                downcall(
                        "PQconnectdb",
                        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        private static final MethodHandle STATUS =
                downcall(
                        "PQstatus",
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        private static final MethodHandle ERROR_MESSAGE =
                downcall(
                        "PQerrorMessage",
                        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        private static final MethodHandle FINISH =
                downcall("PQfinish", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
            MemorySegment symbol =
                    SYMBOLS.find(name)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "libpq symbol not found: " + name));
            return LINKER.downcallHandle(symbol, descriptor);
        }
    }
}
