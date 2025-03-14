package ai.vespa.schemals.common;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;

import ai.vespa.schemals.parser.GeneralTokenSource;

/**
 * StringUtils
 * For dealing with specific problems related to Strings that represent text documents.
 */
public class StringUtils {
    // TODO: ideally this is configurable and 
    // possible to indent using tabs and not spaces.
    public static final int TAB_SIZE = 4;
    /*
     * If necessary, the following methods can be sped up by
     * selecting an appropriate data structure.
     * */
    public static int positionToOffset(String content, Position pos) {
        List<String> lines = content.lines().toList();
        if (pos.getLine() >= lines.size())throw new IllegalArgumentException("Line " + pos.getLine() + " out of range.");

        int lineCounter = 0;
        int offset = 0;
        for (String line : lines) {
            if (lineCounter == pos.getLine())break;
            offset += line.length() + 1; // +1 for line terminator
            lineCounter += 1;
        }

        if (pos.getCharacter() > lines.get(pos.getLine()).length())throw new IllegalArgumentException("Character " + pos.getCharacter() + " out of range for line " + pos.getLine());

        offset += pos.getCharacter();

        return offset;
    }

    public static Position offsetToPosition(String content, int offset) {
        List<String> lines = content.lines().toList();
        int lineCounter = 0;
        for (String line : lines) {
            int lengthIncludingTerminator = line.length() + 1;
            if (offset < lengthIncludingTerminator) {
                return new Position(lineCounter, offset);
            }
            offset -= lengthIncludingTerminator;
            lineCounter += 1;
        }
        return null;
    }

    public static Position positionAddOffset(String content, Position pos, int offset) {
        int totalOffset = positionToOffset(content, pos) + offset;
        return offsetToPosition(content, totalOffset);
    }

    public static String getIndentString(String content, Node node) {
        int offset = node.getBeginOffset();
        int nl = content.lastIndexOf('\n', offset) + 1;
        return content.substring(nl, offset);
    }

    public static int countSpaceIndents(String indentString) {
        if (indentString.isEmpty()) return 0;
        if (indentString.charAt(0) == '\t') {
            return indentString.length() * TAB_SIZE;
        }
        return indentString.length();
    }

    public static String spaceIndent(int indent) {
        return new String(new char[indent]).replace("\0", " ");
    }

    /**
     * Indents all lines of a string except the first one by indentDelta spaces. If indentDelta is negative it will unindent.
     * Similar to selecting a block of text and hitting tab/shift-tab (or &lt;, &gt; in vim), but not quite equal. If you try to unindent by more spaces 
     * than the current indent of a line, nothing will happen.
     *
     * @param text the text to modify
     * @param indentDelta number of spaces to add or subtract indentation.
     * @return the modified text
     */
    public static String addIndents(String text, int indentDelta) {
        if (indentDelta == 0) return text;
        if (indentDelta > 0) {
            return text.replace("\n", "\n" + spaceIndent(indentDelta));
        } else {
            return text.replace("\n" + spaceIndent(-indentDelta), "\n");
        }
    }

    public static Position getPreviousStartOfWord(String content, Position pos) {
        try {
            int offset = positionToOffset(content, pos) - 1;

            // Skip whitespace
            // But not newline because newline is a token
            while (offset >= 0 && Character.isWhitespace(content.charAt(offset)) && content.charAt(offset) != '\n')offset--;

            for (int i = offset; i >= 0; i--) {
                if (Character.isWhitespace(content.charAt(i)))return StringUtils.offsetToPosition(content, i + 1);
            }
        } catch (Exception e) {
        }

        return null;
    }

    public static boolean isInsideComment(String content, Position pos) {
        try {
            int offset = positionToOffset(content, pos);

            if (content.charAt(offset) == '\n')offset--;

            for (int i = offset; i >= 0; i--) {
                if (content.charAt(i) == '\n')break;
                if (content.charAt(i) == '#')return true;
            }
        } catch(Exception e) {

        }
        return false;
    }

    /**
     * @param rootNode Root AST node to search for single line comments
     * @param commentMarker Substring marking the start of a single line comment
     * @return A sorted list of {@link Range} where start position is location of commentMarker 
     *         and end position is location of newline character at the end of the comment.
     */
    public static List<Range> findSingleLineComments(Node rootNode, String commentMarker) {
        ArrayList<Range> ret = new ArrayList<>();
        GeneralTokenSource tokenSource = rootNode.getTokenSource();

        String content = tokenSource.toString();

        int index = content.indexOf(commentMarker);
        while (index >= 0) {
            Position start = CSTUtils.getPositionFromOffset(tokenSource, index);
            if (CSTUtils.getLeafNodeAtPosition(rootNode, start) != null) {
                index = content.indexOf(commentMarker, index + 1);
                continue;
            }

            index = content.indexOf("\n", index + 1);

            if (index < 0) {
                index = content.length() - 1;
            }

            Position end = CSTUtils.getPositionFromOffset(tokenSource, index);

            ret.add(new Range(start, end));

            index = content.indexOf(commentMarker, index + 1);
        }

        return ret;
    }

    public static Position getStringPosition(String str) {
        int lines = 0;
        int column = str.length();
        for (int i = str.length() - 1; i >= 0; i--) {
            if (str.charAt(i) == '\n') {
                if (lines == 0) {
                    column = str.length() - i - 1;
                }
                lines++;
            }
        }

        return new Position(lines, column);
    }

    public static int countNewLines(String str) {
        int ret = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\n') ret++;
        }
        return ret;
    }

    public static Range getStringRange(String str) {
        return new Range(new Position(0, 0), getStringPosition(str));
    }

}
