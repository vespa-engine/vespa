package ai.vespa.schemals.common;

import java.io.File;
import java.io.FileReader;
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
import java.util.Optional;

import com.yahoo.io.IOUtils;

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

    public static String readFromURI(String fileURI) throws IOException {
        File file = new File(URI.create(fileURI));
        return IOUtils.readAll(new FileReader(file));
    }

    public static List<String> findSchemaFiles(String workspaceFolderUri, PrintStream logger) {
        return walkFileTree(Paths.get(URI.create(workspaceFolderUri)),  "glob:**/*.sd", logger);
    }

    public static List<String> findRankProfileFiles(String workspaceFolderUri, PrintStream logger) {
        // glob at least one dir deep
        return walkFileTree(Paths.get(URI.create(workspaceFolderUri)),  "glob:**/*/*.profile", logger);
    }

    public static String firstPathComponentAfterPrefix(String pathURIStr, String prefixURIStr) {
        URI pathURI = URI.create(pathURIStr);
        URI prefixURI = URI.create(prefixURIStr);
        URI relativeURI = prefixURI.relativize(pathURI);

        if (relativeURI.isAbsolute()) {
            return null;
        }

        String relativePath = relativeURI.getPath();

        if (relativePath == null) return null;

        // TODO: is this an issue on Windows?
        String[] components = relativePath.split("/");

        if (components.length == 0) return null;

        return components[0];
    }

    public static Optional<URI> findSchemaDirectory(URI initialURI) {
        Path path = Paths.get(initialURI);
        while (path != null && path.getFileName() != null) {
            if (path.getFileName().toString().equals("schemas")) {
                break;
            }
            path = path.getParent();
        }

        if (path == null || path.getFileName() == null) {
            return Optional.empty();
        }
        return Optional.of(path.toUri());
    }

    private static List<String> walkFileTree(Path rootDir, String pathMatcherStr, PrintStream logger) {
        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(pathMatcherStr);

        // TODO: Exclude known heavy directories like .git
        List<String> filePaths = new ArrayList<>();
        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
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
