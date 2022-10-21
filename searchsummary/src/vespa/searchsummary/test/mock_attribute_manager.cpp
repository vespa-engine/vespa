// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_manager.h"
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchcommon/attribute/config.h>
#include <cassert>

using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;

namespace search::docsummary::test {

template <typename AttributeType, typename ValueType>
void
MockAttributeManager::build_attribute(const vespalib::string& name, BasicType type,
                                      CollectionType col_type,
                                      const std::vector<std::vector<ValueType>>& values)
{
    Config cfg(type, col_type);
    auto attr_base = AttributeFactory::createAttribute(name, cfg);
    assert(attr_base);
    auto attr = std::dynamic_pointer_cast<AttributeType>(attr_base);
    assert(attr);
    attr->addReservedDoc();
    for (const auto& docValues : values) {
        uint32_t docId = 0;
        attr->addDoc(docId);
        for (const auto& value : docValues) {
            attr->append(docId, value, 1);
        }
        attr->commit();
    }
    _mgr.add(attr);
}

MockAttributeManager::MockAttributeManager()
    : _mgr()
{
}

MockAttributeManager::~MockAttributeManager() = default;

void
MockAttributeManager::build_string_attribute(const vespalib::string& name,
                                             const std::vector<std::vector<vespalib::string>>& values,
                                             CollectionType col_type)
{
    build_attribute<StringAttribute, vespalib::string>(name, BasicType::Type::STRING, col_type, values);
}

void
MockAttributeManager::build_float_attribute(const vespalib::string& name,
                                            const std::vector<std::vector<double>>& values,
                                            CollectionType col_type)
{
    build_attribute<FloatingPointAttribute, double>(name, BasicType::Type::DOUBLE, col_type, values);
}

void
MockAttributeManager::build_int_attribute(const vespalib::string& name, BasicType type,
                                          const std::vector<std::vector<int64_t>>& values,
                                          CollectionType col_type)
{
    build_attribute<IntegerAttribute, int64_t>(name, type, col_type, values);
}

}
