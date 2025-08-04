package ai.vespa.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import com.yahoo.io.IOUtils;

/**
 * @author arnej
 */
class CrossLinkerTest {

    static InputStream dummyContents() {
        byte[] contents = { 100, 117, 109, 109, 121, 10 };
        return new ByteArrayInputStream(contents);
    }
    static void mkFile(Path path) throws IOException {
        Files.copy(dummyContents(), path);
    }

    Path mkTemp(String prefix) throws IOException {
        Path p = Files.createTempDirectory(prefix);
        p.toFile().deleteOnExit();
        return p;
    }

    Path tmpOne;
    Path tmpTwo;
    CrossLinker linker;
    int mult = 1;

    @BeforeEach
    void setup() throws IOException {
        this.tmpOne = mkTemp("one");
        this.tmpTwo = mkTemp("two");
        this.linker = new CrossLinker();
        this.mult = 1;
    }

    @AfterEach
    void teardown() throws IOException {
        rm_r(tmpOne);
        rm_r(tmpTwo);
    }

    void rm_r(Path p) {
        IOUtils.recursiveDeleteDir(p.toFile());
    }

    String name(int idx) { return String.valueOf((idx + 1) * mult); }

    void fill(Path p, int depth, int num)
        throws IOException
    {
        Files.createDirectories(p);
        if (depth > 0) {
            for (int i = 0; i < num; i++) {
                Path subDir = p.resolve(name(i));
                fill(subDir, depth - 1, num);
            }
        }
        for (int i = 0; i < num; i++) {
            Path subFile = p.resolve(name(num + i));
            mkFile(subFile);
        }
    }

    void checkEqTrees() throws IOException {
        System.err.println("==== Compare ====");
        String[] cmd = { "diff", "-ru", tmpOne.toString(), tmpTwo.toString() };
        try {
            var process = Runtime.getRuntime().exec(cmd);
            try (var err = process.getInputStream()) {
                assertEquals(0, process.waitFor());
                int b;
                while ((b = err.read()) != -1) {
                    System.err.print((char) b);
                }
            }
        } catch (InterruptedException e) {
            assertEquals(null, e);
        }
        System.err.println("==== Compared ok ====");
    }

    @Test
    void replicates_one_way() throws IOException {
        System.err.println(">>> replicates_one_way >>>");
        fill(tmpOne, 3, 2);
        rm_r(tmpTwo);
        linker.crossLink(tmpOne.toString(), tmpTwo.toString());
        checkEqTrees();
        linker.dumpAndResetStats();
        System.err.println("<<< replicates_one_way <<<");
    }

    @Test
    void replicates_both_ways() throws IOException {
        System.err.println(">>> replicates_both_ways >>>");
        fill(tmpOne, 2, 3);
        this.mult = 17;
        fill(tmpTwo, 2, 3);
        linker.crossLink(tmpOne.toString(), tmpTwo.toString());
        checkEqTrees();
        linker.dumpAndResetStats();
        System.err.println("<<< replicates_both_ways <<<");
    }

    @Test
    void can_skip_replicates() throws IOException {
        System.err.println(">>> can_skip_replicates >>>");
        fill(tmpOne, 2, 4);
        fill(tmpTwo, 2, 4);
        linker.crossLink(tmpOne.toString(), tmpTwo.toString());
        checkEqTrees();
        linker.dumpAndResetStats();
        System.err.println("<<<  can_skip_replicates <<<");
    }

    @Test
    void skips_clashing() throws IOException {
        System.err.println(">>> skips_clashing >>>");
        fill(tmpOne, 2, 4);
        fill(tmpTwo, 3, 3);
        linker.crossLink(tmpOne.toString(), tmpTwo.toString());
        linker.dumpAndResetStats();
        System.err.println("<<< skips_clashing <<<");
    }

    /* insert empty USB stick for this test:
    @Test
    void can_copy() throws IOException {
        System.err.println(">>> can_copy >>>");
        fill(tmpOne, 2, 3);
        this.mult = 17;
        tmpTwo = Files.createTempDirectory(Path.of("/Volumes/NO NAME"), "two");
        fill(tmpTwo, 2, 3);
        linker = new CrossLinker(true);
        linker.crossLink(tmpOne, tmpTwo);
        checkEqTrees();
        linker.dumpAndResetStats();
        System.err.println("<<< can_copy <<<");
    }
    */
}
