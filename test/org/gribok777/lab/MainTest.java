package org.gribok777.lab;

public final class MainTest {
    public static void main(String[] args) {
        System.out.println("Running the test suite...");
        run();
    }

    public static void run() {
        org.gribok777.lab.LoggerTest.run();
        System.out.println("[OK] " + MainTest.class.getSimpleName() + " passed all tests.");
    }
}
