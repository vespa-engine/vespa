// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <iostream>
#include <iomanip>

#include "permuter.h"
#include "selector.h"
#include "ngram.h"
#include "base64.h"

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
  NGram query,gram;
  Selector s;
  std::string qstr;
  unsigned int qlen,glen;
  bool verbose=true;
  unsigned int i;
  double total,ctotal;
  int stats[MAXQUERY+1];

  for(i=0;i<=MAXQUERY;i++)
    stats[i]=0;
  while(!std::cin.eof()){
    getline(std::cin,qstr);
    query.set(qstr,1);
    qlen = query.length();
    if(2<=qlen && qlen<=MAXQUERY){
      stats[qlen]++;
      std::cout << "QUERY: " << query << std::endl;
      query.sort();
      qlen = query.uniq();
      unsigned int glen=qlen<MAXGRAM?qlen:MAXGRAM;
      for(unsigned int n=2;n<=glen;n++){
        unsigned int c=Permuter::firstComb(n,qlen);
        while(c>0){
          s.clear();
          s.set(c);
          gram.set(query,s);
          std::cout << "   " << gram << std::endl;
          c=Permuter::nextComb(c,qlen);
        }
      }
    }
    else{
      if(qlen<2)
        stats[0]++;
      else
        stats[1]++;
    }
  }



  if(verbose){
    total=0.0;ctotal=0.0;
    for(i=0;i<=MAXQUERY;i++)
      total+=stats[i];
    std::cerr << std::fixed << std::setprecision(4) << std::endl;
    std::cerr << "Statistics:" << std::endl;
    std::cerr << std::endl;
    std::cerr << "  Empty or single term:  " <<
      std::setw(12) << stats[0] << "   " <<
      std::setw(7) << double(stats[0])*100.0/total << "%" << std::endl;
    std::cerr << "  Too long:              " <<
      std::setw(12) << stats[1] << "   " <<
      std::setw(7) << double(stats[1])*100.0/total << "%" << std::endl;
    for(i=2;i<=MAXQUERY;i++){
      std::cerr << "  Length " << std::setw(2) << i << " (grams " << std::setw(3) <<
        gram_count(i<MAXGRAM?i:MAXGRAM,i) << "): " <<
        std::setw(12) << stats[i] << "   " <<
        std::setw(7) << double(stats[i])*100.0/total << "%" << std::endl;
      ctotal+=stats[i]*gram_count(i<MAXGRAM?i:MAXGRAM,i);
    }
    std::cerr << "  Total:                 " <<
      std::setw(12) << std::setprecision(0) << total << std::endl;
    std::cerr << std::endl;
    std::cerr << "Average number of grams per query: " <<
      std::setprecision(2) << ctotal/total << std::endl;
    std::cerr << std::endl;
  }

  return 0;
}
