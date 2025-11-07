// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rangequerylocator.h"
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>

using namespace search::queryeval;

namespace proton::matching {

RangeLimitMetaInfo::RangeLimitMetaInfo()
    : _valid(false),
      _estimate(0),
      _range_spec()
{}
RangeLimitMetaInfo::RangeLimitMetaInfo(const search::NumericRangeSpec& range_spec_, size_t estimate_)
    : _valid(true),
      _estimate(estimate_),
      _range_spec(range_spec_)
{}
RangeLimitMetaInfo::~RangeLimitMetaInfo() = default;

namespace {

RangeLimitMetaInfo
locateFirst(uint32_t field_id, const Blueprint & blueprint) {
    const auto * intermediate = blueprint.asIntermediate();
    if (intermediate) {
        if (intermediate->isAndNot() || intermediate->isRank()) {
            return locateFirst(field_id, intermediate->getChild(0));
        } else if (intermediate->isAnd()) {
            for (size_t i(0); i < intermediate->childCnt(); i++) {
                RangeLimitMetaInfo childMeta = locateFirst(field_id, intermediate->getChild(i));
                if (childMeta.valid()) {
                    return childMeta;
                }
            }
        }
    } else {
        const Blueprint::State & state = blueprint.getState();
        if (state.isTermLike() && (state.numFields() == 1) && (state.field(0).getFieldId() == field_id)) {
            const LeafBlueprint * leaf = blueprint.asLeaf();
            search::NumericRangeSpec range_spec;
            if (leaf->getRange(range_spec)) {
                return {range_spec, state.estimate().estHits};
            }
        }
    }
    return {};
}

}

RangeLimitMetaInfo
LocateRangeItemFromQuery::locate() const {
    return locateFirst(_field_id, _blueprint);
}

}

