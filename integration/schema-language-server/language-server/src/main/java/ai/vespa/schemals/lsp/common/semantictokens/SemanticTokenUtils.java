package ai.vespa.schemals.lsp.common.semantictokens;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Range;

public class SemanticTokenUtils {
    public static List<SemanticTokenMarker> convertCommentRanges(List<Range> commentRanges) {
        int commentTokenType = CommonSemanticTokens.getType("comment");
        return commentRanges.stream()
                            .map(range -> new SemanticTokenMarker(commentTokenType, range))
                            .collect(Collectors.toList());
    }
}
