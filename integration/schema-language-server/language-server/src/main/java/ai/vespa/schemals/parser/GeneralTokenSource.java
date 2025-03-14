package ai.vespa.schemals.parser;

/**
 * Interface to capture logic needed from the different parser's generated TokenSource classes.
 */
public interface GeneralTokenSource extends CharSequence {
    public String getInputSource();
    public String getText(int startOffset, int endOffset);
    public String toString();
    public CharSequence subSequence(int start, int end);
    public char charAt(int pos);
    public int length();
    public int getCodePointColumnFromOffset(int pos);
    public int getLineEndOffset(int lineNumber);
    public int getLineFromOffset(int pos);
    public int getLineStartOffset(int lineNumber);
    public void setInputSource(String inputSource);
}
