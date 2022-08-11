package ai.vespa.validation;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PathValidatorTest {

    @Test
    public void testPathValidation() {
        Path root = Path.of("/foo/");

        assertOk(Path.of("/foo/bar"), root);
        assertOk(Path.of("/foo/foo2/bar"), root);
        assertOk(Path.of("/foo/foo2/../bar"), root);
        assertOk(Path.of("/foo/../foo/bar"), root);
        assertOk(Path.of("/bar/../foo/../foo/bar"), root);

        assertInvalid(Path.of("/foo/../bar"), root);
        assertInvalid(Path.of("/foo/bar/../../bar"), root);
    }

    private void assertOk(Path path, Path root) {
        PathValidator.validateChildOf(root, path);
    }

    private void assertInvalid(Path path, Path root) {
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                                                                         () -> PathValidator.validateChildOf(root, path));
        assertEquals("Invalid path %s".formatted(path), illegalArgumentException.getMessage());
    }
}
