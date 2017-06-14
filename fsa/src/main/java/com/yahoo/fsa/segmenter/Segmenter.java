// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.segmenter;

import java.util.LinkedList;
import java.util.ListIterator;

import com.yahoo.fsa.FSA;

/**
 * API for accessing the Segmenter automata.
 *
 * @author  <a href="mailto:boros@yahoo-inc.com">Peter Boros</a>
 */
public class Segmenter {

  private FSA _fsa;

  public Segmenter(FSA fsa) {
    _fsa = fsa;
  }

  public Segmenter(String filename) {
    _fsa = new FSA(filename,"utf-8");
  }

  public Segmenter(String filename, String charsetname) {
    _fsa = new FSA(filename,charsetname);
  }

  public boolean isOk()
  {
    return _fsa.isOk();
  }

  public Segments segment(String input)
  {
    String[] tokens = input.split("\\s");
    return segment(tokens);
  }

  private class Detector {
    FSA.State _state;
    int       _index;

    public Detector(FSA.State s, int i)
    {
      _state = s;
      _index = i;
    }

    public FSA.State state()
    {
      return _state;
    }

    public int index()
    {
      return _index;
    }
  }

  public Segments segment(String[] tokens)
  {
    Segments segments = new Segments(tokens);
    LinkedList detectors = new LinkedList();

    int i=0;


    while(i<tokens.length){
      detectors.add(new Detector(_fsa.getState(),i));

      ListIterator det_it = detectors.listIterator();
      while(det_it.hasNext()){
        Detector d = (Detector)det_it.next();
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

  //// test ////
  public static void main(String[] args) {
    String fsafile = "/home/gv/fsa/automata/segments.fsa";

    Segmenter segmenter = new Segmenter(fsafile);

    System.out.println("Loading segmenter FSA file "+fsafile+": "+segmenter.isOk());

    for(int a=0;a<1||a<args.length;a++){

      String query;
      if(a==args.length){
        query = "times square head";
      }
      else {
        query = args[a];
      }
      System.out.println("processing query \""+query+"\"");

      Segments segments = segmenter.segment(query);
      System.out.println("all segments:");
      for(int i=0; i<segments.size();i++){
        System.out.println("  "+i+": \""+segments.sgm(i)+"\","+segments.conn(i));
      }

      Segments best;

      best = segments.segmentation(Segments.SEGMENTATION_WEIGHTED);
      System.out.print("best segments (weighted): ");
      for(int i=0; i<best.size();i++){
        System.out.print("("+best.sgm(i)+")");
      }
      System.out.println();

      best = segments.segmentation(Segments.SEGMENTATION_RIGHTMOST_LONGEST);
      System.out.print("best segments (rightmost_longest):");
      for(int i=0; i<best.size();i++){
        System.out.print("("+best.sgm(i)+")");
      }
      System.out.println();

    }

  }

}

