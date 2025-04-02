package ai.vespa.schemals;

import java.util.UUID;

import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

public class SchemaProgressHandler {

    private LanguageClient client;

    public static class Progress {
        private final Either<String, Integer> token;
        private final LanguageClient client;
        private final boolean sendPercentage;

        protected Progress(LanguageClient client, Either<String, Integer> token) {
            this.client = client;
            this.token = token;
            this.sendPercentage = false;
        }

        Progress(LanguageClient client, String title, boolean sendPercentage) {
            this.token = Either.forLeft(UUID.randomUUID().toString());
            this.client = client;
            this.sendPercentage = sendPercentage;

            client.createProgress(
                new WorkDoneProgressCreateParams(
                    this.token
                )
            );


            WorkDoneProgressBegin beginReport = new WorkDoneProgressBegin();
            beginReport.setTitle(title);
            if (sendPercentage) {
                beginReport.setPercentage(0);
            }
            sendNotification(beginReport);
        }

        private void sendNotification(WorkDoneProgressNotification notification) {
            client.notifyProgress(new ProgressParams(
                token, 
                Either.forRight(notification)
            ));
        }

        public void partialResult(String message, Integer percentage) {
            WorkDoneProgressReport report = new WorkDoneProgressReport();

            report.setMessage(message);
            report.setPercentage(percentage);

            sendNotification(report);
        }

        public void partialResult(String message) {
            if (sendPercentage) {
                throw new IllegalArgumentException("For Progresses with percentage, a percentage must be given");
            }

            partialResult(message, null);
        }

        public void end(String message) {
            WorkDoneProgressEnd report = new WorkDoneProgressEnd();
            if (message != null) {
                report.setMessage(message);
            }
            sendNotification(report);
        }

        public void end() {
            end(null);
        }
    }

    void connectClient(LanguageClient client) {
        this.client = client;
    }

    public boolean connected() {
        return client != null;
    }

    /**
     * This is used for server initiated Progress
     */
    public Progress newWorkDoneProgress(String title) {
        return new Progress(client, title, true);
    }

}
