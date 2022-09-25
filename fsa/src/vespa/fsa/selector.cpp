// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    selector.cpp
 * @brief   Selector class.
 */

#include "selector.h"

namespace fsa {

// {{{ Selector::clear()

void Selector::clear()
{
  _selector.clear();
}

// }}}
// {{{ Selector::set()

void Selector::set(unsigned int c)
{
  unsigned int idx=0;
  while(c>0){
    if(idx>=_selector.size()){
      _selector.resize(idx+1,false);
    }
    if(c&1)
      _selector[idx]=true;
    c>>=1;
    idx++;
  }
}

// }}}
// {{{ Selector::select()

void Selector::select(unsigned int i)
{
  if(i>=_selector.size()){
    _selector.resize(i+1,false);
  }
  _selector[i] = true;
}

// }}}
// {{{ Selector::unselect()

void Selector::unselect(unsigned int i)
{
  if(i>=_selector.size()){
    _selector.resize(i+1,false);
  }
  _selector[i] = false;
}

// }}}
// {{{ Selector::operator[]()

bool Selector::operator[](unsigned int i) const
{
  if(i>=_selector.size()){
    return false;
  }
  return _selector[i];
}

// }}}

} // namespace fsa
