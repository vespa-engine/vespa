// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/20
 * @version $Id$
 * @file    checksum.h
 * @brief   Definition of Checksum class
 *
 */

#pragma once

#include <inttypes.h>
#include <string.h>


namespace fsa {

// {{{ class Checksum

/**
 * @class Checksum
 * @brief Simple checksum class
 */
class Checksum {
public:

  /**
   * @brief Comupte 32-bit checksum value of an arbitrary buffer.
   *
   * @param buffer Pointer to the buffer.
   * @param size Size of the buffer.
   * @return 32-bit checksum value.
   */
  static uint32_t compute(void *buffer, uint32_t size)
  {
    uint32_t checksum=0,rest=0,i=0;
    char *buf = (char *)buffer;

    for(i=0;i<(size>>2);i++){
      uint32_t tmp;
      memcpy(&tmp, buf, sizeof(uint32_t));
      buf += sizeof(uint32_t);
      checksum += tmp;
    }
    //@@@@@@BUG! should be if((size&3)>0) but that will break checksumming; postpone to next major .fsa format change
    if(size&(3>0)){ // was if(size&3>0) but that generates a warning in GCC4
      memcpy(&rest,(uint8_t*)buffer+4*i,size&3);
      checksum+=rest;
    }
    return checksum;
  }
};

// }}}

} // namespace fsa

