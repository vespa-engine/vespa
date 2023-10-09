// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <iostream>
#include <iomanip>

#include "permuter.h"
#include "ngram.h"
#include "base64.h"
#include "wordchartokenizer.h"

using namespace fsa;

int main(int argc, char **argv)
{

  NGram query;
  WordCharTokenizer tokenizer(WordCharTokenizer::PUNCTUATION_WHITESPACEONLY);
  std::string qstr;

  while(!std::cin.eof()){
    getline(std::cin,qstr);
    query.set(qstr,tokenizer,0,-1);
    query.sort();
    query.uniq();
    std::cout << query << std::endl;
  }


  return 0;
}
