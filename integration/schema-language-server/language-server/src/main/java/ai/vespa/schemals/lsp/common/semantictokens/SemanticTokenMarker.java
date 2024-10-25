package ai.vespa.schemals.lsp.common.semantictokens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.tree.SchemaNode;

public class SemanticTokenMarker {
    private static final int LINE_INDEX = 0;
    private static final int COLUMN_INDEX = 1;

    private int tokenType;
    private int modifierValue = 0;
    private Range range;

    public SemanticTokenMarker(int tokenType, SchemaNode node) {
        this(tokenType, node.getRange());
    }
    
    public SemanticTokenMarker(int tokenType, Range range) {
        this.tokenType = tokenType;
        this.range = range;
    }

    public Range getRange() { return range; }

    public void addModifier(String modifier) {
        int modifierIndex = CommonSemanticTokens.getModifier(modifier);
        if (modifierIndex == -1) {
            throw new IllegalArgumentException("Could not find the semantic token modifier '" + modifier + "'. Remember to add the modifier to the tokenModifiers list.");
        }
        int bitMask = 1 << modifierIndex;
        modifierValue = modifierValue | bitMask;
    }

    private ArrayList<Integer> compactForm() {
        int length = range.getEnd().getCharacter() - range.getStart().getCharacter();

        return new ArrayList<Integer>() {{
            add(range.getStart().getLine());
            add(range.getStart().getCharacter());
            add(length);
            add(tokenType);
            add(modifierValue);
        }};
    }

    public static List<Integer> concatCompactForm(List<SemanticTokenMarker> markers) {
        List<Integer> ret = new ArrayList<>(markers.size() * 5);

        if (markers.size() == 0) {
            return ret;
        }

        ret.addAll(markers.get(0).compactForm());

        for (int i = 1; i < markers.size(); i++) {
            ArrayList<Integer> markerCompact = markers.get(i).compactForm();
            ArrayList<Integer> lastMarkerCompact = markers.get(i - 1).compactForm();
            markerCompact.set(LINE_INDEX, markerCompact.get(LINE_INDEX) - lastMarkerCompact.get(LINE_INDEX));
            if (markerCompact.get(LINE_INDEX) == 0) {
                markerCompact.set(COLUMN_INDEX, markerCompact.get(COLUMN_INDEX) - lastMarkerCompact.get(COLUMN_INDEX));
            }
            ret.addAll(markerCompact);
        }

        return ret;
    }
}
