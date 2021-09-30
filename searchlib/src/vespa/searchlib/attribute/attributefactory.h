// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/config.h>
#include <memory>

namespace search {

class AttributeVector;

/**
 * Factory for creating attribute vector instances.
 **/
class AttributeFactory {
private:
    using stringref = vespalib::stringref;
    using Config = attribute::Config;
    using AttributeSP = std::shared_ptr<AttributeVector>;
    static AttributeSP createArrayStd(stringref name, const Config & cfg);
    static AttributeSP createArrayFastSearch(stringref name, const Config & cfg);
    static AttributeSP createSetStd(stringref name, const Config & cfg);
    static AttributeSP createSetFastSearch(stringref name, const Config & cfg);
    static AttributeSP createSingleStd(stringref name, const Config & cfg);
    static AttributeSP createSingleFastSearch(stringref name, const Config & cfg);
public:
    /**
     * Create an attribute vector with the given name based on the given config.
     **/
    static AttributeSP createAttribute(stringref name, const Config & cfg);
};

}

