// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.segmenter;

import java.util.LinkedList;
import java.util.ListIterator;

import com.yahoo.fsa.FSA;

/**
 * API for accessing the Segmenter automata.
 *
 * @author Peter Boros
 */
public class Segmenter {

  private final FSA fsa;

  public Segmenter(FSA fsa) {
    this.fsa = fsa;
  }

  public Segmenter(String filename) {
    fsa = new FSA(filename, "utf-8");
  }

  public Segmenter(String filename, String charsetname) {
    fsa = new FSA(filename, charsetname);
  }

  public boolean isOk() {
    return fsa.isOk();
  }

  public Segments segment(String input) {
    String[] tokens = input.split("\\s");
    return segment(tokens);
  }

  private class Detector {

    final FSA.State state;
    final int index;

    public Detector(FSA.State s, int i) {
      state = s;
      index = i;
    }

    public FSA.State state()
    {
      return state;
    }

    public int index()
    {
      return index;
    }

  }

  public Segments segment(String[] tokens) {
    Segments segments = new Segments(tokens);
    LinkedList<Detector> detectors = new LinkedList<>();

    int i=0;


    while(i<tokens.length){
      detectors.add(new Detector(fsa.getState(), i));

      ListIterator<Detector> det_it = detectors.listIterator();
      while(det_it.hasNext()){
        Detector d = det_it.next();
        d.state().deltaWord(tokens[i]);
        if(d.state().isFinal()){
          segments.add(new Segment(d.index(),i+1,d.state().data().getInt(0)));
        }

        if(!d.state().isValid()){
          det_it.remove();
        }
      }
      i++;
    }

    return segments;
  }

}

