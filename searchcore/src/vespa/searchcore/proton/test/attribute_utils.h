// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace search { class AttributeVector; }
namespace search::attribute { class Config; }

namespace proton::test {

struct AttributeUtils
{
    using Config = search::attribute::Config;
    static void fillAttribute(search::AttributeVector &attr, uint32_t numDocs, int64_t value, uint64_t lastSyncToken);
    static void fillAttribute(search::AttributeVector &attr, uint32_t from, uint32_t to, int64_t value, uint64_t lastSyncToken);
    static const Config & getInt32Config();
    static const Config & getInt32ArrayConfig();
    static const Config & getStringConfig();
    static const Config & getPredicateConfig();
    static const Config & getTensorConfig();
};

}

