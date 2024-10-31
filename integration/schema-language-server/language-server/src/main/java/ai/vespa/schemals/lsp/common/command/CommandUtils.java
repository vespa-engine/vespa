package ai.vespa.schemals.lsp.common.command;

import java.util.Optional;

import com.google.gson.JsonPrimitive;

public class CommandUtils {
    public static Optional<String> getStringArgument(Object jsonObject) {
        if (!(jsonObject instanceof JsonPrimitive))
            return Optional.empty();
        JsonPrimitive arg = (JsonPrimitive) jsonObject;
        return Optional.of(arg.getAsString());
    }
}
