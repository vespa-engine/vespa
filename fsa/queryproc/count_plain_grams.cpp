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

unsigned int gram_count(unsigned int mg, unsigned int q)
{
  unsigned int i,j,c1,c2,ct=0;

  for(i=2;i<=mg;i++){
    c1=1;c2=1;
    for(j=(i>q-i)?(i+1):(q-i+1);j<=q;j++){
      c1*=j;
      c2*=(q-j)+1;
    }
    ct+=c1/c2;
  }
  return ct;
}

int main(int argc, char **argv)
{
  const unsigned int MAXQUERY = 10;
  const unsigned int MAXGRAM  = 6;

  Permuter p;
  NGram freq_s,query;
  WordCharTokenizer tokenizer(WordCharTokenizer::PUNCTUATION_WHITESPACEONLY);
  unsigned int freq;
  Selector s;
  std::string qstr;
  unsigned int qlen,glen;

  if(argc!=2){
    std::cerr << "usage: " << argv[0] << " fsa_file" << std::endl;
    return 1;
  }

  FSA fsa(argv[1]);
  FSA::State state(fsa);
  std::map<std::string,unsigned int> grams,gq;
  std::map<std::string,unsigned int>::iterator grams_it,gq_it;
  std::string gram_str;

  while(!std::cin.eof()){
    getline(std::cin,qstr);
    query.set(qstr,tokenizer,1,-1);
    qlen = query.length();
    if(2<=qlen && qlen<=MAXQUERY){
      freq_s.set(qstr,tokenizer,0,1);
      freq=atoi(freq_s[0].c_str());
      gq.clear();
      for(unsigned int i=0;i<qlen-1;i++){
        for(unsigned int j=2;j<=MAXGRAM&&i+j<=qlen;j++){
          state.startWord(query[i]);
          for(unsigned int k=1;state.isValid()&&k<j;k++){
            state.deltaWord(query[i+k]);
          }
          if(state.isFinal()){
            gram_str = query.join(" ",i,j);
            gq[gram_str]=freq;
          }
        }
      }
      for(gq_it=gq.begin();gq_it!=gq.end();++gq_it){
        grams_it=grams.find(gq_it->first);
        if(grams_it!=grams.end())
          grams[gq_it->first]=grams_it->second+gq_it->second;
        else
          grams[gq_it->first]=gq_it->second;
      }
    }
  }

  for(grams_it=grams.begin();grams_it!=grams.end();++grams_it)
    std::cout << grams_it->first << '\t' << grams_it->second << std::endl;

  return 0;
}
