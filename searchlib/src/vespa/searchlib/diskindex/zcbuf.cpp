// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcbuf.h"
#include <cstdlib>
#include <cstring>

namespace search::diskindex {

ZcBuf::ZcBuf()
    : _buffer()
{
}

ZcBuf::~ZcBuf() = default;

}
