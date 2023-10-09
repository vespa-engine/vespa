// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    vectorizertest.cpp
 * @brief   Test for the vectorizer class
 *
 */

#include <iostream>
#include <iomanip>

#include <vespa/fsa/fsa.h>
#include <vespa/fsa/detector.h>

using namespace fsa;

class MyHits : public Detector::Hits{
public:
  MyHits() {};
  ~MyHits() {};

  void add(const NGram &text,
           unsigned int from, int length,
           const FSA::State &) override
  {
    std::cout << "detected: [" << from << "," << from+length-1 << "], '"
              << text.join(" ",from,length) << "'\n";
  }
};

int main(int argc, char **argv)
{
  FSA dict(argc>=2? argv[1] : "__testfsa__.__fsa__");

  Detector d(dict);
  MyHits   h;

  std::string text;
  while(!std::cin.eof()){
    getline(std::cin,text);

    d.detect(text,h);
  }

  return 0;
}
