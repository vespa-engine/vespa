// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <utility>
#include <vector>

namespace search { class AttributeVector; }
namespace search::attribute { class Config; }

namespace search::attribute::test {

/**
 * Helper class used to build and fill AttributeVector instances in unit tests.
 */
class AttributeBuilder {
private:
    std::shared_ptr<AttributeVector> _attr_ptr;
    AttributeVector& _attr;

public:
    AttributeBuilder(const vespalib::string& name, const Config& cfg);
    AttributeBuilder(AttributeVector& attr);

    // Fill functions for integer attributes
    AttributeBuilder& fill(const std::vector<int64_t>& values);
    AttributeBuilder& fill_array(const std::vector<std::vector<int64_t>>& values);
    AttributeBuilder& fill_wset(const std::vector<std::vector<std::pair<int64_t, int32_t>>>& values);

    // Fill functions for string attributes
    AttributeBuilder& fill(const std::vector<vespalib::string>& values);
    AttributeBuilder& fill_array(const std::vector<std::vector<vespalib::string>>& values);
    AttributeBuilder& fill_wset(const std::vector<std::vector<std::pair<vespalib::string, int32_t>>>& values);

    std::shared_ptr<AttributeVector> get() const { return _attr_ptr; }
};

}
