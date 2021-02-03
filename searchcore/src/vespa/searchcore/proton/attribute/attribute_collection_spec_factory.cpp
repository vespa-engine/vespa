// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_collection_spec_factory.h"
#include <vespa/searchlib/attribute/configconverter.h>

using search::attribute::ConfigConverter;
using search::GrowStrategy;

namespace proton {

AttributeCollectionSpecFactory::AttributeCollectionSpecFactory(
        const AllocStrategy &alloc_strategy,
        bool fastAccessOnly)
    : _alloc_strategy(alloc_strategy),
      _fastAccessOnly(fastAccessOnly)
{
}

AttributeCollectionSpec::UP
AttributeCollectionSpecFactory::create(const AttributesConfig &attrCfg,
                                       uint32_t docIdLimit,
                                       search::SerialNum serialNum) const
{
    AttributeCollectionSpec::AttributeList attrs;
    // Amortize memory spike cost over N docs
    const size_t skew = _alloc_strategy.get_amortize_count()/(attrCfg.attribute.size()+1);
    GrowStrategy grow = _alloc_strategy.get_grow_strategy();
    grow.setDocsInitialCapacity(std::max(grow.getDocsInitialCapacity(),docIdLimit));
    for (const auto &attr : attrCfg.attribute) {
        search::attribute::Config cfg = ConfigConverter::convert(attr);
        if (_fastAccessOnly && !cfg.fastAccess()) {
            continue;
        }
        grow.setDocsGrowDelta(grow.getDocsGrowDelta() + skew);
        cfg.setGrowStrategy(grow);
        cfg.setCompactionStrategy(_alloc_strategy.get_compaction_strategy());
        attrs.push_back(AttributeSpec(attr.name, cfg));
    }
    return std::make_unique<AttributeCollectionSpec>(attrs, docIdLimit, serialNum);
}

} // namespace proton
