// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/searchlib/attribute/attributemanager.h>

#include <optional>

namespace search::docsummary::test {

/**
 * Class used to build attributes and populate a manager for testing.
 */
class MockAttributeManager {
    AttributeManager _mgr;

    template <typename AttributeType>
    std::shared_ptr<AttributeType> create_and_register_attribute(const std::string&       name,
                                                                 const attribute::Config& cfg);

    template <typename AttributeType, typename ValueType>
    void build_attribute(const std::string& name, attribute::BasicType type, attribute::CollectionType col_type,
                         const std::vector<std::vector<ValueType>>& values, std::optional<bool> uncased);

public:
    MockAttributeManager();
    ~MockAttributeManager();
    AttributeManager& mgr() { return _mgr; }

    void build_string_attribute(const std::string& name, const std::vector<std::vector<std::string>>& values,
                                attribute::CollectionType col_type = attribute::CollectionType::ARRAY,
                                std::optional<bool>       uncased = std::nullopt);
    void build_float_attribute(const std::string& name, const std::vector<std::vector<double>>& values,
                               attribute::CollectionType col_type = attribute::CollectionType::ARRAY);
    void build_int_attribute(const std::string& name, attribute::BasicType type,
                             const std::vector<std::vector<int64_t>>& values,
                             attribute::CollectionType                col_type = attribute::CollectionType::ARRAY);
    void build_raw_attribute(const std::string& name, const std::vector<std::vector<std::vector<char>>>& values);
    void build_bool_attribute(const std::string& name, const std::vector<std::vector<int8_t>>& values);
    void build_tensor_attribute(const std::string& name, const std::string& tensor_spec, bool quantized,
                                const std::vector<std::unique_ptr<vespalib::eval::Value>>& tensors);
};

} // namespace search::docsummary::test
