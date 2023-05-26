// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_context.h"
#include <cassert>

namespace proton::matching {

MatchContext::MatchContext(std::unique_ptr<IAttributeContext> attrCtx, std::unique_ptr<ISearchContext> searchCtx) noexcept
    : _attrCtx(std::move(attrCtx)),
      _searchCtx(std::move(searchCtx))
{
    assert(_attrCtx);
    assert(_searchCtx);
}

MatchContext::MatchContext() noexcept = default;
MatchContext::~MatchContext() = default;

}
