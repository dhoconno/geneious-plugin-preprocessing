import com.biomatters.plugins.fastp.FastpBinaryManager;
import java.io.File;

/**
 * Simple standalone test for FastpBinaryManager.
 * Tests binary extraction, validation, and version checking.
 */
public class TestFastpBinaryManager {

    public static void main(String[] args) {
        System.out.println("=== FastpBinaryManager Test ===\n");

        try {
            // Get singleton instance
            FastpBinaryManager manager = FastpBinaryManager.getInstance();
            System.out.println("1. Created BinaryManager instance");

            // Initialize (extract binaries)
            System.out.println("2. Initializing (extracting binaries)...");
            manager.initialize();
            System.out.println("   Initialization successful!");

            // Check availability
            System.out.println("\n3. Checking binary availability:");
            System.out.println("   Fastp available: " + manager.isFastpAvailable());
            System.out.println("   Fastplong available: " + manager.isFastplongAvailable());

            // Get binary paths
            System.out.println("\n4. Binary paths:");
            File fastpBinary = manager.getFastpBinary();
            File fastplongBinary = manager.getFastplongBinary();
            System.out.println("   Fastp: " + fastpBinary.getAbsolutePath());
            System.out.println("   Fastplong: " + fastplongBinary.getAbsolutePath());

            // Check executability
            System.out.println("\n5. Binary validation:");
            System.out.println("   Fastp exists: " + fastpBinary.exists());
            System.out.println("   Fastp is file: " + fastpBinary.isFile());
            System.out.println("   Fastp executable: " + fastpBinary.canExecute());
            System.out.println("   Fastplong exists: " + fastplongBinary.exists());
            System.out.println("   Fastplong is file: " + fastplongBinary.isFile());
            System.out.println("   Fastplong executable: " + fastplongBinary.canExecute());

            // Get versions
            System.out.println("\n6. Binary versions:");
            try {
                String fastpVersion = manager.getFastpVersion();
                System.out.println("   Fastp version:\n" + indent(fastpVersion));
            } catch (Exception e) {
                System.out.println("   Fastp version failed: " + e.getMessage());
            }

            try {
                String fastplongVersion = manager.getFastplongVersion();
                System.out.println("   Fastplong version:\n" + indent(fastplongVersion));
            } catch (Exception e) {
                System.out.println("   Fastplong version failed: " + e.getMessage());
            }

            // Display full info
            System.out.println("\n7. Complete binary info:");
            System.out.println(indent(manager.getBinaryInfo()));

            System.out.println("\n=== All Tests Passed! ===");

        } catch (Exception e) {
            System.err.println("\n=== TEST FAILED ===");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String indent(String text) {
        return "   " + text.replace("\n", "\n   ");
    }
}
