package ai.vespa.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
    int numDirsSynced = 0;
    int numFilesLinked = 0;
    int numFilesCopied = 0;
    int numNonRegularFiles = 0;
    int numConflicts = 0;
    int numAlreadyPresent = 0;
    int numCopyFailures = 0;

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
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                result.add(p.getFileName());
            }
        }
        return result;
    }

    void maybeSync(Path src, Path dst) throws IOException {
        if (Files.isDirectory(src)) {
            if (Files.isDirectory(dst)) {
                crossLink(src, dst);
            } else {
                System.err.println("cannot sync directory " + src + " <-> non-directory " + dst);
                ++numConflicts;
            }
        } else if (Files.isDirectory(dst)) {
            System.err.println("cannot sync non-directory " + src + " <-> directory " + dst);
            ++numConflicts;
        }
        // else: assume already OK
        ++numAlreadyPresent;
    }

    void linkOrCopy(Path src, Path dst) {
        if (! Files.isRegularFile(src, LinkOption.NOFOLLOW_LINKS)) {
            ++numNonRegularFiles;
            return;
        }
        try {
            Path ok = Files.createLink(dst, src);
            ++numFilesLinked;
            if (verbose) System.out.println("link " + src + " -> " + ok);
            return;
        } catch (IOException e) {
            // should maybe log error first time
        }
        try {
            Path tmp = Files.createTempFile(dst.getParent(), "tmp-", ".tmp");
            Files.delete(tmp);
            Files.copy(src, tmp);
            Path ok = Files.move(tmp, dst);
            ++numFilesCopied;
            if (verbose) System.out.println("copy " + src + " -> " + dst);
        } catch (IOException e) {
            System.err.println("[IGNORED " + e + "] Could not copy " + src + " -> " + dst);
            ++numCopyFailures;
        }
    }

    void crossLink(Path src, Path dst) throws IOException {
        ++numDirsSynced;
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

    void dumpAndResetStats() {
        System.err.println("Synced " + numDirsSynced + " directories:");
        System.err.println("  -  files linked: " + numFilesLinked);
        System.err.println("  -  files copied: " + numFilesCopied);
        System.err.println("  -  non-regular files skipped: " + numNonRegularFiles);
        System.err.println("  -  dir/file conflicts skipped: " + numConflicts);
        System.err.println("  -  files already present skipped: " + numAlreadyPresent);
        System.err.println("  -  failures to link or copy: " + numCopyFailures);
        numDirsSynced = 0;
        numFilesLinked = 0;
        numFilesCopied = 0;
        numNonRegularFiles = 0;
        numConflicts = 0;
        numAlreadyPresent = 0;
        numCopyFailures = 0;
    }

}
