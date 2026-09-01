package org.gribok777.lab.test;

public final class MainTest {
    public static void main(String[] args) {
        System.out.println("Running the test suite...");
        run();
    }

    public static void run() {
        LoggerTest.run();
        System.out.println("[OK] " + MainTest.class.getSimpleName() + " passed all tests.");
    }
}
