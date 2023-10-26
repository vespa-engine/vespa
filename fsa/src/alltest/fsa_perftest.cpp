// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdlib.h>
#include <iostream>
#include <iomanip>
#include <string>

#include <vespa/fsa/fsa.h>
#include <vespa/fsa/timestamp.h>

using namespace fsa;

int main(int, char**)
{
  FSA                    f("__testfsa__.__fsa__");
  FSA::State             s(f);
  FSA::HashedState       hs(f);
  FSA::MemoryState       ms(f);
  FSA::HashedMemoryState hms(f);
  FSA::CounterState      cs(f);
  std::string input("cucumber");
  unsigned int count=10000000,i;

  std::cout << "Number of lookups: " << count << std::endl;
  std::cout << "Input string length: " << input.length() << std::endl;
  std::cout << std::endl;

  TimeStamp t;
  double t0,t1;

  t0=t.elapsed();
  for(i=0;i<count;i++){
    s.start();
    s.lookup(input);
  }
  t1=t.elapsed()-t0;
  std::cout << "State:              " << t1*1000 << " ms" << "\t"
            << (unsigned int)(count*input.length()/t1) << " delta/sec" << std::endl;

  t0=t.elapsed();
  for(i=0;i<count;i++){
    hs.start();
    hs.lookup(input);
  }
  t1=t.elapsed()-t0;
  std::cout << "HashedState:        " << t1*1000 << " ms"<< "\t"
            << (unsigned int)(count*input.length()/t1) << " delta/sec" << std::endl;

  t0=t.elapsed();
  for(i=0;i<count;i++){
    ms.start();
    ms.lookup(input);
  }
  t1=t.elapsed()-t0;
  std::cout << "MemoryState:        " << t1*1000 << " ms"<< "\t"
            << (unsigned int)(count*input.length()/t1) << " delta/sec" << std::endl;

  t0=t.elapsed();
  for(i=0;i<count;i++){
    hms.start();
    hms.lookup(input);
  }
  t1=t.elapsed()-t0;
  std::cout << "HashedMemoryState:  " << t1*1000 << " ms"<< "\t"
            << (unsigned int)(count*input.length()/t1) << " delta/sec" << std::endl;

  t0=t.elapsed();
  for(i=0;i<count;i++){
    cs.start();
    cs.lookup(input);
  }
  t1=t.elapsed()-t0;
  std::cout << "CounterState:       " << t1*1000 << " ms"<< "\t"
            << (unsigned int)(count*input.length()/t1) << " delta/sec" << std::endl;

  return 0;
}
