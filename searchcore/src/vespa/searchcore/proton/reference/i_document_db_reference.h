// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::attribute { class ReadableAttributeVector; }

namespace search {

class IGidToLidMapperFactory;
class IDocumentMetaStoreContext;

}

namespace proton {

class GidToLidChangeRegistrator;

/*
 * Interface class for getting target attributes for imported
 * attributes, and for getting interface for mapping to lids
 * compatible with the target attributes.
 */
class IDocumentDBReference
{
public:
    using SP = std::shared_ptr<IDocumentDBReference>;
    virtual ~IDocumentDBReference() { }
    virtual std::shared_ptr<search::attribute::ReadableAttributeVector> getAttribute(vespalib::stringref name) = 0;
    virtual std::shared_ptr<const search::IDocumentMetaStoreContext> getDocumentMetaStore() const = 0;
    virtual std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() = 0;
    virtual std::unique_ptr<GidToLidChangeRegistrator> makeGidToLidChangeRegistrator(const vespalib::string &docTypeName) = 0;
};

} // namespace proton
