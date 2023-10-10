// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.segmenter;

/**
 * Class encapsulation of a segment.
 *
 * @author Peter Boros
 */
public class Segment {

    final int begin;
    final int end;
    final int conn;

    public Segment(int b, int e, int c) {
      begin = b;
      end = e;
      conn = c;
    }

    public int beg()
    {
      return begin;
    }

    public int end()
    {
      return end;
    }

    public int len()
    {
      return end - begin;
    }

    public int conn()
    {
      return conn;
    }

}
