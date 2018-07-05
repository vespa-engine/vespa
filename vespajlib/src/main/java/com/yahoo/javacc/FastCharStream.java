// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.javacc;

import java.io.IOException;

/**
 * @author Simon Thoresen Hult
 */
public class FastCharStream {

    private static final String JAVACC_EXCEPTION_FORMAT = "line -1, column ";
    private static final IOException EOF = new IOException();
    private final String inputStr;
    private final char[] inputArr;
    private int tokenPos = 0;
    private int readPos = 0;

    public FastCharStream(String input) {
        this.inputStr = input;
        this.inputArr = input.toCharArray();
    }

    public char readChar() throws IOException {
        if (readPos >= inputArr.length) {
            throw EOF;
        }
        return inputArr[readPos++];
    }

    @Deprecated
    public int getColumn() {
        return getEndColumn();
    }

    @Deprecated
    public int getLine() {
        return getEndLine();
    }

    public int getEndColumn() {
        return readPos + 1;
    }

    public int getEndLine() {
        return -1; // indicate unset
    }

    public int getBeginColumn() {
        return tokenPos + 1;
    }

    public int getBeginLine() {
        return -1; // indicate unset
    }

    public void backup(int amount) {
        readPos -= amount;
    }

    public char BeginToken() throws IOException {
        tokenPos = readPos;
        return readChar();
    }

    public String GetImage() {
        return inputStr.substring(tokenPos, readPos);
    }

    @SuppressWarnings("UnusedParameters")
    public char[] GetSuffix(int len) {
        throw new UnsupportedOperationException();
    }

    public void Done() {

    }

    public String formatException(String parseException) {
        int errPos = findErrPos(parseException);
        if (errPos < 0 || errPos > inputArr.length + 1) {
            return parseException;
        }
        int errLine = 0;
        int errColumn = 0;
        for (int i = 0; i < errPos - 1; ++i) {
            if (inputStr.charAt(i) == '\n') {
                ++errLine;
                errColumn = 0;
            } else {
                ++errColumn;
            }
        }
        StringBuilder out = new StringBuilder();
        out.append(parseException.replace(JAVACC_EXCEPTION_FORMAT + errPos,
                                          "line " + (errLine + 1) + ", column " + (errColumn + 1)));
        out.append("\nAt position:\n");
        appendErrorPosition(errLine, out);
        for (int i = 0; i < errColumn; ++i) {
            out.append(" ");
        }
        out.append("^");
        return out.toString();
    }

    private void appendErrorPosition(int errLine, StringBuilder out) {        
        String[] inputStrLines = inputStr.split("\n");
        if (inputStrLines.length<errLine+1) {
            out.append("EOF\n");
        } else {
            out.append(inputStrLines[errLine]).append("\n");
        }
    }

    private static int findErrPos(String str) {
        int from = str.indexOf(JAVACC_EXCEPTION_FORMAT);
        if (from < 0) {
            return -1;
        }
        from = from + JAVACC_EXCEPTION_FORMAT.length();

        int to = from;
        while (to < str.length() && Character.isDigit(str.charAt(to))) {
            ++to;
        }
        if (to == from) {
            return -1;
        }

        return Integer.valueOf(str.substring(from, to));
    }
}
