// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * This is adapted from the Lucene code base which is Copyright 2008 Apache Software Foundation and Licensed
 * under the terms of the Apache License, Version 2.0.
 */
package com.yahoo.language.simple.kstem;


import java.io.IOException;
import java.io.Reader;

/**
 * {@link CharacterUtils} provides a unified interface to Character-related
 * operations to implement backwards compatible character operations.
 */
public abstract class CharacterUtils {

  private static final Java4CharacterUtils JAVA_4 = new Java4CharacterUtils();
  private static final Java5CharacterUtils JAVA_5 = new Java5CharacterUtils();

  /**
   * Returns a {@link CharacterUtils} implementation.
   */
  public static CharacterUtils getInstance() {
    return JAVA_5;
  }

  /**
   * Returns the code point at the given index of the {@link CharSequence}.
   * 
   * @param seq
   *          a character sequence
   * @param offset
   *          the offset to the char values in the chars array to be converted
   * 
   * @return the Unicode code point at the given index
   * @throws NullPointerException
   *           - if the sequence is null.
   * @throws IndexOutOfBoundsException
   *           - if the value offset is negative or not less than the length of
   *           the character sequence.
   */
  public abstract int codePointAt(final CharSequence seq, final int offset);
  
  /**
   * Returns the code point at the given index of the char array where only elements
   * with index less than the limit are used.
   * 
   * @param chars
   *          a character array
   * @param offset
   *          the offset to the char values in the chars array to be converted
   * @param limit the index afer the last element that should be used to calculate
   *        codepoint.  
   * 
   * @return the Unicode code point at the given index
   * @throws NullPointerException
   *           - if the array is null.
   * @throws IndexOutOfBoundsException
   *           - if the value offset is negative or not less than the length of
   *           the char array.
   */
  public abstract int codePointAt(final char[] chars, final int offset, final int limit);

  /** Return the number of characters in <code>seq</code>. */
  public abstract int codePointCount(CharSequence seq);

  /**
   * Creates a new {@link CharacterBuffer} and allocates a <code>char[]</code>
   * of the given bufferSize.
   * 
   * @param bufferSize
   *          the internal char buffer size, must be <code>&gt;= 2</code>
   * @return a new {@link CharacterBuffer} instance.
   */
  public static CharacterBuffer newCharacterBuffer(final int bufferSize) {
    if (bufferSize < 2) {
      throw new IllegalArgumentException("buffersize must be >= 2");
    }
    return new CharacterBuffer(new char[bufferSize], 0, 0);
  }
  
  
  /**
   * Converts each unicode codepoint to lowerCase via {@link Character#toLowerCase(int)} starting 
   * at the given offset.
   * @param buffer the char buffer to lowercase
   * @param offset the offset to start at
   * @param limit the max char in the buffer to lower case
   */
  public final void toLowerCase(final char[] buffer, final int offset, final int limit) {
    assert buffer.length >= limit;
    assert offset <=0 && offset <= buffer.length;
    for (int i = offset; i < limit;) {
      i += Character.toChars(
              Character.toLowerCase(
                  codePointAt(buffer, i, limit)), buffer, i);
     }
  }

  /**
   * Converts each unicode codepoint to UpperCase via {@link Character#toUpperCase(int)} starting 
   * at the given offset.
   * @param buffer the char buffer to UPPERCASE
   * @param offset the offset to start at
   * @param limit the max char in the buffer to lower case
   */
  public final void toUpperCase(final char[] buffer, final int offset, final int limit) {
    assert buffer.length >= limit;
    assert offset <=0 && offset <= buffer.length;
    for (int i = offset; i < limit;) {
      i += Character.toChars(
              Character.toUpperCase(
                  codePointAt(buffer, i, limit)), buffer, i);
     }
  }

  /** Converts a sequence of Java characters to a sequence of unicode code points.
   *  @return the number of code points written to the destination buffer */
  public final int toCodePoints(char[] src, int srcOff, int srcLen, int[] dest, int destOff) {
    if (srcLen < 0) {
      throw new IllegalArgumentException("srcLen must be >= 0");
    }
    int codePointCount = 0;
    for (int i = 0; i < srcLen; ) {
      final int cp = codePointAt(src, srcOff + i, srcOff + srcLen);
      final int charCount = Character.charCount(cp);
      dest[destOff + codePointCount++] = cp;
      i += charCount;
    }
    return codePointCount;
  }

  /** Converts a sequence of unicode code points to a sequence of Java characters.
   *  @return the number of chars written to the destination buffer */
  public final int toChars(int[] src, int srcOff, int srcLen, char[] dest, int destOff) {
    if (srcLen < 0) {
      throw new IllegalArgumentException("srcLen must be >= 0");
    }
    int written = 0;
    for (int i = 0; i < srcLen; ++i) {
      written += Character.toChars(src[srcOff + i], dest, destOff + written);
    }
    return written;
  }

  /**
   * Fills the {@link CharacterBuffer} with characters read from the given
   * reader {@link Reader}. This method tries to read <code>numChars</code>
   * characters into the {@link CharacterBuffer}, each call to fill will start
   * filling the buffer from offset <code>0</code> up to <code>numChars</code>.
   * In case code points can span across 2 java characters, this method may
   * only fill <code>numChars - 1</code> characters in order not to split in
   * the middle of a surrogate pair, even if there are remaining characters in
   * the {@link Reader}.
   * <p>
   * This method guarantees
   * that the given {@link CharacterBuffer} will never contain a high surrogate
   * character as the last element in the buffer unless it is the last available
   * character in the reader. In other words, high and low surrogate pairs will
   * always be preserved across buffer boarders.
   * </p>
   * <p>
   * A return value of <code>false</code> means that this method call exhausted
   * the reader, but there may be some bytes which have been read, which can be
   * verified by checking whether <code>buffer.getLength() &gt; 0</code>.
   * </p>
   * 
   * @param buffer
   *          the buffer to fill.
   * @param reader
   *          the reader to read characters from.
   * @param numChars
   *          the number of chars to read
   * @return <code>false</code> if and only if reader.read returned -1 while trying to fill the buffer
   * @throws IOException
   *           if the reader throws an {@link IOException}.
   */
  public abstract boolean fill(CharacterBuffer buffer, Reader reader, int numChars) throws IOException;

