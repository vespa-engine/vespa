// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.common.selectcontext");
#include "selectcontext.h"
#include "cachedselect.h"
#include <vespa/searchlib/attribute/attributevector.h>

namespace proton
{

using document::select::Value;
using document::select::Context;
using search::AttributeGuard;
using search::AttributeVector;


SelectContext::SelectContext(const CachedSelect &cachedSelect)
    : Context(),
      _docId(0u),
      _guards(cachedSelect._attributes.size()),
      _cachedSelect(cachedSelect)
{
}


void
SelectContext::getAttributeGuards(void)
{
    _guards.resize(_cachedSelect._attributes.size());
    std::vector<AttributeVector::SP>::const_iterator
        j(_cachedSelect._attributes.begin());
    for (std::vector<AttributeGuard>::iterator i(_guards.begin()),
             ie(_guards.end()); i != ie; ++i, ++j) {
        *i = AttributeGuard(*j);
    }
}


void
SelectContext::dropAttributeGuards(void)
{
    _guards.clear();
}


} // namespace proton

