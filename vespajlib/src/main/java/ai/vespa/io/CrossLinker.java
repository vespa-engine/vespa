package ai.vespa.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * Utility to sync two directory trees with each other
 *
 * @author arnej
 */
public class CrossLinker {

    private final boolean verbose;

    public CrossLinker() { this(false); }
    public CrossLinker(boolean verbose) { this.verbose = verbose; }

    public void crossLink(String srcDir, String dstDir) {
        try {
            Path src = Path.of(srcDir);
            Path dst = Path.of(dstDir);
            crossLink(src, dst);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Set<Path> entries(Path dir) throws IOException {
        var result = new TreeSet<Path>();
        for (Path p : Files.newDirectoryStream(dir)) {
            result.add(p.getFileName());
        }
        return result;
    }

    void maybeSync(Path src, Path dst) throws IOException {
        if (Files.isDirectory(src)) {
            if (Files.isDirectory(dst)) {
                crossLink(src, dst);
            } else {
                System.err.println("cannot sync directory " + src + " <-> non-directory " + dst);
            }
        } else if (Files.isDirectory(dst)) {
            System.err.println("cannot sync non-directory " + src + " <-> directory " + dst);
        }
        // else: assume already OK
    }

    void linkOrCopy(Path src, Path dst) throws IOException {
        try {
            Path ok = Files.createLink(dst, src);
            if (verbose) System.out.println("link " + src + " -> " + ok);
            return;
        } catch (Exception e) {
            // should maybe log error first time
        }
        Files.copy(src, dst);
        if (verbose) System.out.println("copy " + src + " -> " + dst);
    }

    void crossLink(Path src, Path dst) throws IOException {
        Path a = Files.createDirectories(src);
        Path b = Files.createDirectories(dst);
        Set<Path> aSet = entries(a);
        Set<Path> bSet = entries(b);
        for (Path entry : aSet) {
            Path aSub = a.resolve(entry);
            Path bSub = b.resolve(entry);
            if (bSet.contains(entry)) {
                maybeSync(aSub, bSub);
            } else if (Files.isDirectory(aSub)) {
                crossLink(aSub, bSub);
            } else {
                linkOrCopy(aSub, bSub);
            }
        }
        for (Path entry : bSet) {
            if (aSet.contains(entry)) continue;
            Path aSub = a.resolve(entry);
            Path bSub = b.resolve(entry);
            if (Files.isDirectory(bSub)) {
                // keep "a" as source
                crossLink(aSub, bSub);
            } else {
                linkOrCopy(bSub, aSub);
            }
        }
    }

}
