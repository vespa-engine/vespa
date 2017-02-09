// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "document_db_referent.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/iattributemanager.h>

namespace proton {

DocumentDBReferent::DocumentDBReferent()
    : _attrMgr(),
      _dms()
{
}

DocumentDBReferent::~DocumentDBReferent()
{
}

std::shared_ptr<search::AttributeVector>
DocumentDBReferent::getAttribute(vespalib::stringref name)
{
    search::AttributeGuard::UP guard = _attrMgr->getAttribute(name);
    if (guard) {
        return guard->getSP();
    } else {
        return std::shared_ptr<search::AttributeVector>();
    }
}

std::shared_ptr<search::IGidToLidMapperFactory>
DocumentDBReferent::getGidToLidMapperFactory()
{
    return std::shared_ptr<search::IGidToLidMapperFactory>();
}

} // namespace proton
