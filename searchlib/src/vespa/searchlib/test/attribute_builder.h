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
    using WeightedInt = std::pair<int32_t, int32_t>;
    using WeightedDouble = std::pair<double, int32_t>;
    using WeightedString = std::pair<vespalib::string, int32_t>;
    using IntList = std::initializer_list<int32_t>;
    using DoubleList = std::initializer_list<double>;
    using StringList = std::initializer_list<vespalib::string>;
    using WeightedIntList = std::initializer_list<WeightedInt>;
    using WeightedDoubleList = std::initializer_list<WeightedDouble>;
    using WeightedStringList = std::initializer_list<WeightedString>;

    AttributeBuilder(const vespalib::string& name, const Config& cfg);

    AttributeBuilder& docs(size_t num_docs);

    // Fill functions for integer attributes
    AttributeBuilder& fill(std::initializer_list<int32_t> values);
    AttributeBuilder& fill(std::initializer_list<int64_t> values);
    AttributeBuilder& fill_array(std::initializer_list<IntList> values);
    AttributeBuilder& fill_wset(std::initializer_list<WeightedIntList> values);

    // Fill functions for float attributes
    AttributeBuilder& fill(std::initializer_list<double> values);
    AttributeBuilder& fill_array(std::initializer_list<DoubleList> values);
    AttributeBuilder& fill_wset(std::initializer_list<WeightedDoubleList> values);

    // Fill functions for string attributes
    AttributeBuilder& fill(std::initializer_list<vespalib::string> values);
    AttributeBuilder& fill_array(std::initializer_list<StringList> values);
    AttributeBuilder& fill_wset(std::initializer_list<WeightedStringList> values);

    std::shared_ptr<AttributeVector> get() const { return _attr_ptr; }
};

}
