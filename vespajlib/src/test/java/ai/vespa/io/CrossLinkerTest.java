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
        Path curr = Path.of(System.getProperty("user.dir"));
        Path p = Files.createTempDirectory(curr, prefix);
        p.toFile().deleteOnExit();
        return p;
    }

    Path tmpOne;
    Path tmpTwo;
    CrossLinker linker;
    int mult = 1;


    @BeforeEach
    void setup() throws IOException {
        this.tmpOne = mkTemp("one").getFileName();
        this.tmpTwo = mkTemp("two").getFileName();
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
        fill(tmpOne, 3, 2);
        rm_r(tmpTwo);
        linker.crossLink(tmpOne, tmpTwo);
        checkEqTrees();
    }

    @Test
    void replicates_both_ways() throws IOException {
        fill(tmpOne, 2, 3);
        this.mult = 17;
        fill(tmpTwo, 2, 3);
        linker.crossLink(tmpOne, tmpTwo);
        checkEqTrees();
    }

    @Test
    void skips_clashing() throws IOException {
        fill(tmpOne, 2, 4);
        fill(tmpTwo, 3, 3);
        linker.crossLink(tmpOne, tmpTwo);
        checkEqTrees();
    }
}
