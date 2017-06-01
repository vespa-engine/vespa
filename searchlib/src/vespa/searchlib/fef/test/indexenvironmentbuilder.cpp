// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexenvironmentbuilder.h"

namespace search {
namespace fef {
namespace test {

IndexEnvironmentBuilder::IndexEnvironmentBuilder(IndexEnvironment &env) :
    _env(env)
{
    // empty
}

IndexEnvironmentBuilder &
IndexEnvironmentBuilder::addField(const FieldType &type,
                                  const FieldInfo::CollectionType &coll,
                                  const vespalib::string &name)
{
    uint32_t idx = _env.getFields().size();
    FieldInfo field(type, coll, name, idx);
    _env.getFields().push_back(field);
    return *this;
}

} // namespace test
} // namespace fef
} // namespace search
