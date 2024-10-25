package ai.vespa.schemals.lsp.common.semantictokens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensServerFull;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;

import ai.vespa.schemals.lsp.schema.semantictokens.SchemaSemanticTokens;
import ai.vespa.schemals.lsp.yqlplus.semantictokens.YQLPlusSemanticTokens;

public class CommonSemanticTokens {

    private static List<String> tokenTypes = new ArrayList<>();
    private static List<String> tokenModifiers = new ArrayList<>();

    public static SemanticTokensWithRegistrationOptions getSemanticTokensRegistrationOptions() {

        // Make sure that SchemaSemanticToken is initiated
        SchemaSemanticTokens.init();
        YQLPlusSemanticTokens.init();

        return new SemanticTokensWithRegistrationOptions(
            new SemanticTokensLegend(tokenTypes, tokenModifiers),
            new SemanticTokensServerFull(false)
        );
    }

    private static int addUniqueToList(List<String> list, String element) {
        int index = list.indexOf(element);
        if (index == -1) {
            index = list.size();
            list.add(element);
        }
        return index;
    }

    public static int addTokenType(String name) {
        return addUniqueToList(tokenTypes, name);
    }

    public static void addTokenTypes(List<String> names) {
        for (String name : names) {
            addTokenType(name);
        }
    }

    public static Integer getType(String token) {
        return tokenTypes.indexOf(token);
    }

    public static int addTokenModifier(String modifier) {
        return addUniqueToList(tokenModifiers, modifier);
    }

    public static void addTokenModifiers(List<String> modifiers) {
        for (String modifier : modifiers) {
            addTokenModifier(modifier);
        }
    }

    public static Integer getModifier(String modifier) {
        return tokenModifiers.indexOf(modifier);
    }
}
