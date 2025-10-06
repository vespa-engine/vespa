package ai.vespa.vespasignificance.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VespaIndexInspectClientTest {

    static class FakeProcess extends Process {
        private final InputStream out;
        private final int exitCode;
        private boolean destroyed;

        FakeProcess(String stdout, int exitCode) {
            this.out = new ByteArrayInputStream(stdout.getBytes());
            this.exitCode = exitCode;
        }

        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return out; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return exitCode; }
        @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) { return true; }
        @Override public int exitValue() { return exitCode; }
        @Override public void destroy() { destroyed = true; }
        @Override public Process destroyForcibly() { destroyed = true; return this; }
        @Override public boolean isAlive() { return false; }
    }

    @Test
    void streamsHappyPathAndChecksExit0OnClose() throws Exception {
        String stdout = "t1\t1\nt2\t2\nignored\tbad\n";
        VespaIndexInspectClient.ProcessStarter pf = cmd -> new FakeProcess(stdout, 0);
        var client = new VespaIndexInspectClient("vespa-index-inspect", pf);

        try (var stream = client.streamDumpWords(Path.of("/tmp"), "myfield")) {
            List<VespaIndexInspectClient.TermDocumentFrequency> list = stream.toList();
            assertEquals(List.of(new VespaIndexInspectClient.TermDocumentFrequency("t1",1L), new VespaIndexInspectClient.TermDocumentFrequency("t2",2L)), list);
        }
    }

    @Test
    void nonZeroExitSurfacesOnClose() {
        String stdout = "t1\t1\n";
        VespaIndexInspectClient.ProcessStarter pf = cmd -> new FakeProcess(stdout, 7);
        var client = new VespaIndexInspectClient("vespa-index-inspect", pf);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            var s = client.streamDumpWords(Path.of("/tmp"), "f");
            s.close();
        });
        assertInstanceOf(IOException.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("exited with code 7"));
    }

    @Test
    void inputValidation() {
        var client = new VespaIndexInspectClient();
        assertThrows(NullPointerException.class, () -> client.streamDumpWords(null, "f"));
        assertThrows(IllegalArgumentException.class, () -> client.streamDumpWords(Path.of("."), " "));
    }
}
