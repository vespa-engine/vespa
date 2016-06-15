// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/context.h>
#include <vespa/searchlib/attribute/attributeguard.h>

namespace proton
{


class CachedSelect;

class SelectContext : public document::select::Context
{
public:
    uint32_t _docId;

    std::vector<search::AttributeGuard> _guards;
    const CachedSelect &_cachedSelect;

    SelectContext(const CachedSelect &cachedSelect);

    void getAttributeGuards(void);
    void dropAttributeGuards(void);
};

} // namespace proton

