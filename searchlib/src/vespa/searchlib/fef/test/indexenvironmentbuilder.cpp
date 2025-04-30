// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexenvironmentbuilder.h"
#include <vespa/searchcommon/common/datatype.h>

namespace search::fef::test {

IndexEnvironmentBuilder::IndexEnvironmentBuilder(IndexEnvironment &env) :
    _env(env)
{
}

IndexEnvironmentBuilder &
IndexEnvironmentBuilder::addField(const FieldType &type,
                                  const FieldInfo::CollectionType &coll,
                                  const std::string &name)
{
    return addField(type, coll, FieldInfo::DataType::DOUBLE, name);
}

IndexEnvironmentBuilder &
IndexEnvironmentBuilder::addField(const FieldType &type,
                                  const FieldInfo::CollectionType &coll,
                                  const FieldInfo::DataType &dataType,
                                  const std::string &name)
{
    return addField(type, coll, dataType, std::nullopt, name);
}

IndexEnvironmentBuilder &
IndexEnvironmentBuilder::addField(const FieldType &type,
                                  const FieldInfo::CollectionType &coll,
                                  const FieldInfo::DataType &dataType,
                                  std::optional<ElementGap> element_gap,
                                  const std::string &name)
{
    uint32_t idx = _env.getFields().size();
    FieldInfo field(type, coll, name, idx);
    if (element_gap.has_value()) {
        field.set_element_gap(element_gap.value());
    }
    field.set_data_type(dataType);
    _env.getFields().push_back(field);
    return *this;
}

}
