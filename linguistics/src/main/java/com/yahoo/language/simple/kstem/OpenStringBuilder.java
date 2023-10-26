// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * This is adapted from the Lucene code base which is Copyright 2008 Apache Software Foundation and Licensed
 * under the terms of the Apache License, Version 2.0.
 */
package com.yahoo.language.simple.kstem;

/**
 * A StringBuilder that allows one to access the array.
 */
public class OpenStringBuilder implements Appendable, CharSequence {

  protected char[] buf;
  protected int len;

  public OpenStringBuilder() {
    this(32);
  }

  public OpenStringBuilder(int size) {
    buf = new char[size];
  }

  public void setLength(int len) { this.len = len; }

  public void set(char[] arr, int end) {
    this.buf = arr;
    this.len = end;
  }

  public char[] getArray() { return buf; }
  public int size() { return len; }
  @Override
  public int length() { return len; }
  public int capacity() { return buf.length; }

  @Override
  public Appendable append(CharSequence csq) {
    return append(csq, 0, csq.length());
  }

  @Override
  public Appendable append(CharSequence csq, int start, int end) {
    reserve(end-start);
    for (int i=start; i<end; i++) {
      unsafeWrite(csq.charAt(i));
    }
    return this;
  }

  @Override
  public Appendable append(char c) {
    write(c);
    return this;
  }

  @Override
  public char charAt(int index) {
    return buf[index];
  }

  public void setCharAt(int index, char ch) {
    buf[index] = ch;    
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    throw new UnsupportedOperationException(); // todo
  }

  public void unsafeWrite(char b) {
    buf[len++] = b;
  }

  public void unsafeWrite(char b[], int off, int len) {
    System.arraycopy(b, off, buf, this.len, len);
    this.len += len;
  }

  protected void resize(int len) {
    char newbuf[] = new char[Math.max(buf.length << 1, len)];
    System.arraycopy(buf, 0, newbuf, 0, size());
    buf = newbuf;
  }

  public void reserve(int num) {
    if (len + num > buf.length) resize(len + num);
  }

  public void write(char b) {
    if (len >= buf.length) {
      resize(len +1);
    }
    unsafeWrite(b);
  }

  public void write(int b) { write((char)b); }

  public final void write(char[] b) {
    write(b,0,b.length);
  }

  public void write(char b[], int off, int len) {
    reserve(len);
    unsafeWrite(b, off, len);
  }

  public final void write(OpenStringBuilder arr) {
    write(arr.buf, 0, len);
  }

  public void write(String s) {
    reserve(s.length());
    s.getChars(0,s.length(),buf, len);
    len +=s.length();
  }

  public void flush() {
  }

  public final void reset() {
    len =0;
  }

  public char[] toCharArray() {
    char newbuf[] = new char[size()];
    System.arraycopy(buf, 0, newbuf, 0, size());
    return newbuf;
  }

  @Override
  public String toString() {
    return new String(buf, 0, size());
  }

}
