package ai.vespa.schemals.common;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import java.util.Set;
import java.util.stream.Collectors;

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

    public static List<String> findSchemaFiles(String workspaceFolderUri, ClientLogger logger) {
        return walkFileTree(Paths.get(URI.create(workspaceFolderUri)),  "glob:**/*.sd", logger);
    }

    public static List<String> findRankProfileFiles(String workspaceFolderUri, ClientLogger logger) {
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

    // https://stackoverflow.com/questions/1976007/what-characters-are-forbidden-in-windows-and-linux-directory-names
    private final static Set<Character> DISALLOWED_CHARS = Set.of('/', '<', '>', ':', '"', '\\', '|', '?', '*');
    public static String sanitizeFileName(String fileName) {
        return fileName.chars()
                       .filter(c -> !DISALLOWED_CHARS.contains((char) c))
                       .mapToObj(c -> "" + (char) c)
                       .collect(Collectors.joining());
    }

    /* 
     * Decode URL in a kind way
     */
    public static String decodeURL(String URL) {
        try {
            return URLDecoder.decode(URL, StandardCharsets.UTF_8.name());
        } catch(Exception e) {
            return URL;
        }
    }

    /**
     * Searches among the parents for a directory named "schemas"
     */
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

    private static List<String> walkFileTree(Path rootDir, String pathMatcherStr, ClientLogger logger) {
        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(pathMatcherStr);

        // TODO: Exclude known heavy directories like .git
        List<String> filePaths = new ArrayList<>();
        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    if (pathMatcher.matches(path)) {
                        filePaths.add(decodeURL(path.toUri().toString()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch(IOException ex) {
            logger.error("IOException caught when walking file tree: " + ex.getMessage());
        }

        return filePaths;
    }
}
