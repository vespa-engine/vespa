package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.eclipse.lsp4j.MessageType;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventExecuteCommandContext;

public class RunVespaQuery implements SchemaCommand {

    private String queryCommand;

    public int getArity() {
        return 1;
    }

    public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        if (!(arguments.get(0) instanceof JsonPrimitive))
            return false;

        JsonPrimitive arg = (JsonPrimitive) arguments.get(0);
        queryCommand = arg.getAsString();
        return true;
    }

    public Object execute(EventExecuteCommandContext context) {
        context.logger.info("Running Vespa query...");
        context.logger.info(queryCommand);

        QueryResult result = runVespaQuery(queryCommand, context.logger);

        if (!result.success()) {
            context.messageHandler.sendMessage(MessageType.Error, "Failed to run query:\n" + result.result());
            return null;
        }

        context.logger.info(result.result());

        return result.result();
    }

    private record QueryResult(boolean success, String result) {};

    private QueryResult runVespaQuery(String query, ClientLogger logger) {

        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

        ProcessBuilder builder = new ProcessBuilder();

        if (isWindows) {
            builder.command(String.format("cmd.exe /c %s", query)); // TODO: Fix this for window
        } else {
            builder.command("/usr/local/bin/vespa", "query", query);
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
