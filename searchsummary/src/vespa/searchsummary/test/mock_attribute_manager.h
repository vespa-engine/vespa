// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/searchlib/attribute/attributemanager.h>

namespace search::docsummary::test {

/**
 * Class used to build attributes and populate a manager for testing.
 */
class MockAttributeManager {
private:
    AttributeManager _mgr;

    template <typename AttributeType, typename ValueType>
    void build_attribute(const vespalib::string& name, search::attribute::BasicType type,
                         search::attribute::CollectionType col_type,
                         const std::vector<std::vector<ValueType>>& values);

public:
    MockAttributeManager();
    ~MockAttributeManager();
    AttributeManager& mgr() { return _mgr; }

    void build_string_attribute(const vespalib::string& name,
                                const std::vector<std::vector<vespalib::string>>& values,
                                search::attribute::CollectionType col_type = search::attribute::CollectionType::ARRAY);
    void build_float_attribute(const vespalib::string& name,
                               const std::vector<std::vector<double>>& values,
                               search::attribute::CollectionType col_type = search::attribute::CollectionType::ARRAY);
    void build_int_attribute(const vespalib::string& name, search::attribute::BasicType type,
                             const std::vector<std::vector<int64_t>>& values,
                             search::attribute::CollectionType col_type = search::attribute::CollectionType::ARRAY);
    void build_raw_attribute(const vespalib::string& name,
                             const std::vector<std::vector<std::vector<char>>>& values);
};

}
