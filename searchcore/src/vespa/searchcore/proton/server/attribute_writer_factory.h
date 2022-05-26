// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_attribute_writer_factory.h"
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>

namespace proton {

/**
 * Factory for creating new IAttributeWriter instances during reconfig.
 */
struct AttributeWriterFactory : public IAttributeWriterFactory
{
    AttributeWriterFactory() {}
    IAttributeWriter::SP create(const IAttributeWriter::SP &old,
                                AttributeCollectionSpec && attrSpec) const override
    {
        const AttributeWriter &oldAdapter = dynamic_cast<const AttributeWriter &>(*old.get());
        const proton::IAttributeManager::SP &oldMgr = oldAdapter.getAttributeManager();
        proton::IAttributeManager::SP newMgr = oldMgr->create(std::move(attrSpec));
        return std::make_shared<AttributeWriter>(newMgr);
    }
};

} // namespace proton

