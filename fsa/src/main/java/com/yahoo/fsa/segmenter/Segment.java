// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.segmenter;

/**
 * Class encapsulation of a segment.
 *
 * @author  <a href="mailto:boros@yahoo-inc.com">Peter Boros</a>
 */
public class Segment {

    int   _beg;
    int   _end;
    int   _conn;

    public Segment(int b, int e, int c)
    {
      _beg  = b;
      _end  = e;
      _conn = c;
    }

    public int beg()
    {
      return _beg;
    }

    public int end()
    {
      return _end;
    }

    public int len()
    {
      return _end-_beg;
    }

    public int conn()
    {
      return _conn;
    }

}
