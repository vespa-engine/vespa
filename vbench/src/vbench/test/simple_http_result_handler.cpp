// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_http_result_handler.h"

namespace vbench {

using WritableMemory = vespalib::WritableMemory;

SimpleHttpResultHandler::SimpleHttpResultHandler()
    : _headers(),
      _content(),
      _failures()
{
}

SimpleHttpResultHandler::~SimpleHttpResultHandler() {}

void
SimpleHttpResultHandler::handleHeader(const string &name, const string &value)
{
    _headers.push_back(std::make_pair(name, value));
}

void
SimpleHttpResultHandler::handleContent(const Memory &data)
{
    WritableMemory wm = _content.reserve(data.size);
    memcpy(wm.data, data.data, data.size);
    _content.commit(data.size);
}

void
SimpleHttpResultHandler::handleFailure(const string &reason)
{
    _failures.push_back(reason);
}

} // namespace vbench
