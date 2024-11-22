package ai.vespa.schemals.tree;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.grouping.ast.request;
import ai.vespa.schemals.parser.yqlplus.Node;
import ai.vespa.schemals.tree.YQL.YQLUtils;
import ai.vespa.schemals.tree.grouping.GroupingUtils;

public class YQLNode extends ai.vespa.schemals.tree.Node {
    
    private Node originalYQLNode;
    private ai.vespa.schemals.parser.grouping.Node originalGroupingNode;

    private String customText;

    public YQLNode(Node node, Position offset) {
        super(LanguageType.YQLPlus, CSTUtils.addPositionToRange(offset, YQLUtils.getNodeRange(node)), node.isDirty());
        originalYQLNode = node;

        for (Node child : node.children()) {
            addChild(new YQLNode(child, offset));
        }
    }

    public YQLNode(ai.vespa.schemals.parser.grouping.Node node, Position rangeOffset) {
        super(LanguageType.GROUPING, CSTUtils.addPositionToRange(rangeOffset, GroupingUtils.getNodeRange(node)), node.isDirty());
        originalGroupingNode = node;

        for (ai.vespa.schemals.parser.grouping.Node child : node.children()) {
            addChild(new YQLNode(child, rangeOffset));
        }
    }

    public YQLNode(Range range) {
        super(LanguageType.CUSTOM, range, false);
    }

    public YQLNode(Range range, String customText) {
        this(range);
        this.customText = customText;
    }

    public Range setRange(Range range) {
        this.range = range;
        return range;
    }

    public String getText() {
        if (language == LanguageType.YQLPlus) {
            return originalYQLNode.getSource();
        }

        if (language == LanguageType.GROUPING) {
            if (getASTClass() != request.class) {
                return originalGroupingNode.getSource();
            }

            if (originalGroupingNode.size() == 0) return "";
            // Ignore the EOF token
            var lastChild = originalGroupingNode.get(originalGroupingNode.size() - 1);
            int beginOffset = originalGroupingNode.getBeginOffset();
            int endOffset = lastChild.getBeginOffset();
            return originalGroupingNode.getTokenSource().getText(beginOffset, endOffset);
        }

        if (customText != null) return customText;

        String ret = "";
        for (int i = 0; i < size(); i++) {
            var child = get(i);
            ret += " " + child.getText();
        }

        return ret.substring(1);
    }

    public ai.vespa.schemals.parser.grouping.Node getOriginalGroupingNode() {
        return originalGroupingNode;
    }

    @Override
    public boolean isYQLNode() { return true; }

    @Override
    public YQLNode getYQLNode() { return this; }

    @Override
    public Class<?> getASTClass() {
        if (language == LanguageType.CUSTOM) return YQLNode.class;
        if (language == LanguageType.YQLPlus) return originalYQLNode.getClass();
        if (language == LanguageType.GROUPING) return originalGroupingNode.getClass();
        
        throw new RuntimeException("The YQLNode has an invalid languageType");
    }

    @Override
    public int getBeginOffset() {
        if (language == LanguageType.YQLPlus) return originalYQLNode.getBeginOffset();
        if (language == LanguageType.GROUPING) return originalGroupingNode.getBeginOffset();

        throw new RuntimeException("Could not find the begin offset of YQLNode.");
    }

    @Override
    public int getEndOffset() {
        if (language == LanguageType.YQLPlus) return originalYQLNode.getEndOffset();
        if (language == LanguageType.GROUPING) return originalGroupingNode.getEndOffset();

        if (language == LanguageType.CUSTOM && size() > 0) {
            var child = get(size() - 1);
            return child.getEndOffset();
        }

        throw new RuntimeException("Could not find the end offset of YQLNode.");
    }

    public String toString() {
        Range range = getRange();
        Position start = range.getStart();
        Position end = range.getEnd();
        return "YQLNode(" + getASTClass() + ", " + start.getLine() + "," + start.getCharacter() + "->" + end.getLine() + "," + end.getCharacter() + ")";
    }
}
