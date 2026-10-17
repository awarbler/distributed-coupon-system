package coupons;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Two independent JVMs prove SAVE/exit/reopen works through the actual CLI entry point. */
class ReceiptRestartTest {
    @TempDir Path directory;

    private String run(Path data, String script, String name) throws Exception {
        Path input = directory.resolve(name + ".in"), output = directory.resolve(name + ".out");
        Files.writeString(input, script);
        String classes = Path.of(ReceiptEntryApp.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classes, "coupons.ReceiptEntryApp", data.toString())
                .redirectInput(input.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertTrue(finished, "CLI did not finish");
        String text = Files.readString(output);
        assertEquals(0, process.exitValue(), text);
        assertFalse(text.contains("Could not open receipt storage"), text);
        return text;
    }

    @Test void savedReceiptHistoryRetryAndFormattedTimeSurviveExitAndReopen() throws Exception {
        Path data = directory.resolve("receipts.bin");
        String entry = String.join("\n", "Restart-receipt", "25:99", "940 am", "10/4/26", "Walgreens", "aj", "", "Milk",
                "5.00", "1", "0", "0", "n", "n", "0", "0", "0", "1", "5.00", "n", "5.00",
                "n", "3.60", "6.00", "9.60", "43%", "247.75", "0.80", "10.00", "238.55", "n", "SAVE", "6") + "\n";
        String first = run(data, entry, "first");
        assertTrue(first.contains("Save result:"), first);
        assertTrue(first.contains("Time: 9:40 AM"), first);
        assertTrue(first.contains("Enter a valid time"), first);
        String second = run(data, "2\n#1\n4\n#1\n3\n4\n#1\n6\n", "second");
        assertTrue(second.contains("Loaded saved receipts: 1"), second);
        assertTrue(second.contains("Time: 9:40 AM"), second);
        assertTrue(second.contains("Original result:"), second);
        assertTrue(second.replaceAll(" +", " ").contains("Receipt Saving Value 43%"), second);
        assertTrue(second.replaceAll(" +", " ").contains("Receipt Opening Balance 247.75"), second);
        assertEquals(2, second.lines().filter(line -> line.equals("History entries: 1")).count(), second);
    }
}
