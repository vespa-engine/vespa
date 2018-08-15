// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryenvironment.h"

using search::attribute::IAttributeContext;
using search::fef::IIndexEnvironment;
using search::fef::Location;
using search::fef::Properties;

namespace proton::matching {

QueryEnvironment::QueryEnvironment(const IIndexEnvironment &indexEnv,
                                   const IAttributeContext &attrContext,
                                   const Properties &properties)
    : _indexEnv(indexEnv),
      _attrContext(attrContext),
      _properties(properties),
      _locations(1),
      _terms()
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
        return 0;
    }
    return _terms[idx];
}

const search::fef::Location &
QueryEnvironment::getLocation() const
{
    return *_locations[0];
}

const IAttributeContext &
QueryEnvironment::getAttributeContext() const
{
    return _attrContext;
}

const search::fef::IIndexEnvironment &
QueryEnvironment::getIndexEnvironment() const
{
    return _indexEnv;
}

QueryEnvironment::~QueryEnvironment() = default;

}
