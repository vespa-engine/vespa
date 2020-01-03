// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_requestcontext.h"

namespace search::queryeval {

FakeRequestContext::FakeRequestContext(attribute::IAttributeContext * context, vespalib::steady_time softDoom, vespalib::steady_time hardDoom)
    : _clock(),
      _doom(_clock, softDoom, hardDoom, false),
      _attributeContext(context),
      _query_tensor_name(),
      _query_tensor()
{
}

FakeRequestContext::~FakeRequestContext() = default;

}
