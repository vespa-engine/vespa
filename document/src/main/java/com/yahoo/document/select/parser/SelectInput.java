// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.parser;

import com.yahoo.javacc.FastCharStream;

/**
 * @author Simon Thoresen Hult
 */
public class SelectInput extends FastCharStream implements CharStream {

    public SelectInput(String input) {
        super(input);
    }

    public boolean getTrackLineColumn() {  return true; } 

    public void Done() { } 


  /**
   * Returns the next character from the selected input.  The method
   * of selecting the input is the responsibility of the class
   * implementing this interface.  Can throw any java.io.IOException.
   */
  public char readChar() throws java.io.IOException { return 0; }

  @Deprecated
  /**
   * Returns the column position of the character last read.
   * @deprecated
   * @see #getEndColumn
   */
  public int getColumn() { return 0; }

  @Deprecated
  /**
   * Returns the line number of the character last read.
   * @deprecated
   * @see #getEndLine
   */
  public int getLine() { return 0;}

    public void backup(int amount) { }

  /**
   * Returns the next character that marks the beginning of the next token.
   * All characters must remain in the buffer between two successive calls
   * to this method to implement backup correctly.
   */
  public char BeginToken() throws java.io.IOException { return 0; }

  /**
   * Returns a string made up of characters from the marked token beginning
   * to the current buffer position. Implementations have the choice of returning
   * anything that they want to. For example, for efficiency, one might decide
   * to just return null, which is a valid implementation.
   */
  public String GetImage() { return ""; }

  /**
   * Returns an array of characters that make up the suffix of length 'len' for
   * the currently matched token. This is used to build up the matched string
   * for use in actions in the case of MORE. A simple and inefficient
   * implementation of this is as follows :
   *
   *   {
   *      String t = GetImage();
   *      return t.substring(t.length() - len, t.length()).toCharArray();
   *   }
   */
  public char[] GetSuffix(int len) { return null;}
}
