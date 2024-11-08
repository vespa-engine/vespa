package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.commons.math3.analysis.function.Pow;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.editbuilder.WorkspaceEditBuilder;
import ai.vespa.schemals.context.EventExecuteCommandContext;

public class RunVespaQuery implements SchemaCommand {

    private String queryCommand;
    private String sourceFileURI;

    public int getArity() {
        return 2;
    }

    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        if (!(arguments.get(0) instanceof JsonPrimitive))
            return false;

        if (!(arguments.get(1) instanceof JsonPrimitive))
            return false;

        JsonPrimitive arg0 = (JsonPrimitive) arguments.get(0);
        queryCommand = arg0.getAsString();

        JsonPrimitive arg1 = (JsonPrimitive) arguments.get(1);
        sourceFileURI = arg1.getAsString();
        return true;
    }

    public Object execute(EventExecuteCommandContext context) {
        context.logger.info("Running Vespa query: " + queryCommand);

        QueryResult result = runVespaQuery(queryCommand, context.logger);

        if (!result.success()) {
            if (result.result().toLowerCase().contains("command not found")) {
                context.messageHandler.sendMessage(MessageType.Error, "Could not find vespa CLI. Make sure vespa CLI is installed and added to path. Download vespa CLI here: https://docs.vespa.ai/en/vespa-cli.html");
                return null;
            }
            context.messageHandler.sendMessage(MessageType.Error, "Failed to run query:\n" + result.result());
            return null;
        }

        String response = result.result();

        String targetFileURI = getTargetFileURI(sourceFileURI);

        CreateFile newFile = new CreateFile(targetFileURI);

        TextEdit textEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), response);

        WorkspaceEdit wsEdit = new WorkspaceEditBuilder()
            .addResourceOperation(newFile)
            .addTextEdit(targetFileURI, textEdit)
            .build();

        context.messageHandler.applyEdit(new ApplyWorkspaceEditParams(wsEdit)).thenRun(() -> {
            context.messageHandler.showDocument(targetFileURI);
        });


        return null;
    }

    private static String getTargetFileURI(String sourceFileURI) {
        Path filePath = Paths.get(sourceFileURI);
        String fileName = filePath.getFileName().toString();

        fileName = fileName.substring(0, fileName.lastIndexOf('.'));

        ZonedDateTime now = ZonedDateTime.now();
        String isoDateTime = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        fileName += "." + isoDateTime + ".json";

        return filePath.getParent().resolve(fileName).toString();
    }

    private record QueryResult(boolean success, String result) {};

    private QueryResult runVespaQuery(String query, ClientLogger logger) {

        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

        ProcessBuilder builder = new ProcessBuilder();

        String queryEscaped = query.replace("\"", "\\\"");
        String vespaCommand = String.format("vespa query \"%s\"", queryEscaped);

        if (isWindows) {
            builder.command("cmd.exe", "/c", vespaCommand); // TODO: Test this on windows
        } else {
            builder.command("/bin/sh", "-c", vespaCommand);
        }

        try {

            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return new QueryResult(true, output.toString());
            }

            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder error = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }

            return new QueryResult(false, error.toString());

        } catch (InterruptedException e) {
            return new QueryResult(false, "Program interrupted");
        } catch (IOException e) {
            logger.error(e.getMessage());
            return new QueryResult(false, "IOException occurred.");
        }
    }

}
