// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <iostream>
#include <iomanip>
#include <map>
#include <string>

#include "fsa.h"
#include "permuter.h"
#include "ngram.h"
#include "base64.h"

using namespace fsa;

int main(int argc, char **argv)
{
  const unsigned int MAXQUERY = 10;
  const unsigned int MAXGRAM  = 6;

  Permuter p;
  NGram freq_s,gram,sorted_gram;
  unsigned int freq;
  Selector s(10);
  std::string gstr;

  if(argc!=3){
    std::cerr << "usage: " << argv[0] << " plain_count_fsa_file sorted_count_fsa_file" << std::endl;
    return 1;
  }

  FSA plain_fsa(argv[1]);
  FSA sorted_fsa(argv[2]);
  FSA::State state1(plain_fsa),state2(sorted_fsa);

  while(!std::cin.eof()){
    getline(std::cin,gstr);
    gram.set(gstr);
    if(gram.length()>1){
      sorted_gram.set(gram);
      sorted_gram.sort();
      sorted_gram.uniq();
      state1.startWord(gram[0]);
      for(unsigned int i=1;state1.isValid()&&i<gram.length();i++){
        state1.deltaWord(gram[i]);
      }
      state2.startWord(sorted_gram[0]);
      for(unsigned int i=1;state2.isValid()&&i<sorted_gram.length();i++){
        state2.deltaWord(sorted_gram[i]);
      }
      if(state1.isFinal() && state2.isFinal()){
        unsigned int c1,c2;
        c1=*((unsigned int*)state1.data());
        c2=*((unsigned int*)state2.data());
        std::cout << gram << "\t" << c1 << "," << c2 << "," << (double)c1/(double)c2 << std::endl;
      }
    }
  }

  return 0;
}
