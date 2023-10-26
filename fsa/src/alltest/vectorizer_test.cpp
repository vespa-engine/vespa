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

#include <vespa/fsa/vectorizer.h>

using namespace fsa;

int main(int argc, char **argv)
{
  FSA dict(argc>=2? argv[1] : "__testfsa__.__fsa__");

  Vectorizer v(dict);
  Vectorizer::TermVector tv;

  std::string text;
  NGram tokenized_text;

  while(!std::cin.eof()){
    getline(std::cin,text);

    tokenized_text.set(text);
    v.vectorize(tokenized_text,tv);

    for(unsigned int i=0; i<tv.size(); i++){
      std::cout << tv[i].term() << ", " << tv[i].weight() << std::endl;
    }
  }

  return 0;
}
