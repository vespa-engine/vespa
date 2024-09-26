package ai.vespa.schemals.lsp.yqlplus.semantictokens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.lsp.common.semantictokens.CommonSemanticTokens;
import ai.vespa.schemals.lsp.common.semantictokens.SemanticTokenMarker;
import ai.vespa.schemals.parser.yqlplus.ast.pipeline_step;
import ai.vespa.schemals.tree.YQLNode;

public class YQLPlusSemanticTokens {

    public static void init() {
        CommonSemanticTokens.addTokenType(SemanticTokenTypes.Keyword);
        CommonSemanticTokens.addTokenTypes(new ArrayList<String>(YQLPlusSemanticTokenConfig.tokensMap.values()));

    }

    private static List<SemanticTokenMarker> traverseCST(YQLNode node, ClientLogger logger) {
        List<SemanticTokenMarker> ret = new ArrayList<>();

        if (node.getIsDirty()) return ret;

        Class<?> nodeClass = node.getASTClass();
        String tokenString = YQLPlusSemanticTokenConfig.tokensMap.get(nodeClass);
        if (tokenString != null) {
            int tokenType = CommonSemanticTokens.getType(tokenString);
            ret.add(new SemanticTokenMarker(tokenType, node));
        } else if (YQLPlusSemanticTokenConfig.keywordTokens.contains(nodeClass)) {
            int keywordType = CommonSemanticTokens.getType(SemanticTokenTypes.Keyword);
            ret.add(new SemanticTokenMarker(keywordType, node));
        } else if (node.getASTClass() != pipeline_step.class) {
            for (YQLNode child : node) {
                ret.addAll(traverseCST(child, logger));
            }
        }

        return ret;
    }

    public static SemanticTokens getSemanticTokens(EventDocumentContext context) {

        if (context.document == null) {
            return new SemanticTokens(new ArrayList<>());
        }

        YQLNode node = context.document.getRootYQLNode();
        if (node == null) {
            return new SemanticTokens(new ArrayList<>());
        }

        List<SemanticTokenMarker> markers = traverseCST(node, context.logger);
        List<Integer> compactMarkers = SemanticTokenMarker.concatCompactForm(markers);

        return new SemanticTokens(compactMarkers);
    }
}
