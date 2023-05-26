// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchcontext.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>

namespace proton::matching {

class MatchContext {
    using IAttributeContext = search::attribute::IAttributeContext;
    std::unique_ptr<IAttributeContext> _attrCtx;
    std::unique_ptr<ISearchContext>    _searchCtx;
public:
    using UP = std::unique_ptr<MatchContext>;

    MatchContext() noexcept;
    MatchContext(IAttributeContext::UP attrCtx, std::unique_ptr<ISearchContext> searchCtx) noexcept;
    MatchContext(MatchContext &&) noexcept = default;
    ~MatchContext();

    IAttributeContext &getAttributeContext() const { return *_attrCtx; }
    ISearchContext &getSearchContext() const { return *_searchCtx; }
    void releaseEnumGuards() { _attrCtx->releaseEnumGuards(); }
};

}
