// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <iostream>

#include <vespa/fsa/permuter.h>
#include <vespa/fsa/selector.h>
#include <vespa/fsa/ngram.h>
#include <vespa/fsa/base64.h>
#include <vespa/fsa/wordchartokenizer.h>

using namespace fsa;

int main(int, char **)
{
  Permuter p;

  NGram q1("a b c d e f"), q2(q1,p,10), q3(q2,p,13);

  Selector s;

  std::string s1("this is a test"), s2;

  Base64::encode(s1,s2);
  std::cout << "'" << s1 << "'" << std::endl;
  std::cout << "'" << s2 << "'" << std::endl;
  Base64::decode(s2,s1);
  std::cout << "'" << s1 << "'" << std::endl;


  std::cout << q1 << std::endl;
  std::cout << q2 << std::endl;
  std::cout << q3 << std::endl;

  q2.sort();
  std::cout << q2 << std::endl;
  q2.reverse();
  std::cout << q2 << std::endl;

  std::cout << std::hex;
  for(unsigned int n=1;n<=6;n++){
    unsigned int c=Permuter::firstComb(n,6);
    while(c>0){
      s.clear();
      s.set(c);
      q2.set(q1,s);
      std::cout << c << ": " << q2 << std::endl;
      c=Permuter::nextComb(c,6);
    }
  }
  std::cout << std::dec;

  WordCharTokenizer tokenizer(WordCharTokenizer::PUNCTUATION_SMART,"PUNCT");

  NGram q4("test, wordchar tokenizer. does it work?",tokenizer);

  std::cout << q4.join(" -|- ") << std::endl;

}
