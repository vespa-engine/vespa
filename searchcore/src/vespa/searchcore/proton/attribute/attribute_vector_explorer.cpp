// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_vector_explorer.h"
#include "attribute_executor.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/distance_metric_utils.h>
#include <vespa/searchlib/attribute/i_enum_store.h>
#include <vespa/searchlib/attribute/i_enum_store_dictionary.h>
#include <vespa/searchlib/attribute/ipostinglistattributebase.h>
#include <vespa/searchlib/attribute/multi_value_mapping.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/searchlib/util/state_explorer_utils.h>
#include <vespa/vespalib/data/slime/cursor.h>

using search::AddressSpaceUsage;
using search::AttributeVector;
using search::IEnumStore;
using search::StateExplorerUtils;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::DistanceMetricUtils;
using search::attribute::IAttributeVector;
using search::attribute::IPostingListAttributeBase;
using search::attribute::MultiValueMappingBase;
using search::attribute::Status;
using vespalib::AddressSpace;
using vespalib::MemoryUsage;
using namespace vespalib::slime;

namespace proton {

namespace {

void
convertGenerationToSlime(const AttributeVector &attr, Cursor &object)
{
    object.setLong("oldest_used", attr.get_oldest_used_generation());
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
    for (const auto& entry : usage.get_all()) {
        convertAddressSpaceToSlime(entry.second, object.setObject(entry.first));
    }
}

void
convertMemoryUsageToSlime(const MemoryUsage &usage, Cursor &object)
{
    StateExplorerUtils::memory_usage_to_slime(usage, object);
}

void
convert_enum_store_dictionary_to_slime(const search::IEnumStoreDictionary &dictionary, Cursor &object)
{
    if (dictionary.get_has_btree_dictionary()) {
        convertMemoryUsageToSlime(dictionary.get_btree_memory_usage(), object.setObject("btreeMemoryUsage"));
    }
    if (dictionary.get_has_hash_dictionary()) {
        convertMemoryUsageToSlime(dictionary.get_hash_memory_usage(), object.setObject("hashMemoryUsage"));
    }
}

void
convertEnumStoreToSlime(const IEnumStore &enumStore, Cursor &object)
{
    object.setLong("numUniques", enumStore.get_num_uniques());
    convertMemoryUsageToSlime(enumStore.get_values_memory_usage(), object.setObject("valuesMemoryUsage"));
    convertMemoryUsageToSlime(enumStore.get_dictionary_memory_usage(), object.setObject("dictionaryMemoryUsage"));
    convert_enum_store_dictionary_to_slime(enumStore.get_dictionary(), object.setObject("dictionary"));
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

void
convertPostingBaseToSlime(const IPostingListAttributeBase &postingBase, Cursor &object)
{
    auto& cursor = object.setObject("memory_usage");
    auto memory_usage = postingBase.getMemoryUsage();
    convertMemoryUsageToSlime(memory_usage.total, cursor.setObject("total"));
    convertMemoryUsageToSlime(memory_usage.btrees, cursor.setObject("btrees"));
    convertMemoryUsageToSlime(memory_usage.short_arrays, cursor.setObject("short_arrays"));
    convertMemoryUsageToSlime(memory_usage.bitvectors, cursor.setObject("bitvectors"));
}

vespalib::string
type_to_string(const Config& cfg)
{
    if (cfg.basicType().type() == BasicType::TENSOR) {
        return cfg.tensorType().to_spec();
    }
    if (cfg.collectionType().type() == CollectionType::SINGLE) {
        return cfg.basicType().asString();
    }
    return vespalib::string(cfg.collectionType().asString()) +
           "<" + vespalib::string(cfg.basicType().asString()) + ">";
}

void
convert_config_to_slime(const Config& cfg, bool full, Cursor& object)
{
    object.setString("type", type_to_string(cfg));
    object.setBool("fast_search", cfg.fastSearch());
    object.setBool("filter", cfg.getIsFilter());
    object.setBool("paged", cfg.paged());
    if (full) {
        if (cfg.basicType().type() == BasicType::TENSOR) {
            object.setString("distance_metric", DistanceMetricUtils::to_string(cfg.distance_metric()));
        }
        if (cfg.hnsw_index_params().has_value()) {
            const auto& hnsw_cfg = cfg.hnsw_index_params().value();
            auto& hnsw = object.setObject("hnsw");
            hnsw.setLong("max_links_per_node", hnsw_cfg.max_links_per_node());
            hnsw.setLong("neighbors_to_explore_at_insert", hnsw_cfg.neighbors_to_explore_at_insert());
        }
    }
}

}

AttributeVectorExplorer::AttributeVectorExplorer(std::unique_ptr<AttributeExecutor> executor)
    : _executor(std::move(executor))
{
}

void
AttributeVectorExplorer::get_state(const vespalib::slime::Inserter &inserter, bool full) const
{
    auto& attr = _executor->get_attr();
    _executor->run_sync([this, &attr, &inserter, full] { get_state_helper(attr, inserter, full); });
}

void
AttributeVectorExplorer::get_state_helper(const AttributeVector& attr, const vespalib::slime::Inserter &inserter, bool full) const
{
    const Status &status = attr.getStatus();
    Cursor &object = inserter.insertObject();
    if (full) {
        convert_config_to_slime(attr.getConfig(), full, object.setObject("config"));
        StateExplorerUtils::status_to_slime(status, object.setObject("status"));
        convertGenerationToSlime(attr, object.setObject("generation"));
        convertAddressSpaceUsageToSlime(attr.getAddressSpaceUsage(), object.setObject("addressSpaceUsage"));
        // TODO: Consider making enum store, multivalue mapping, posting list attribute and tensor attribute
        // explorable as children of this state explorer, and let them expose even more detailed information.
        // In this case we must ensure that ExclusiveAttributeReadAccessor::Guard is held also when exploring children.
        const IEnumStore *enumStore = attr.getEnumStoreBase();
        if (enumStore) {
            convertEnumStoreToSlime(*enumStore, object.setObject("enumStore"));
        }
        const MultiValueMappingBase *multiValue = attr.getMultiValueBase();
        if (multiValue) {
            convertMultiValueToSlime(*multiValue, object.setObject("multiValue"));
        }
        const IPostingListAttributeBase *postingBase = attr.getIPostingListAttributeBase();
        if (postingBase) {
            convertPostingBaseToSlime(*postingBase, object.setObject("posting_store"));
        }
        const auto* tensor_attr = attr.asTensorAttribute();
        if (tensor_attr) {
            ObjectInserter tensor_inserter(object, "tensor");
            tensor_attr->get_state(tensor_inserter);
        }
        convertChangeVectorToSlime(attr, object.setObject("changeVector"));
        object.setLong("committedDocIdLimit", attr.getCommittedDocIdLimit());
        object.setLong("createSerialNum", attr.getCreateSerialNum());
    } else {
        convert_config_to_slime(attr.getConfig(), full, object);
        object.setLong("allocated_bytes", status.getAllocated());
    }
}

}
