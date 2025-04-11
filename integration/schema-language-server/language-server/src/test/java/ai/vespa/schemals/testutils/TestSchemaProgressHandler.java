package ai.vespa.schemals.testutils;

import java.util.UUID;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import ai.vespa.schemals.SchemaProgressHandler;

/**
 * TestSchemaPogressHandler
 */
public class TestSchemaProgressHandler extends SchemaProgressHandler {

    public class TestProgress extends Progress {

        TestProgress() {
            super(null, Either.forLeft(UUID.randomUUID().toString()));
        }

        @Override
        public void partialResult(String message, Integer percentage) {
        }

        @Override
        public void partialResult(String message) {
        }

        @Override
        public void end(String message) {
        }

        @Override
        public void end() {
        }
    }

    public TestSchemaProgressHandler() {
        super();
    }

    @Override
    public boolean connected() { return true; }

    @Override
    public Progress newWorkDoneProgress(String title) {
        return new TestProgress();
    }

}
