package ai.vespa.schemals.common;

import java.util.List;

import org.eclipse.lsp4j.Position;

/**
 * StringUtils
 * For dealing with specific problems related to Strings that represent text documents.
 */
public class StringUtils {
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
}
