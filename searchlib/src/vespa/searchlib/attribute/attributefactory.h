// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>

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
    static AttributeSP createArrayStd(std::string name, const Config & cfg);
    static AttributeSP createArrayFastSearch(std::string name, const Config & cfg);
    static AttributeSP createSetStd(std::string name, const Config & cfg);
    static AttributeSP createSetFastSearch(std::string name, const Config & cfg);
    static AttributeSP createSingleStd(std::string name, const Config & cfg);
    static AttributeSP createSingleFastSearch(std::string name, const Config & cfg);
public:
    /**
     * Create an attribute vector with the given name based on the given config.
     **/
    static AttributeSP createAttribute(string_view name, const Config & cfg);
};

}

