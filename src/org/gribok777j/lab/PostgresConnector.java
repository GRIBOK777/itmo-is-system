package org.gribok777j.lab;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class PostgresConnector implements AutoCloseable {
    private static final int CONNECTION_OK = 0;
    private static final Linker LINKER = Linker.nativeLinker();
    private static final FunctionDescriptor POINTER_TO_POINTER =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    private static final FunctionDescriptor STATUS_DESCRIPTOR =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
    private static final FunctionDescriptor FINISH_DESCRIPTOR =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

    private volatile MemorySegment connection;

    public synchronized void connect(String url, String username, String password) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (connection != null) {
            throw new IllegalStateException("database is already connected");
        }

        try (Arena connectionArena = Arena.ofConfined()) {
            LibPQ libpq = LibPQ.instance();
            MemorySegment conninfo = connectionArena.allocateFrom(
                connectionInfo(url, username, password), StandardCharsets.UTF_8);
            MemorySegment newConnection = (MemorySegment) libpq.connect.invokeExact(conninfo);
            if (newConnection.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("PQconnectdb returned null");
            }
            int status = (int) libpq.status.invokeExact(newConnection);
            if (status != CONNECTION_OK) {
                String error = errorMessage(libpq, newConnection);
                libpq.finish.invokeExact(newConnection);
                throw new IllegalStateException("PostgreSQL connection failed: " + error);
            }
            connection = newConnection;
        } catch (Throwable exception) {
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
            LibPQ.instance().finish.invokeExact(connection);
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

    private static String errorMessage(LibPQ libpq, MemorySegment connection) {
        try {
            MemorySegment message = (MemorySegment) libpq.errorMessage.invokeExact(connection);
            return message.equals(MemorySegment.NULL)
                ? "unknown error"
                : message.reinterpret(4_096).getString(0);
        } catch (Throwable _) {
            return "unknown error (could not read native error message)";
        }
    }

    private static String connectionInfo(String url, String username, String password) {
        String host = url.contains("=") ? url : "host=" + quote(url);
        return host + " user=" + quote(username) + " password=" + quote(password);
    }

    private static String quote(String value) {
        var quoted = new StringBuilder(value.length() + 2).append('\'');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '\'') {
                quoted.append('\\');
            }
            quoted.append(character);
        }
        return quoted.append('\'').toString();
    }

    private static final class LibPQ {
        private static final LibPQ INSTANCE = load();

        private final java.lang.invoke.MethodHandle connect;
        private final java.lang.invoke.MethodHandle status;
        private final java.lang.invoke.MethodHandle errorMessage;
        private final java.lang.invoke.MethodHandle finish;

        private LibPQ(SymbolLookup symbols) {
            connect = downcall(symbols, "PQconnectdb", POINTER_TO_POINTER);
            status = downcall(symbols, "PQstatus", STATUS_DESCRIPTOR);
            errorMessage = downcall(symbols, "PQerrorMessage", POINTER_TO_POINTER);
            finish = downcall(symbols, "PQfinish", FINISH_DESCRIPTOR);
        }

        private static LibPQ instance() {
            return INSTANCE;
        }

        private static LibPQ load() {
            String library = System.getProperty("org.gribok777j.lab.sql.libpq", "libpq.so.5");
            return new LibPQ(SymbolLookup.libraryLookup(library, Arena.global()));
        }

        private static java.lang.invoke.MethodHandle downcall(
            SymbolLookup symbols, String name, FunctionDescriptor descriptor) {
            MemorySegment symbol = symbols.find(name)
                .orElseThrow(() -> new IllegalStateException("libpq symbol not found: " + name));
            return LINKER.downcallHandle(symbol, descriptor);
        }
    }
}
