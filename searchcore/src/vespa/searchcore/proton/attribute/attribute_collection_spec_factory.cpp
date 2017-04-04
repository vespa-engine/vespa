// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_collection_spec_factory");
#include "attribute_collection_spec_factory.h"
#include <vespa/searchlib/attribute/configconverter.h>
#include "attribute_specs.h"

using search::attribute::ConfigConverter;
using search::GrowStrategy;

namespace proton {

AttributeCollectionSpecFactory::AttributeCollectionSpecFactory(
        const search::GrowStrategy &growStrategy,
        size_t growNumDocs,
        bool fastAccessOnly)
    : _growStrategy(growStrategy),
      _growNumDocs(growNumDocs),
      _fastAccessOnly(fastAccessOnly)
{
}

AttributeCollectionSpec::UP
AttributeCollectionSpecFactory::create(const AttributeSpecs &attrSpecs,
                                       uint32_t docIdLimit,
                                       search::SerialNum serialNum) const
{
    AttributeCollectionSpec::AttributeList attrs;
    // Amortize memory spike cost over N docs
    const size_t skew = _growNumDocs/(attrSpecs.getSpecs().size()+1);
    GrowStrategy grow = _growStrategy;
    grow.setDocsInitialCapacity(std::max(grow.getDocsInitialCapacity(),
                                         docIdLimit));
    for (const auto &attr : attrSpecs.getSpecs()) {
        auto cfg = attr.getConfig();
        if (_fastAccessOnly && !cfg.fastAccess()) {
            continue;
        }
        grow.setDocsGrowDelta(grow.getDocsGrowDelta() + skew);
        cfg.setGrowStrategy(grow);
        attrs.push_back(AttributeSpec(attr.getName(), cfg));
    }
    return AttributeCollectionSpec::UP(new AttributeCollectionSpec(attrs,
                                                                   docIdLimit,
                                                                   serialNum));
}

} // namespace proton
