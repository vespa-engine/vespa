// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributes_initializer_base.h"
#include "attributemanager.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <cassert>
namespace proton {

void
AttributesInitializerBase::considerPadAttribute(search::AttributeVector &attribute,
                                                search::SerialNum currentSerialNum,
                                                uint32_t newDocIdLimit)
{
    /*
     * Sizing requirements for other components to work with the
     * new attributes vectors:
     *
     * Document meta store doesn't need to be resized here ever.
     * It is always present and is the authorative source for
     * allocation of new lids after replay of transaction log has
     * completed. The transaction log should never be pruned
     * beyond the last saved version of the document meta store,
     * and the document meta store will grow as needed during
     * replay unless the transaction log is corrupted.
     *
     * If a newly loaded attribute vector is shorter than the
     * document meta store then it needs to be padded upwards to
     * the same size to ensure that further operations will work.
     * This is not needed if the system has never performed any
     * reconfiguration introducing/removing attribute vectors,
     * i.e.  if the newest saved config is still at serial number
     * 1, since a replay of a non-corrupted transaction log should
     * grow the attribute as needed.
     */
    if (attribute.getStatus().getLastSyncToken() < currentSerialNum) {
        AttributeManager::padAttribute(attribute, newDocIdLimit);
        attribute.commit();
        assert(newDocIdLimit <= attribute.getNumDocs());
    }
}

AttributesInitializerBase::AttributesInitializerBase()
    : IAttributeInitializerRegistry(),
      _initializedAttributes()
{
}

} // namespace proton
