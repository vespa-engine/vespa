// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iattributeadapterfactory.h"
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>

namespace proton {

struct AttributeAdapterFactory : public IAttributeAdapterFactory
{
    AttributeAdapterFactory()
    { }
    virtual IAttributeWriter::SP create(const IAttributeWriter::SP &old,
                                        const AttributeCollectionSpec &attrSpec) const
    {
        const AttributeWriter &oldAdapter = dynamic_cast<const AttributeWriter &>(*old.get());
        const proton::IAttributeManager::SP &oldMgr = oldAdapter.getAttributeManager();
        proton::IAttributeManager::SP newMgr = oldMgr->create(attrSpec);
        return IAttributeWriter::SP(new AttributeWriter(newMgr));
    }
};

} // namespace proton

