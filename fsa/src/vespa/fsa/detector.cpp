// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    detector.cpp
 * @brief   %FSA (%Finite %State %Automaton) based detector (implementation)
 *
 */

#include <list>
#include <algorithm>
#include <cmath>

#include "detector.h"
#include "fsa.h"
#include "ngram.h"


namespace fsa {

// {{{ Detector::detect

void Detector::detect(const NGram &text, Detector::Hits &hits,
                      unsigned int from, int length) const
{
  std::list<FSA::WordCounterState>            detectors;
  std::list<FSA::WordCounterState>::iterator  det_it;
  unsigned int i,to;

  to = text.length();
  if(length!=-1 && from+length<to)
    to=from+length;

  i=from;
  while(i<to){
    detectors.push_back(FSA::WordCounterState(_dictionary));

    det_it=detectors.begin();
    while(det_it!=detectors.end()){
      det_it->deltaWord(text[i]);
      if(det_it->isFinal()){
        hits.add(text, i-det_it->getCounter()+1, det_it->getCounter(), *det_it);
      }

      if(det_it->isValid())
        ++det_it;
      else{
        det_it=detectors.erase(det_it);
      }
    }
    ++i;
  }

  detectors.clear();
}

// }}}
// {{{ Detector::detectWithHash

void Detector::detectWithHash(const NGram &text, Detector::Hits &hits,
                              unsigned int from, int length) const
{
  std::list<FSA::HashedWordCounterState>            detectors;
  std::list<FSA::HashedWordCounterState>::iterator  det_it;
  unsigned int i,to;

  to = text.length();
  if(length!=-1 && from+length<to)
    to=from+length;

  i=from;
  while(i<to){
    detectors.push_back(FSA::HashedWordCounterState(_dictionary));

    det_it=detectors.begin();
    while(det_it!=detectors.end()){
      det_it->deltaWord(text[i]);
      if(det_it->isFinal()){
        hits.add(text, i-det_it->getCounter()+1, det_it->getCounter(), *det_it);
      }

      if(det_it->isValid())
        ++det_it;
      else{
        det_it=detectors.erase(det_it);
      }
    }
    ++i;
  }

  detectors.clear();
}

// }}}

} // namespace fsa
