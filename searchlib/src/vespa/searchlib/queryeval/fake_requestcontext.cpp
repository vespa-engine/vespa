// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_requestcontext.h"
#include <vespa/vespalib/util/testclock.h>

namespace search::queryeval {

FakeRequestContext::FakeRequestContext()
    : FakeRequestContext(nullptr)
{
}

FakeRequestContext::FakeRequestContext(attribute::IAttributeContext * context, vespalib::steady_time softDoom, vespalib::steady_time hardDoom)
    : _clock(std::make_unique<vespalib::TestClock>()),
      _doom(_clock->nowRef(), softDoom, hardDoom, false),
      _attributeContext(context),
      _query_tensor_name(),
      _query_tensor(),
      _create_blueprint_params()
{
}

FakeRequestContext::~FakeRequestContext() = default;

const CreateBlueprintParams&
FakeRequestContext::get_create_blueprint_params() const
{
    return _create_blueprint_params;
}

}
