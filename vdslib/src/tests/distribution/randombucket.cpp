// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "randombucket.h"
#include <vespa/vdslib/state/random.h>

namespace RandomBucket{

uint64_t _num_users;
uint64_t _locationbits;
bool _scheme = false;
storage::lib::RandomGen rg;

void setUserDocScheme(uint64_t num_users, uint64_t locationbits)
{
    _scheme = true;
    _num_users = num_users;
    _locationbits = locationbits;
}

void setDocScheme()
{
    _scheme = false;
}

uint64_t get()
{
    uint64_t u = rg.nextUint64();
    if(_scheme){ // userdoc
        uint64_t shift = 8 * sizeof(uint64_t) - _locationbits;
        uint64_t lsb = u << shift;
        lsb >>= shift;
        lsb %= _num_users;
        u >>= _locationbits;
        u <<= _locationbits;
        u |= lsb;
    }
    return u;
}

void setSeed(int seed)
{
    if(seed == -1){
        rg  = storage::lib::RandomGen();
    }
    else{
        rg.setSeed(seed);
    }
}

}
