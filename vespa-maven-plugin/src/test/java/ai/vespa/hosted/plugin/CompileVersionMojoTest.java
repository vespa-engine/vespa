package ai.vespa.hosted.plugin;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
class CompileVersionMojoTest {

    @Test
    public void allow_major() {
        assertMajorVersion(OptionalInt.empty(), Paths.get("non-existent-deployment.xml"));
        assertMajorVersion(OptionalInt.empty(), Paths.get("src/test/resources/deployment.xml"));
        assertMajorVersion(OptionalInt.of(8), Paths.get("src/test/resources/deployment-with-major.xml"));
    }

    private void assertMajorVersion(OptionalInt expected, Path deploymentXml) {
        OptionalInt allowMajor = CompileVersionMojo.majorVersion(deploymentXml);
        assertEquals(expected, allowMajor);
    }

}
