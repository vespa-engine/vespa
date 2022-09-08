// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rangequerylocator.h"
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>

using namespace search::queryeval;

namespace proton::matching {

RangeLimitMetaInfo::RangeLimitMetaInfo()
    : _valid(false),
      _estimate(0),
      _low(),
      _high()
{}
RangeLimitMetaInfo::RangeLimitMetaInfo(vespalib::stringref low_, vespalib::stringref high_, size_t estimate_)
    : _valid(true),
      _estimate(estimate_),
      _low(low_),
      _high(high_)
{}
RangeLimitMetaInfo::~RangeLimitMetaInfo() = default;

namespace {

RangeLimitMetaInfo
locateFirst(uint32_t field_id, const Blueprint & blueprint) {
    if (blueprint.isIntermediate()) {
        const auto & intermediate = static_cast<const IntermediateBlueprint &>(blueprint);
        if (intermediate.isAndNot()) {
            return locateFirst(field_id, intermediate.getChild(0));
        } else if (intermediate.isRank()) {
            return locateFirst(field_id, intermediate.getChild(0));
        } else if (intermediate.isAnd()) {
            for (size_t i(0); i < intermediate.childCnt(); i++) {
                RangeLimitMetaInfo childMeta = locateFirst(field_id, intermediate.getChild(i));
                if (childMeta.valid()) {
                    return childMeta;
                }
            }
        }
    } else {
        const Blueprint::State & state = blueprint.getState();
        if (state.isTermLike() && (state.numFields() == 1) && (state.field(0).getFieldId() == field_id)) {
            const LeafBlueprint &leaf = static_cast<const LeafBlueprint &>(blueprint);
            vespalib::string from, too;
            if (leaf.getRange(from, too)) {
                return {from, too, state.estimate().estHits};
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

