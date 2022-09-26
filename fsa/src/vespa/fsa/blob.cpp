// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    blob.cpp
 * @brief   Implementation of Blob class methods
 *
 */

#include "blob.h"


namespace fsa {

// {{{ Blob::operator<()

bool Blob::operator<(const Blob& b) const
{
  if(_size<b._size) return true;
  if(_size>b._size) return false;
  if(_size==0) return false;
  if(memcmp(_data,b._data,_size)<0) return true;
  return false;
}

// }}}
// {{{ Blob::operator>()

bool Blob::operator>(const Blob& b) const
{
  if(_size>b._size) return true;
  if(_size<b._size) return false;
  if(_size==0) return false;
  if(memcmp(_data,b._data,_size)>0) return true;
  return false;
}

// }}}
// {{{ Blob::operator==()

bool Blob::operator==(const Blob& b) const
{
  if(_size==b._size && (_size==0 || memcmp(_data,b._data,_size)==0)) return true;
  return false;
}

// }}}

} // namespace fsa
