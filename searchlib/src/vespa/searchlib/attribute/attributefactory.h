// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search::attribute { class Config; }
namespace search {

class AttributeVector;

/**
 * Factory for creating attribute vector instances.
 **/
class AttributeFactory {
private:
    using string_view = std::string_view;
    using Config = attribute::Config;
    using AttributeSP = std::shared_ptr<AttributeVector>;
    static AttributeSP createArrayStd(string_view name, const Config & cfg);
    static AttributeSP createArrayFastSearch(string_view name, const Config & cfg);
    static AttributeSP createSetStd(string_view name, const Config & cfg);
    static AttributeSP createSetFastSearch(string_view name, const Config & cfg);
    static AttributeSP createSingleStd(string_view name, const Config & cfg);
    static AttributeSP createSingleFastSearch(string_view name, const Config & cfg);
public:
    /**
     * Create an attribute vector with the given name based on the given config.
     **/
    static AttributeSP createAttribute(string_view name, const Config & cfg);
};

}

