// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_vector_explorer");

#include "attribute_vector_explorer.h"
#include <vespa/searchlib/attribute/enumstorebase.h>
#include <vespa/searchlib/attribute/multi_value_mapping.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/vespalib/data/slime/cursor.h>

using search::attribute::Status;
using search::AddressSpace;
using search::AddressSpaceUsage;
using search::AttributeVector;
using search::EnumStoreBase;
using search::MemoryUsage;
using search::attribute::MultiValueMappingBase;
using namespace vespalib::slime;

namespace proton {

namespace {

void
convertStatusToSlime(const Status &status, Cursor &object)
{
    object.setLong("numDocs", status.getNumDocs());
    object.setLong("numValues", status.getNumValues());
    object.setLong("numUniqueValues", status.getNumUniqueValues());
    object.setLong("lastSerialNum", status.getLastSyncToken());
    object.setLong("updateCount", status.getUpdateCount());
    object.setLong("nonIdempotentUpdateCount", status.getNonIdempotentUpdateCount());
    object.setLong("bitVectors", status.getBitVectors());
    {
        Cursor &memory = object.setObject("memoryUsage");
        memory.setLong("allocatedBytes", status.getAllocated());
        memory.setLong("usedBytes", status.getUsed());
        memory.setLong("deadBytes", status.getDead());
        memory.setLong("onHoldBytes", status.getOnHold());
        memory.setLong("onHoldBytesMax", status.getOnHoldMax());
    }
}

void
convertGenerationToSlime(const AttributeVector &attr, Cursor &object)
{
    object.setLong("firstUsed", attr.getFirstUsedGeneration());
    object.setLong("current", attr.getCurrentGeneration());
}

void
convertAddressSpaceToSlime(const AddressSpace &addressSpace, Cursor &object)
{
    object.setDouble("usage", addressSpace.usage());
    object.setLong("used", addressSpace.used());
    object.setLong("dead", addressSpace.dead());
    object.setLong("limit", addressSpace.limit());
}

void
convertAddressSpaceUsageToSlime(const AddressSpaceUsage &usage, Cursor &object)
{
    convertAddressSpaceToSlime(usage.enumStoreUsage(), object.setObject("enumStore"));
    convertAddressSpaceToSlime(usage.multiValueUsage(), object.setObject("multiValue"));
}

void
convertMemoryUsageToSlime(const MemoryUsage &usage, Cursor &object)
{
    object.setLong("allocated", usage.allocatedBytes());
    object.setLong("used", usage.usedBytes());
    object.setLong("dead", usage.deadBytes());
    object.setLong("onHold", usage.allocatedBytesOnHold());
}

void
convertEnumStoreToSlime(const EnumStoreBase &enumStore, Cursor &object)
{
    object.setLong("lastEnum", enumStore.getLastEnum());
    object.setLong("numUniques", enumStore.getNumUniques());
    convertMemoryUsageToSlime(enumStore.getMemoryUsage(), object.setObject("memoryUsage"));
    convertMemoryUsageToSlime(enumStore.getTreeMemoryUsage(), object.setObject("treeMemoryUsage"));
}

void
convertMultiValueToSlime(const MultiValueMappingBase &multiValue, Cursor &object)
{
    object.setLong("totalValueCnt", multiValue.getTotalValueCnt());
    convertMemoryUsageToSlime(multiValue.getMemoryUsage(), object.setObject("memoryUsage"));
}


void
convertChangeVectorToSlime(const AttributeVector &v, Cursor &object)
{
    MemoryUsage usage = v.getChangeVectorMemoryUsage();
    convertMemoryUsageToSlime(usage, object);
}

}

AttributeVectorExplorer::AttributeVectorExplorer(ExclusiveAttributeReadAccessor::UP attribute)
    : _attribute(std::move(attribute))
{
}

void
AttributeVectorExplorer::get_state(const vespalib::slime::Inserter &inserter, bool full) const
{
    ExclusiveAttributeReadAccessor::Guard::UP readGuard = _attribute->takeGuard();
    const AttributeVector &attr = readGuard->get();
    const Status &status = attr.getStatus();
    Cursor &object = inserter.insertObject();
    if (full) {
        convertStatusToSlime(status, object.setObject("status"));
        convertGenerationToSlime(attr, object.setObject("generation"));
        convertAddressSpaceUsageToSlime(attr.getAddressSpaceUsage(), object.setObject("addressSpaceUsage"));
        const EnumStoreBase *enumStore = attr.getEnumStoreBase();
        if (enumStore) {
            convertEnumStoreToSlime(*enumStore, object.setObject("enumStore"));
        }
        const MultiValueMappingBase *multiValue = attr.getMultiValueBase();
        if (multiValue) {
            convertMultiValueToSlime(*multiValue, object.setObject("multiValue"));
        }
        convertChangeVectorToSlime(attr, object.setObject("changeVector"));
        object.setLong("committedDocIdLimit", attr.getCommittedDocIdLimit());
        object.setLong("createSerialNum", attr.getCreateSerialNum());
    } else {
        object.setLong("numDocs", status.getNumDocs());
        object.setLong("lastSerialNum", status.getLastSyncToken());
        object.setLong("allocatedMemory", status.getAllocated());
        object.setLong("committedDocIdLimit", attr.getCommittedDocIdLimit());
    }
}

} // namespace proton
