// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplequerystack.h"
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/objects/nbo.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".search.simplequerystack");

using vespalib::make_string;

namespace search {

SimpleQueryStack::SimpleQueryStack()
    : _numItems(0),
      _stack(nullptr)
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

    _numItems++;
}

void
SimpleQueryStack::AppendBuffer(RawBuf *buf) const
{
    for (SimpleQueryStackItem *item = _stack; item != nullptr; item = item->_next) {
        item->AppendBuffer(buf);
    }
}


uint32_t
SimpleQueryStack::GetSize()
{
    return _numItems;
}

} // namespace search