  /** Convenience method which calls <code>fill(buffer, reader, buffer.buffer.length)</code>. */
  public final boolean fill(CharacterBuffer buffer, Reader reader) throws IOException {
    return fill(buffer, reader, buffer.buffer.length);
  }

  /** Return the index within <code>buf[start:start+count]</code> which is by <code>offset</code>
   *  code points from <code>index</code>. */
  public abstract int offsetByCodePoints(char[] buf, int start, int count, int index, int offset);

  static int readFully(Reader reader, char[] dest, int offset, int len) throws IOException {
    int read = 0;
    while (read < len) {
      final int r = reader.read(dest, offset + read, len - read);
      if (r == -1) {
        break;
      }
      read += r;
    }
    return read;
  }

  private static final class Java5CharacterUtils extends CharacterUtils {
    Java5CharacterUtils() {
    }

    @Override
    public int codePointAt(final CharSequence seq, final int offset) {
      return Character.codePointAt(seq, offset);
    }

    @Override
    public int codePointAt(final char[] chars, final int offset, final int limit) {
     return Character.codePointAt(chars, offset, limit);
    }

    @Override
    public boolean fill(final CharacterBuffer buffer, final Reader reader, int numChars) throws IOException {
      assert buffer.buffer.length >= 2;
      if (numChars < 2 || numChars > buffer.buffer.length) {
        throw new IllegalArgumentException("numChars must be >= 2 and <= the buffer size");
      }
      final char[] charBuffer = buffer.buffer;
      buffer.offset = 0;
      final int offset;

      // Install the previously saved ending high surrogate:
      if (buffer.lastTrailingHighSurrogate != 0) {
        charBuffer[0] = buffer.lastTrailingHighSurrogate;
        buffer.lastTrailingHighSurrogate = 0;
        offset = 1;
      } else {
        offset = 0;
      }

      final int read = readFully(reader, charBuffer, offset, numChars - offset);

      buffer.length = offset + read;
      final boolean result = buffer.length == numChars;
      if (buffer.length < numChars) {
        // We failed to fill the buffer. Even if the last char is a high
        // surrogate, there is nothing we can do
        return result;
      }

      if (Character.isHighSurrogate(charBuffer[buffer.length - 1])) {
        buffer.lastTrailingHighSurrogate = charBuffer[--buffer.length];
      }
      return result;
    }

    @Override
    public int codePointCount(CharSequence seq) {
      return Character.codePointCount(seq, 0, seq.length());
    }

    @Override
    public int offsetByCodePoints(char[] buf, int start, int count, int index, int offset) {
      return Character.offsetByCodePoints(buf, start, count, index, offset);
    }
  }

  private static final class Java4CharacterUtils extends CharacterUtils {
    Java4CharacterUtils() {
    }

    @Override
    public int codePointAt(final CharSequence seq, final int offset) {
      return seq.charAt(offset);
    }

    @Override
    public int codePointAt(final char[] chars, final int offset, final int limit) {
      if(offset >= limit)
        throw new IndexOutOfBoundsException("offset must be less than limit");
      return chars[offset];
    }

    @Override
    public boolean fill(CharacterBuffer buffer, Reader reader, int numChars)
        throws IOException {
      assert buffer.buffer.length >= 1;
      if (numChars < 1 || numChars > buffer.buffer.length) {
        throw new IllegalArgumentException("numChars must be >= 1 and <= the buffer size");
      }
      buffer.offset = 0;
      final int read = readFully(reader, buffer.buffer, 0, numChars);
      buffer.length = read;
      buffer.lastTrailingHighSurrogate = 0;
      return read == numChars;
    }

    @Override
    public int codePointCount(CharSequence seq) {
      return seq.length();
    }

    @Override
    public int offsetByCodePoints(char[] buf, int start, int count, int index, int offset) {
      final int result = index + offset;
      if (result < 0 || result > count) {
        throw new IndexOutOfBoundsException();
      }
      return result;
    }

  }
  
  /**
   * A simple IO buffer to use with
   * {@link CharacterUtils#fill(CharacterBuffer, Reader)}.
   */
  public static final class CharacterBuffer {
    
    private final char[] buffer;
    private int offset;
    private int length;
    // NOTE: not private so outer class can access without
    // $access methods:
    char lastTrailingHighSurrogate;
    
    CharacterBuffer(char[] buffer, int offset, int length) {
      this.buffer = buffer;
      this.offset = offset;
      this.length = length;
    }
    
    /**
     * Returns the internal buffer
     * 
     * @return the buffer
     */
    public char[] getBuffer() {
      return buffer;
    }
    
    /**
     * Returns the data offset in the internal buffer.
     * 
     * @return the offset
     */
    public int getOffset() {
      return offset;
    }
    
    /**
     * Return the length of the data in the internal buffer starting at
     * {@link #getOffset()}
     * 
     * @return the length
     */
    public int getLength() {
      return length;
    }
    
    /**
     * Resets the CharacterBuffer. All internals are reset to its default
     * values.
     */
    public void reset() {
      offset = 0;
      length = 0;
      lastTrailingHighSurrogate = 0;
    }
  }

}
