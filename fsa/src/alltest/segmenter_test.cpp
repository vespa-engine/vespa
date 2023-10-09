// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    segmenter_test.cpp
 * @brief   Test for the Segmenter class
 *
 */

#include <iostream>
#include <iomanip>

#include <vespa/fsa/segmenter.h>

using namespace fsa;

int main(int argc, char **argv)
{
  FSA dict(argc>=2? argv[1] : "__testfsa__.__fsa__");

  Segmenter segmenter(dict);
  Segmenter::Segments segments;
  const Segmenter::Segmentation *segmentation;

  std::string text;
  while(!std::cin.eof()){
    getline(std::cin,text);

    if(text.size()>3){

      segmenter.segment(text,segments);

      std::cout << "List of all segments:" << std::endl;
      for(unsigned int i=0; i<segments.size(); i++){
        std::cout << "  "
                  << segments.sgm(i) << ":" << segments.conn(i) << " ["
                  << segments.beg(i) << "," << segments.end(i)-1 << "]"
                  << std::endl;
      }

      segmentation=segments.segmentation(Segmenter::SEGMENTATION_WEIGHTED);

      std::cout << "Weighted segmentation:" << std::endl << "  ";
      for(Segmenter::SegmentationConstIterator it=segmentation->begin();
          it!=segmentation->end();++it){
        std::cout << "(" << segments.sgm(*it) << ")";
      }
      std::cout << std::endl;

      segmentation=segments.segmentation(Segmenter::SEGMENTATION_RIGHTMOST_LONGEST);

      std::cout << "Rightmost-longest segmentation:" << std::endl << "  ";
      for(Segmenter::SegmentationConstIterator it=segmentation->begin();
          it!=segmentation->end();++it){
        std::cout << "(" << segments.sgm(*it) << ")";
      }
      std::cout << std::endl;

      segmentation=segments.segmentation(Segmenter::SEGMENTATION_LEFTMOST_LONGEST);

      std::cout << "Lefttmost-longest segmentation:" << std::endl << "  ";
      for(Segmenter::SegmentationConstIterator it=segmentation->begin();
          it!=segmentation->end();++it){
        std::cout << "(" << segments.sgm(*it) << ")";
      }
      std::cout << std::endl;

    }

  }

  return 0;
}
