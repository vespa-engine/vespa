// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplequerystack.h"
#include <vespa/vespalib/util/compress.h>

#include <vespa/log/log.h>
LOG_SETUP(".search.simplequerystack");

namespace search {

SimpleQueryStack::SimpleQueryStack()
    : _stack(nullptr)
{
}

SimpleQueryStack::~SimpleQueryStack()
{
    delete _stack;
}

void
SimpleQueryStack::Push(SimpleQueryStackItem *item)
{
    item->_next = _stack;
    _stack = item;
}

void
SimpleQueryStack::AppendBuffer(RawBuf *buf) const
{
    for (SimpleQueryStackItem *item = _stack; item != nullptr; item = item->_next) {
        item->AppendBuffer(buf);
    }
}

} // namespace search
