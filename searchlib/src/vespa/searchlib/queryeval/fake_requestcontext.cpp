// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/fake_requestcontext.h>

namespace search {
namespace queryeval {

FakeRequestContext::FakeRequestContext(attribute::IAttributeContext * context, fastos::TimeStamp doom_in) :
    _clock(),
    _doom(_clock, doom_in),
    _attributeContext(context)
{ }

}
}
