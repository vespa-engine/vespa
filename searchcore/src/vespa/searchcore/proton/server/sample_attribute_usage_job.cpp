// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sample_attribute_usage_job.h"
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/attribute_config_inspector.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_sampler_context.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_sampler_functor.h>
#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/searchcorespi/index/indexsearchablevisitor.h>
#include <vespa/searchlib/attribute/address_space_usage.h>
#include <vespa/searchlib/queryeval/isourceselector.h>

using search::AddressSpaceUsage;
using search::queryeval::ISourceSelector;
using searchcorespi::IIndexManager;

namespace proton {

namespace {

class CountIndexesVisitor : public searchcorespi::IndexSearchableVisitor {
    uint32_t _indexes;
public:
    CountIndexesVisitor();
    ~CountIndexesVisitor() override;
    uint32_t get_indexes() const noexcept { return _indexes; }
    void visit(const searchcorespi::index::IDiskIndex&) override { ++_indexes; }
    void visit(const searchcorespi::index::IMemoryIndex&) override { ++_indexes; }
};

CountIndexesVisitor::CountIndexesVisitor()
    : searchcorespi::IndexSearchableVisitor(),
      _indexes(0)
{
}

CountIndexesVisitor::~CountIndexesVisitor() = default;

/*
 * Source selector is limited to SOURCE_LIMIT indexes. Report index shards usage as attribute
 * address space usage.
 */
void merge_index_shards(AttributeUsageSamplerContext& context, IIndexManager& index_manager)
{
    (void) context;
    auto searchable = index_manager.getSearchable();
    CountIndexesVisitor count_indexes_visitor;
    if (searchable) {
        searchable->accept(count_indexes_visitor);
    }
    /*
     * Skip reporting address space usage for index shards while number is below 10.
     * Always reporting address space usage here would break feed block system tests that sets very low limit
     * for attribute address space usage.
     */
    if (count_indexes_visitor.get_indexes() >= 10) {
        AddressSpaceUsage index_shards;
        index_shards.set("", vespalib::AddressSpace(count_indexes_visitor.get_indexes(), 0,
                                                    ISourceSelector::SOURCE_LIMIT));
        context.merge(index_shards, "index_shards", "");
    }
}

}

SampleAttributeUsageJob::
SampleAttributeUsageJob(IAttributeManagerSP readyAttributeManager,
                        IAttributeManagerSP notReadyAttributeManager,
                        AttributeUsageFilter &attributeUsageFilter,
                        const std::string &docTypeName,
                        vespalib::duration interval,
                        std::shared_ptr<IIndexManager> index_manager)
    : IMaintenanceJob("sample_attribute_usage." + docTypeName, vespalib::duration::zero(), interval),
      _readyAttributeManager(std::move(readyAttributeManager)),
      _notReadyAttributeManager(std::move(notReadyAttributeManager)),
      _attributeUsageFilter(attributeUsageFilter),
      _document_type(docTypeName),
      _index_manager(std::move(index_manager))
{
}

SampleAttributeUsageJob::~SampleAttributeUsageJob() = default;

bool
SampleAttributeUsageJob::run()
{
    auto context = std::make_shared<AttributeUsageSamplerContext>(_document_type, _attributeUsageFilter);
    merge_index_shards(*context, *_index_manager);
    _readyAttributeManager->asyncForEachAttribute(std::make_shared<AttributeUsageSamplerFunctor>(context, "ready"));
    _notReadyAttributeManager->asyncForEachAttribute(std::make_shared<AttributeUsageSamplerFunctor>(context, "notready"));
    return true;
}

} // namespace proton
