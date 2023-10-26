// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <iostream>
#include <iomanip>
#include <map>
#include <string>

#include "fsa.h"
#include "permuter.h"
#include "selector.h"
#include "ngram.h"
#include "base64.h"
#include "wordchartokenizer.h"

using namespace fsa;

int main(int argc, char **argv)
{
  const unsigned int MAXQUERY = 10;
  const unsigned int MAXGRAM  = 6;

  Permuter p;
  NGram freq_s,query,gram;
  WordCharTokenizer tokenizer(WordCharTokenizer::PUNCTUATION_WHITESPACEONLY);
  unsigned int freq;
  Selector s;
  std::string qstr;
  unsigned int qlen,glen;

  if(argc!=2){
    std::cerr << "usage: " << argv[0] << " sorted_fsa_file" << std::endl;
    return 1;
  }

  FSA fsa(argv[1]);
  FSA::State state(fsa);
  std::map<std::string,unsigned int> grams;
  std::map<std::string,unsigned int>::iterator grams_it;
  std::string gram_str;

  while(!std::cin.eof()){
    getline(std::cin,qstr);
    query.set(qstr,tokenizer,1,-1);
    qlen = query.length();
    if(2<=qlen && qlen<=MAXQUERY){
      freq_s.set(qstr,tokenizer,0,1);
      freq=atoi(freq_s[0].c_str());
      query.sort();
      qlen = query.uniq();
      unsigned int glen=qlen<MAXGRAM?qlen:MAXGRAM;
      for(unsigned int n=2;n<=glen;n++){
        unsigned int c=Permuter::firstComb(n,qlen);
        while(c>0){
          s.clear();
          s.set(c);
          gram.set(query,s);
          state.startWord(gram[0]);
          for(unsigned int i=1;state.isValid()&&i<gram.size();i++){
            state.deltaWord(gram[i]);
          }
          if(state.isFinal()){
            gram_str = gram.join(" ");
            grams_it=grams.find(gram_str);
            if(grams_it!=grams.end())
              grams[gram_str]=grams_it->second+freq;
            else
              grams[gram_str]=freq;
          }
          c=Permuter::nextComb(c,qlen);
        }
      }
    }
  }

  for(grams_it=grams.begin();grams_it!=grams.end();++grams_it)
    std::cout << grams_it->first << '\t' << grams_it->second << std::endl;

  return 0;
}
