// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryenvironment.h"
#include <vespa/searchlib/index/i_field_length_inspector.h>

using search::attribute::IAttributeContext;
using search::fef::IIndexEnvironment;
using search::fef::Location;
using search::fef::Properties;

namespace proton::matching {

QueryEnvironment::QueryEnvironment(const IIndexEnvironment &indexEnv,
                                   const IAttributeContext &attrContext,
                                   const Properties &properties,
                                   const search::index::IFieldLengthInspector &field_length_inspector)
    : _indexEnv(indexEnv),
      _attrContext(attrContext),
      _properties(properties),
      _locations(1),
      _terms(),
      _field_length_inspector(field_length_inspector)
{
}

const search::fef::Properties &
QueryEnvironment::getProperties() const
{
    return _properties;
}

uint32_t
QueryEnvironment::getNumTerms() const
{
    return _terms.size();
}

const search::fef::ITermData *
QueryEnvironment::getTerm(uint32_t idx) const
{
    if (idx >= _terms.size()) {
        return nullptr;
    }
    return _terms[idx];
}

// const search::fef::Location &
// QueryEnvironment::getLocation() const
// {
//     return *_locations[0];
// }

const IAttributeContext &
QueryEnvironment::getAttributeContext() const
{
    return _attrContext;
}

double
QueryEnvironment::get_average_field_length(const vespalib::string &field_name) const
{
    return _field_length_inspector.get_field_length_info(field_name).get_average_field_length();
}

const search::fef::IIndexEnvironment &
QueryEnvironment::getIndexEnvironment() const
{
    return _indexEnv;
}

QueryEnvironment::~QueryEnvironment() = default;

}
