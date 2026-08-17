// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "label_wrapper_blueprint.h"

#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/require.h>

namespace search::queryeval {

namespace {

/**
 * Matches exactly as its child does, and injects a constant raw score for every
 * unpacked document.
 */
class LabelWrapperSearch : public SearchIterator {
    SearchIterator::UP       _child;
    fef::TermFieldMatchData& _tfmd;
    double                   _score;

public:
    LabelWrapperSearch(SearchIterator::UP child, fef::TermFieldMatchData& tfmd, double score) noexcept
        : _child(std::move(child)), _tfmd(tfmd), _score(score) {}
    ~LabelWrapperSearch() override;

    void initRange(uint32_t begin_id, uint32_t end_id) override {
        SearchIterator::initRange(begin_id, end_id);
        _child->initRange(begin_id, end_id);
        setDocId(_child->getDocId());
    }
    void doSeek(uint32_t docid) override {
        _child->doSeek(docid);
        setDocId(_child->getDocId());
    }
    void doUnpack(uint32_t docid) override {
        _child->doUnpack(docid);
        _tfmd.setRawScore(docid, _score);
    }
    Trinary is_strict() const override { return _child->is_strict(); }
    void visitMembers(vespalib::ObjectVisitor& visitor) const override {
        visit(visitor, "child", *_child);
        visit(visitor, "score", _score);
    }
};

LabelWrapperSearch::~LabelWrapperSearch() = default;

} // namespace

LabelWrapperBlueprint::LabelWrapperBlueprint(fef::TermFieldHandle handle, double score) noexcept
    : IntermediateBlueprint(), _handle(handle), _score(score) {
}

LabelWrapperBlueprint::~LabelWrapperBlueprint() = default;

FlowStats LabelWrapperBlueprint::calculate_flow_stats(uint32_t) const {
    if (childCnt() == 0) {
        return {0.0, 0.0, 0.0};
    }
    double est = getChild(0).estimate();
    auto   self = self_flow_stats(est, childCnt());
    return {est, getChild(0).cost() + self.cost, getChild(0).strict_cost() + self.strict_cost};
}

Blueprint::HitEstimate LabelWrapperBlueprint::combine(const std::vector<HitEstimate>& data) const {
    if (data.empty()) {
        return {};
    }
    return data[0];
}

FieldSpecBaseList LabelWrapperBlueprint::exposeFields() const {
    // The wrapper does not search a field; its handle is resolved directly from
    // the match data in createIntermediateSearch.
    return {};
}

void LabelWrapperBlueprint::sort(Children&, InFlow) const {
}

SearchIterator::UP LabelWrapperBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                                                   fef::MatchData&       md) const {
    REQUIRE_EQ(sub_searches.size(), 1u);
    auto* tfmd = md.resolveTermField(_handle);
    REQUIRE(tfmd != nullptr);
    return std::make_unique<LabelWrapperSearch>(std::move(sub_searches[0]), *tfmd, _score);
}

SearchIterator::UP LabelWrapperBlueprint::createFilterSearchImpl(FilterConstraint constraint) const {
    return create_first_child_filter(get_children(), constraint);
}

uint8_t LabelWrapperBlueprint::calculate_cost_tier() const {
    return (childCnt() > 0) ? get_children()[0]->getState().cost_tier() : State::COST_TIER_NORMAL;
}

AnyFlow LabelWrapperBlueprint::my_flow(InFlow in_flow) const {
    return AnyFlow::create<RankFlow>(in_flow);
}

} // namespace search::queryeval
