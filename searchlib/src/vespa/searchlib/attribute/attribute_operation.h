// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "vespa/searchcommon/attribute/i_attribute_functor.h"
#include <vespa/searchlib/common/rankedhit.h>
#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/vespalib/util/array.h>
#include <vector>

namespace search { class BitVector; }

namespace search::attribute {

class AttributeOperation : public IAttributeFunctor {
public:
    using Hit = std::pair<uint32_t, double>;
    using FullResult = std::pair<std::unique_ptr<search::BitVector>, vespalib::Array<search::RankedHit>>;

    template <typename Hits>
    static std::unique_ptr<AttributeOperation>
    create(search::attribute::BasicType type, const vespalib::string & operation, Hits docIds);
};

}
