// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    base64.cpp
 * @brief   Implementation of Base64 class methods
 *
 */

#include <iostream>
#include <string>

#include "base64.h"


namespace fsa {

// {{{ Base64::_table, Base64::_padding

const unsigned char Base64::_table[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
const unsigned char Base64::_padding = '=';

// }}}

// {{{ Base64::b2n()

inline int Base64::b2n(int b)
{
  if (b>='A' && b<='Z')
    return b-'A';
  else if (b>='a' && b<='z')
    return b-'a'+26;
  else if (b>='0' && b<='9')
    return b-'0'+52;
  else if (b=='+')
    return 62;
  else if (b=='/')
    return 63;
  else
    return -1;
}

// }}}
// {{{ Base64::n2b()

inline int Base64::n2b(int n)
{
  if(n<0||n>63)
    return -1;
  return _table[n];
}

// }}}

// {{{ Base64::decode()

int Base64::decode(const std::string &src, std::string &dest)
{
  if(src.length()&0x03){  // source length should be 4*n
    dest.resize(0);
    return -1;
  }

  dest.resize(3*(src.length()>>2),'\0');

  std::string::size_type i, index = 0;
  int s1,s2,s3,s4;

  for (i =0; i<src.length(); i+=4) {
    s1 = b2n(src[i]);
    s2 = b2n(src[i+1]);
    s3 = b2n(src[i+2]);
    s4 = b2n(src[i+3]);


    if(s1<0||s2<0){ // the first two symbols should not be '='
      dest.resize(index);
      return -1;
    }

    if(s3<0){ // only one output symbol
      dest[index++] = s1<<2 | s2>>4;
      if(s4>=0){    // if s3 is '=', s4 should be '=' too
        dest.resize(index);
        return -1;
      }
    }
    else if(s4<0){ // two symbols
      dest[index++] = s1<<2 | s2>>4;
      dest[index++] = (s2&0x0f)<<4 | s3>>2;
    }
    else { // all three present
      dest[index++] = s1<<2 | s2>>4;
      dest[index++] = (s2&0x0f)<<4 | s3>>2;
      dest[index++] = (s3&0x03)<<6 | s4;
    }
  }

  dest.resize(index);
  return index;
}

// }}}
// {{{ Base64::encode()

int Base64::encode(const std::string &src, std::string &dest)
{
  dest.resize(4*((src.length()+2)/3),'\0');

  std::string::size_type i, index = 0;

  for(i=0;i+2<src.length();i+=3) {
    dest[index++] = n2b(src[i]>>2);
    dest[index++] = n2b((src[i]&0x03)<<4 | src[i+1]>>4);
    dest[index++] = n2b((src[i+1]&0x0f)<<2 | src[i+2]>>6);
    dest[index++] = n2b(src[i+2]&0x3f);
  }

  if (i<src.length()) { // handle padding
    dest[index++] = n2b(src[i]>>2);
    if (i<src.length()-1) { // 2 bytes left
      dest[index++] = n2b((src[i]&0x03)<<4 | src[i+1]>>4);
      dest[index++] = n2b((src[i+1]&0x0f)<<2);
      dest[index++] = _padding;
    } else {                // 1 byte left
      dest[index++] = n2b((src[i+1]&0x03)<<4);
      dest[index++] = _padding;
      dest[index++] = _padding;
    }
  }

  return index;
}

// }}}

} // namespace fsa
