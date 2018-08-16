// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/attribute/i_attribute_functor.h>
#include <vespa/searchcommon/attribute/basictype.h>
#include <vector>

namespace search { class ResultSet; }

namespace proton::matching {

class AttributeOperation : public IAttributeFunctor {
public:
    using Hit = std::pair<uint32_t, double>;
    static std::unique_ptr<AttributeOperation>
    create(search::attribute::BasicType type, const vespalib::string & operation, std::vector<uint32_t> docIds);
    static std::unique_ptr<AttributeOperation>
    create(search::attribute::BasicType type, const vespalib::string & operation, std::vector<Hit> hits);
    static std::unique_ptr<AttributeOperation>
    create(search::attribute::BasicType type, const vespalib::string & operation, std::unique_ptr<search::ResultSet> result);
};

}
