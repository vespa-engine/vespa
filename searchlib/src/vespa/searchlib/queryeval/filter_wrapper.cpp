// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filter_wrapper.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search::queryeval {

FilterWrapper::~FilterWrapper() = default;

void
FilterWrapper::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "wrapped_as_filter", _wrapped_search);
}

} // namespace
