package ai.vespa.schemals.common;

import java.util.List;

import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.tree.SchemaNode;

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

    public static String getIndentString(String content, SchemaNode node) {
        int offset = node.getOriginalBeginOffset();
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

}
