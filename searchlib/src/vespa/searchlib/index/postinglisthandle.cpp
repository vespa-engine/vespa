// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglisthandle.h"

namespace search::index {

PostingListHandle::~PostingListHandle()
{
    if (_allocMem != nullptr) {
        free(_allocMem);
    }
}

void
PostingListHandle::drop()
{
    _bitOffsetMem = 0;
    _mem = nullptr;
    if (_allocMem != nullptr) {
        free(_allocMem);
        _allocMem = nullptr;
    }
    _allocSize = 0;
    _read_bytes = 0;
}


}
