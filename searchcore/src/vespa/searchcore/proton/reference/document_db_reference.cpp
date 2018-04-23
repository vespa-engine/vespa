// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_reference.h"
#include "gid_to_lid_mapper_factory.h"
#include "gid_to_lid_change_registrator.h"
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>

namespace proton {

DocumentDBReference::DocumentDBReference(std::shared_ptr<IAttributeManager> attrMgr,
                                         std::shared_ptr<const search::IDocumentMetaStoreContext> dmsContext,
                                         std::shared_ptr<IGidToLidChangeHandler> gidToLidChangeHandler)
    : _attrMgr(std::move(attrMgr)),
      _dmsContext(std::move(dmsContext)),
      _gidToLidChangeHandler(std::move(gidToLidChangeHandler))
{
}

DocumentDBReference::~DocumentDBReference()
{
}

std::shared_ptr<search::attribute::ReadableAttributeVector>
DocumentDBReference::getAttribute(vespalib::stringref name)
{
    search::AttributeGuard::UP guard = _attrMgr->getAttribute(name);
    if (guard && guard->valid()) {
        return guard->getSP();
    } else {
        auto importedAttributesRepo = _attrMgr->getImportedAttributes();
        if (importedAttributesRepo != nullptr) {
            return importedAttributesRepo->get(name);
        }
        return std::shared_ptr<search::attribute::ReadableAttributeVector>();
    }
}

std::shared_ptr<const search::IDocumentMetaStoreContext>
DocumentDBReference::getDocumentMetaStoreContext() const
{
    return _dmsContext;
}

std::shared_ptr<search::IGidToLidMapperFactory>
DocumentDBReference::getGidToLidMapperFactory()
{
    return std::make_shared<GidToLidMapperFactory>(_dmsContext);
}

std::unique_ptr<GidToLidChangeRegistrator>
DocumentDBReference::makeGidToLidChangeRegistrator(const vespalib::string &docTypeName)
{
    return std::make_unique<GidToLidChangeRegistrator>(_gidToLidChangeHandler, docTypeName);
}

} // namespace proton
