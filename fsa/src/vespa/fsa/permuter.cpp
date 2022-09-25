// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    permuter.cpp
 * @brief   Permuter class.
 */

#include "permuter.h"

namespace fsa {

// {{{ Permuter::MAX_UNIT_LENGTH

const unsigned int Permuter::MAX_UNIT_LENGTH;

// }}}

// {{{ Permuter::initRec()

void Permuter::initRec(const std::string &input, std::string tail)
{
  std::string temp;
  int i;

  if(input.length()==0){
    _permtab.push_back(tail);
    _permmap[tail] = _permtab.size()-1;
  }
  else{
    for(i=input.length()-1;i>=0;i--){
      temp = input;
      temp.erase(i,1);
      initRec(temp,input.substr(i,1)+tail);
    }
  }
}

// }}}
// {{{ Permuter::Permuter()

Permuter::Permuter() : _permtab(), _permmap(), _size(0), _seed(MAX_UNIT_LENGTH,0)
{
  unsigned int i;

  _size = 1;
  for(i=1;i<=MAX_UNIT_LENGTH;i++){
    _seed[i-1]=i;
    _size*=i;
  }
  _permtab.reserve(_size);

  initRec(_seed,std::string());
}

// }}}
// {{{ Permuter::~Permuter()

Permuter::~Permuter()
{
}

// }}}
// {{{ Permuter::getPermId()

int Permuter::getPermId(const std::string &perm) const
{
  std::string t(perm);

  if(t.length()>MAX_UNIT_LENGTH)
    return -1;

  if(t.length()<MAX_UNIT_LENGTH)
    t+=_seed.substr(t.length(),MAX_UNIT_LENGTH-t.length());

  const PermMapConstIterator pi = _permmap.find(t);
  if(pi==_permmap.end())
    return -1;
  else
    return pi->second;
}

// }}}
// {{{ Permuter::firstComb()

unsigned int Permuter::firstComb(unsigned int n, unsigned int m)
{
  if(n==0 || n>31 || m==0 || m>31 || n>m)
    return 0;

  return (1<<n)-1;
}

// }}}
// {{{ Permuter::nextComb()

unsigned int Permuter::nextComb(unsigned int c, unsigned int m)

{
  if(c==0 || m==0 || m>31)
    return 0;

  unsigned int x=c;
  unsigned int limit=1<<m;
  unsigned int mask, mask1,mask2;

  if(x&1){
    mask=2;
    while(x&mask) mask<<=1;
    x^=(mask+(mask>>1));
  }
  else{
    mask=2;
    while(!(x&mask)) mask<<=1;
    mask1=mask2=0;
    while(x&mask){
      mask1<<=1;mask1++;
      mask2+=mask;
      mask<<=1;
    }
    mask1>>=1;
    x^=(mask+(mask1^mask2));
  }

  return (x<limit)?x:0;
}

// }}}

} // namespace fsa
