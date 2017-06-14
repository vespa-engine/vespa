// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedoperation.h"

namespace proton {

FeedOperation::FeedOperation(Type type)
    : _type(type),
      _serialNum(0)
{
}

} // namespace proton
