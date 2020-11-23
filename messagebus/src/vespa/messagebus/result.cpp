// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "result.h"
#include "message.h"

namespace mbus {

Result::Result()
    : _accepted(true),
      _error(),
      _msg()
{ }

Result::Result(const Error &err, std::unique_ptr<Message> msg)
    : _accepted(false),
      _error(err),
      _msg(std::move(msg))
{ }

Result::~Result() = default;

} // namespace mbus
