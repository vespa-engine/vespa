// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    segmenter.cpp
 * @brief   Query segmenter based on %FSA (%Finite %State %Automaton) (implementation)
 *
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "segmenter.h"


namespace fsa {

Segmenter::Segments::Segments()
  : _text(), _segments(), _map(),
    _segmentation(Segmenter::SEGMENTATION_METHODS,NULL)
{ }

Segmenter::Segments::~Segments()
{
  clear();
}

void
Segmenter::Segments::clear()
{
  _segments.clear();
  _map.init(_text.size());
  initSingles();
  for(unsigned int i=0;i<SEGMENTATION_METHODS;i++){
    delete _segmentation[i];
    _segmentation[i] = nullptr;
  }
}

// {{{ Segmenter::Segments::initSingles

void Segmenter::Segments::initSingles()
{
  for(unsigned int i=0;i<_text.size();i++){
    if(!_map.isValid(i,i+1)){
      _map.set(i,i+1,_segments.size());
      _segments.push_back(Segment(i,i+1,0));
    }
  }
}


// }}}
// {{{ Segmenter::Segments::buildSegmentation

void Segmenter::Segments::buildSegmentation(Segmenter::SegmentationMethod method)
{
  int i,j;
  int n_txt=(int)_text.size(), n_sgm=_segments.size();
  int id,bestid;
  int pos, next=n_txt;
  unsigned int maxsc,conn;
  int bestval,temp=0,bias;
  std::vector<int> nextid(n_sgm,-1);
  std::vector<unsigned int> maxScore(n_sgm,0);

  if(_segmentation[method]==NULL){
    _segmentation[method] = new Segmenter::Segmentation;
  }
  else {
    _segmentation[method]->clear();
  }

  bias=0;
  switch(method){
  case SEGMENTATION_WEIGHTED_BIAS100:
    bias+=50;
    [[fallthrough]];
  case SEGMENTATION_WEIGHTED_BIAS50:
    bias+=30;
    [[fallthrough]];
  case SEGMENTATION_WEIGHTED_BIAS20:
    bias+=10;
    [[fallthrough]];
  case SEGMENTATION_WEIGHTED_BIAS10:
    bias+=10;
    [[fallthrough]];
  case SEGMENTATION_WEIGHTED:
    bestid=-1;
    for(i=n_txt;i>=0;i--){
      bestid=-1;maxsc=0;
      for(j=i+1;j<=n_txt;j++){
        id=_map.get(i,j);
        if(id>=0 && maxScore[id]+1>maxsc) {
          bestid=id;
          maxsc=maxScore[id]+1;
        }
      }
      if(maxsc>0) maxsc--;
      for(j=0;j<i;j++){
        id=_map.get(j,i);
        if(id>=0){
          nextid[id] = bestid;
          conn = _segments[id].conn();
          if(i-j<=1){
            maxScore[id] = maxsc;
          }
          else if(bias>0){
            maxScore[id] = maxsc + ((100+(i-j-2)*bias)*conn)/100;
          }
          else{
            maxScore[id] = maxsc + conn;
          }
        }
      }
    }
    id = bestid;
    while(id!=-1){
      _segmentation[method]->push_back(id);
      id=nextid[id];
    }
    break;
  case SEGMENTATION_LEFTMOST_LONGEST:
  case SEGMENTATION_LEFTMOST_WEIGHTED:
    pos = 0;
    while(pos<n_txt){
      bestid = -1; bestval = -1;
      for(i=pos+1;i<=n_txt;i++){
        id = _map.get(pos,i);
        if(id>=0 &&
           (method==SEGMENTATION_LEFTMOST_LONGEST ||
            (temp=(_segments[id].len()>1)?(int)_segments[id].conn():0)>bestval) ){
          bestid = id;
          bestval = temp;
          next = i;
        }
      }
      _segmentation[method]->push_back(bestid);
      pos=next;
    }
    break;
  case SEGMENTATION_RIGHTMOST_LONGEST:
  case SEGMENTATION_RIGHTMOST_WEIGHTED:
    pos = n_txt;
    while(pos>0){
      bestid = -1; bestval = -1;
      for(i=pos-1;i>=0;i--){
        id = _map.get(i,pos);
        if(id>=0 &&
           (method==SEGMENTATION_RIGHTMOST_LONGEST ||
            (temp=(_segments[id].len()>1)?(int)_segments[id].conn():0)>bestval) ){
          bestid = id;
          bestval = temp;
          next = i;
        }
      }
      _segmentation[method]->push_front(bestid);
      pos=next;
    }
    break;
  case SEGMENTATION_LONGEST_WEIGHTED:
  case SEGMENTATION_LONGEST_LEFTMOST:
  case SEGMENTATION_LONGEST_RIGHTMOST:
  case SEGMENTATION_WEIGHTED_LONGEST:
  case SEGMENTATION_WEIGHTED_LEFTMOST:
  case SEGMENTATION_WEIGHTED_RIGHTMOST:
    buildSegmentationRecursive(method,*_segmentation[method],0,n_txt);
    break;
  default:
    break;
  }
}

// }}}
// {{{ Segmenter::Segments::buildSegmentationRecursive

void Segmenter::Segments::buildSegmentationRecursive(Segmenter::SegmentationMethod method,
                                                     Segmenter::Segmentation& segmentation,
                                                     unsigned int beg,
                                                     unsigned int end)
{
  int bestid, bestval1, bestval2, temp;
  int i;

  // locate the best segment according to method
  bestid=-1;bestval1=-1;bestval2=-1;
  for(i=0;i<(int)_segments.size();i++){
    if(beg<=_segments[i].beg() && end>=_segments[i].end()){
      switch(method){
      case SEGMENTATION_LONGEST_WEIGHTED:
        if((int)_segments[i].len()>bestval1 ||
           ((int)_segments[i].len()==bestval1 && (int)_segments[i].conn()>bestval2) ){
          bestid=i;
          bestval1=_segments[i].len();
          bestval2=_segments[i].conn();
        }
        break;
      case SEGMENTATION_LONGEST_LEFTMOST:
        if((int)_segments[i].len()>bestval1 ||
           ((int)_segments[i].len()==bestval1 && (int)_segments[i].beg()<bestval2) ){
          bestid=i;
          bestval1=_segments[i].len();
          bestval2=_segments[i].beg();
        }
        break;
      case SEGMENTATION_LONGEST_RIGHTMOST:
        if((int)_segments[i].len()>bestval1 ||
           ((int)_segments[i].len()==bestval1 && (int)_segments[i].end()>bestval2) ){
          bestid=i;
          bestval1=_segments[i].len();
          bestval2=_segments[i].end();
        }
        break;
      case SEGMENTATION_WEIGHTED_LONGEST:
        temp = (_segments[i].len()>1)?(int)_segments[i].conn():0;
        if(temp>bestval1 ||
           (temp==bestval1 &&
            (int)_segments[i].len()>bestval2) ){
          bestid=i;
          bestval1=temp;
          bestval2=_segments[i].len();
        }
        break;
      case SEGMENTATION_WEIGHTED_LEFTMOST:
        temp = (_segments[i].len()>1)?(int)_segments[i].conn():0;
        if(temp>bestval1 ||
           (temp==bestval1 &&
            (int)_segments[i].beg()<bestval2) ){
          bestid=i;
          bestval1=temp;
          bestval2=_segments[i].beg();
        }
        break;
      case SEGMENTATION_WEIGHTED_RIGHTMOST:
        temp = (int)_segments[i].len()>1?(int)_segments[i].conn():0;
        if(temp>bestval1 ||
           (temp==bestval1 &&
            (int)_segments[i].end()>bestval2) ){
          bestid=i;
          bestval1=temp;
          bestval2=_segments[i].end();
        }
        break;
      default: // dummy defult pick first possible
        if(bestid<0){
          bestid=i;
        }
        break;
      }
    }
  }
  if(bestid<0) {
    return; // this should never happen, as all one-word segments are created
  }

  // check left side
  if(beg<_segments[bestid].beg()){
    buildSegmentationRecursive(method,segmentation,beg,_segments[bestid].beg());
  }

  // add segment
  segmentation.push_back(bestid);

  // check right side
  if(end>_segments[bestid].end()){
    buildSegmentationRecursive(method,segmentation,_segments[bestid].end(),end);
  }
}

// }}}

// {{{ Segmenter::segment

void Segmenter::segment(Segmenter::Segments &segments) const
{
  segments.clear();
  _detector.detect(segments.getText(),segments);
}

void Segmenter::segment(const NGram &text, Segmenter::Segments &segments) const
{
  segments.setText(text);
  _detector.detect(segments.getText(),segments);
}

void Segmenter::segment(const std::string &text, Segmenter::Segments &segments) const
{

  segments.setText(text);
  _detector.detect(segments.getText(),segments);
}

void Segmenter::segment(const char *text, Segmenter::Segments &segments) const
{
  segments.setText(text);
  _detector.detect(segments.getText(),segments);
}

// }}}

} // namespace fsa
