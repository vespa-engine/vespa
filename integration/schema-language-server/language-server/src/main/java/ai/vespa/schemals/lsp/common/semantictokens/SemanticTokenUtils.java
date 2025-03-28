package ai.vespa.schemals.lsp.common.semantictokens;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.tree.CSTUtils;

public class SemanticTokenUtils {
    public static List<SemanticTokenMarker> convertCommentRanges(List<Range> commentRanges) {
        int commentTokenType = CommonSemanticTokens.getType("comment");
        return commentRanges.stream()
                            .map(range -> new SemanticTokenMarker(commentTokenType, range))
                            .collect(Collectors.toList());
    }

    // This function assumes that both of the lists are sorted, and that no elements are overlapping
    public static List<SemanticTokenMarker> mergeSemanticTokenMarkers(List<SemanticTokenMarker> lhs, List<SemanticTokenMarker> rhs) {
        List<SemanticTokenMarker> ret = new ArrayList<>(lhs.size() + rhs.size());

        int lhsIndex = 0;
        int rhsIndex = 0;
        while (
            lhsIndex < lhs.size() &&
            rhsIndex < rhs.size()
        ) {
            Position rhsPos = rhs.get(rhsIndex).getRange().getStart();
            Position lhsPos = lhs.get(lhsIndex).getRange().getStart();

            if (CSTUtils.positionLT(lhsPos, rhsPos)) {
                ret.add(lhs.get(lhsIndex));
                lhsIndex++;
            } else {
                ret.add(rhs.get(rhsIndex));
                rhsIndex++;
            }
        }

        for (int i = lhsIndex; i < lhs.size(); i++) {
            ret.add(lhs.get(i));
        }

        for (int i = rhsIndex; i < rhs.size(); i++) {
            ret.add(rhs.get(i));
        }

        return ret;
    }

}
