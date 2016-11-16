// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchcontext.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <memory>

namespace proton {
namespace matching {

class MatchContext {
    using IAttributeContext = search::attribute::IAttributeContext;
    IAttributeContext::UP _attrCtx;
    ISearchContext::UP    _searchCtx;

public:
    typedef std::unique_ptr<MatchContext> UP;

    MatchContext(IAttributeContext::UP attrCtx, ISearchContext::UP searchCtx)
        : _attrCtx(std::move(attrCtx)),
          _searchCtx(std::move(searchCtx))
    {
        assert(_attrCtx.get());
        assert(_searchCtx.get());
    }

    IAttributeContext &getAttributeContext() const { return *_attrCtx; }
    ISearchContext &getSearchContext() const { return *_searchCtx; }
    void releaseEnumGuards() { _attrCtx->releaseEnumGuards(); }
};

}  // namespace proton::matching
}  // namespace proton

