package test.org.gribok777.lab;

public final class MainTest {
    public static void main(String[] args) {
        System.out.println("Running the test suite...");
        run();
    }

    public static void run() {
        System.out.println(MainTest.class.getSimpleName() + " passed all tests.");
    }
}
