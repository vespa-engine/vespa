// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search {

class AttributeVector;
class IGidToLidMapperFactory;

}

namespace proton {

/*
 * Interface class for getting target attributes for imported
 * attributes, and for getting interface for mapping to lids
 * compatible with the target attributes.
 */
class IDocumentDBReferent
{
public:
    virtual ~IDocumentDBReferent() { }
    virtual std::shared_ptr<search::AttributeVector> getAttribute(vespalib::stringref name) = 0;
    virtual std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() = 0;
};

} // namespace proton
