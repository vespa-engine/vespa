package ai.vespa.schemals.common;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static String fileNameFromPath(String path) {
        int splitPos = path.lastIndexOf('/');
        return path.substring(splitPos + 1);
    }

    public static String schemaNameFromPath(String path) {
        String fileName = fileNameFromPath(path);

        int splitPos = fileName.lastIndexOf('.');

        if (splitPos == -1) return "";

        String res = fileName.substring(0, splitPos);
        if (res == null) return "";
        return res;
    }

    public static List<String> findSchemaFiles(String workspaceFolderUri, PrintStream logger) {
        String glob = "glob:**/*.sd";
        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(glob);

        // TODO: Exclude known heavy directories like .git
        List<String> filePaths = new ArrayList<>();
        try {
            Files.walkFileTree(Paths.get(URI.create(workspaceFolderUri)), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    if (pathMatcher.matches(path)) {
                        filePaths.add(path.toUri().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch(IOException ex) {
            logger.println("IOException caught when walking file tree: " + ex.getMessage());
        }

        return filePaths;
    }
}
