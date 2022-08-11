package ai.vespa.validation;

import java.nio.file.Path;

/**
 * Path validations
 *
 * @author mortent
 */
public class PathValidator {

    /**
     * Validate that file is a child of basedir
     * @param root Root directory to use for validation
     * @param path Path to validate
     * @throws IllegalArgumentException if path is not a child of root
     */
    public static void validateChildOf(Path root, Path path) {
        if (!path.normalize().startsWith(root)) {
            throw new IllegalArgumentException("Invalid path %s".formatted(path));
        }
    }

    /**
     * Resolves a path under a root path
     * @param root root poth
     * @param path child to resolve
     * @return The resolved path
     * @throws IllegalArgumentException If the provided child path does not resolve as child of root
     */
    public static Path resolveChildOf(Path root, String path) {
        Path resolved = root.resolve(path);
        validateChildOf(root, resolved);
        return resolved;
    }
}
