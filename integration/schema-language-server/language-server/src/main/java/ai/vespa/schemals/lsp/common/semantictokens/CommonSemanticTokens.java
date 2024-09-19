package ai.vespa.schemals.lsp.common.semantictokens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensServerFull;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;

import ai.vespa.schemals.lsp.schema.semantictokens.SchemaSemanticTokens;

public class CommonSemanticTokens {

    private static List<String> tokenTypes = new ArrayList<>();

    public static SemanticTokensWithRegistrationOptions getSemanticTokensRegistrationOptions() {

        // Make sure that SchemaSemanticToken is initiated
        SchemaSemanticTokens.init();

        return new SemanticTokensWithRegistrationOptions(
            new SemanticTokensLegend(tokenTypes, SemanticTokenConfig.tokenModifiers),
            new SemanticTokensServerFull(false)
        );
    }

    public static int addTokenType(String name) {
        int index = getTokenNumber(name);
        if (index == -1) {
            index = tokenTypes.size();
            tokenTypes.add(name);
        }
        return index;
    }

    public static void addTokenTypes(List<String> names) {
        for (String name : names) {
            addTokenType(name);
        }
    }

    public static Integer getTokenNumber(String token) {
        return tokenTypes.indexOf(token);
    }
}
