// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intermediate_blueprints.h"
#include "andnotsearch.h"
#include "andsearch.h"
#include "orsearch.h"
#include "nearsearch.h"
#include "ranksearch.h"
#include "sourceblendersearch.h"
#include "termwise_blueprint_helper.h"
#include "isourceselector.h"
#include "field_spec.hpp"
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>

namespace search::queryeval {

//-----------------------------------------------------------------------------

namespace {

template <typename CombineType>
size_t lookup_create_source(std::vector<std::unique_ptr<CombineType> > &sources, uint32_t child_source, uint32_t docid_limit) {
    for (size_t i = 0; i < sources.size(); ++i) {
        if (sources[i]->getSourceId() == child_source) {
            return i;
        }
    }
    sources.push_back(std::unique_ptr<CombineType>(new CombineType()));
    sources.back()->setSourceId(child_source);
    sources.back()->setDocIdLimit(docid_limit);
    return (sources.size() - 1);
}

template <typename CombineType>
void optimize_source_blenders(IntermediateBlueprint &self, size_t begin_idx) {
    std::vector<size_t> source_blenders;
    const SourceBlenderBlueprint * reference = nullptr;
    for (size_t i = begin_idx; i < self.childCnt(); ++i) {
        const SourceBlenderBlueprint * sbChild = self.getChild(i).asSourceBlender();
        if (sbChild) {
            if (reference == nullptr || reference->isCompatibleWith(*sbChild)) {
                source_blenders.push_back(i);
                reference = sbChild;
            }
        }
    }
    if (source_blenders.size() > 1) { // maybe 2
        Blueprint::UP blender_up;
        std::vector<std::unique_ptr<CombineType> > sources;
        while (!source_blenders.empty()) {
            blender_up = self.removeChild(source_blenders.back());
            source_blenders.pop_back();
            SourceBlenderBlueprint * blender = blender_up->asSourceBlender();
            while (blender->childCnt() > 0) {
                Blueprint::UP child_up = blender->removeLastChild();
                size_t source_idx = lookup_create_source(sources, child_up->getSourceId(), self.get_docid_limit());
                sources[source_idx]->addChild(std::move(child_up));
            }
        }
        SourceBlenderBlueprint * top = blender_up->asSourceBlender();
        while (!sources.empty()) {
            top->addChild(std::move(sources.back()));
            sources.pop_back();
        }
        blender_up = Blueprint::optimize(std::move(blender_up));
        self.addChild(std::move(blender_up));
    }
}

void
need_normal_features_for_children(const IntermediateBlueprint &blueprint, fef::MatchData &md)
{
    for (size_t i = 0; i < blueprint.childCnt(); ++i) {
        const Blueprint::State &cs = blueprint.getChild(i).getState();
        for (size_t j = 0; j < cs.numFields(); ++j) {
            auto *tfmd = cs.field(j).resolve(md);
            if (tfmd != nullptr) {
                tfmd->setNeedNormalFeatures(true);
            }
        }
    }
}

double rel_est_first_child(const Blueprint::Children &children) {
    return children.empty() ? 0.0 : children[0]->getState().relative_estimate();
}

double rel_est_and(const Blueprint::Children &children) {
    double flow = 1.0;
    for (const Blueprint::UP &child: children) {
        flow *= child->getState().relative_estimate();
    }
    return children.empty() ? 0.0 : flow;
}

double rel_est_or(const Blueprint::Children &children) {
    double flow = 1.0;
    for (const Blueprint::UP &child: children) {
        flow *= (1.0 - child->getState().relative_estimate());
    }
    return (1.0 - flow);
}

} // namespace search::queryeval::<unnamed>

//-----------------------------------------------------------------------------

double
AndNotBlueprint::calculate_relative_estimate() const {
    return rel_est_first_child(get_children());
}

Blueprint::HitEstimate
AndNotBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    if (data.empty()) {
        return {};
    }
    return data[0];
}

FieldSpecBaseList
AndNotBlueprint::exposeFields() const
{
    return {};
}

void
AndNotBlueprint::optimize_self(OptimizePass pass)
{
    if (childCnt() == 0) {
        return;
    }
    if (pass == OptimizePass::FIRST) {
        if (auto *child = getChild(0).asAndNot()) {
            while (child->childCnt() > 1) {
                addChild(child->removeLastChild());
            }
            insertChild(1, child->removeChild(0));
            removeChild(0);
        }
        if (auto *child = getChild(0).asAnd()) {
            for (size_t i = 0; i < child->childCnt(); ++i) {
                if (auto *grand_child = child->getChild(i).asAndNot()) {
                    while (grand_child->childCnt() > 1) {
                        addChild(grand_child->removeLastChild());
                    }
                    child->addChild(grand_child->removeChild(0));
                    child->removeChild(i--);
                }
            }
        }
        for (size_t i = 1; i < childCnt(); ++i) {
            if (getChild(i).getState().estimate().empty) {
                removeChild(i--);
            }
        }
    }
    if (pass == OptimizePass::LAST) {
        optimize_source_blenders<OrBlueprint>(*this, 1);
    }
}

Blueprint::UP
AndNotBlueprint::get_replacement()
{
    if (childCnt() == 1) {
        return removeChild(0);
    }
    return {};
}

void
AndNotBlueprint::sort(Children &children) const
{
    if (children.size() > 2) {
        std::sort(children.begin() + 1, children.end(), TieredGreaterEstimate());
    }
}

bool
AndNotBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
AndNotBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                          bool strict, search::fef::MatchData &md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    if (should_do_termwise_eval(unpack_info, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, std::move(sub_searches), unpack_info);
        bool termwise_strict = (strict && inheritStrict(helper.first_termwise));
        auto termwise_search = (helper.first_termwise == 0)
                               ? AndNotSearch::create(helper.get_termwise_children(), termwise_strict)
                               : OrSearch::create(helper.get_termwise_children(), termwise_strict);
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        }
        return AndNotSearch::create(std::move(rearranged), strict);
    }
    return AndNotSearch::create(std::move(sub_searches), strict);
}

SearchIterator::UP
AndNotBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_andnot_filter(get_children(), strict, constraint);
}

//-----------------------------------------------------------------------------

double
AndBlueprint::calculate_relative_estimate() const {
    return rel_est_and(get_children());
}

Blueprint::HitEstimate
AndBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return min(data);
}

FieldSpecBaseList
AndBlueprint::exposeFields() const
{
    return {};
}

void
AndBlueprint::optimize_self(OptimizePass pass)
{
    if (pass == OptimizePass::FIRST) {
        for (size_t i = 0; i < childCnt(); ++i) {
            if (auto *child = getChild(i).asAnd()) {
                while (child->childCnt() > 0) {
                    addChild(child->removeLastChild());
                }
                removeChild(i--);
            }
        }
    }
    if (pass == OptimizePass::LAST) {
        optimize_source_blenders<AndBlueprint>(*this, 0);
    }
}

Blueprint::UP
AndBlueprint::get_replacement()
{
    if (childCnt() == 1) {
        return removeChild(0);
    }
    return {};
}

void
AndBlueprint::sort(Children &children) const
{
    std::sort(children.begin(), children.end(), TieredLessEstimate());
}

bool
AndBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
AndBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                       bool strict, search::fef::MatchData & md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    std::unique_ptr<AndSearch> search;
    if (should_do_termwise_eval(unpack_info, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, std::move(sub_searches), unpack_info);
        bool termwise_strict = (strict && inheritStrict(helper.first_termwise));
        auto termwise_search = AndSearch::create(helper.get_termwise_children(), termwise_strict);
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        } else {
            search = AndSearch::create(std::move(rearranged), strict, helper.termwise_unpack);
        }
    } else {
        search = AndSearch::create(std::move(sub_searches), strict, unpack_info);
    }
    search->estimate(getState().estimate().estHits);
    return search;
}

SearchIterator::UP
AndBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_and_filter(get_children(), strict, constraint);
}

double
AndBlueprint::computeNextHitRate(const Blueprint & child, double hit_rate, bool use_estimate) const {
    double estimate = use_estimate ? child.estimate() : child.hit_ratio();
    return hit_rate * estimate;
}

double
OrBlueprint::computeNextHitRate(const Blueprint & child, double hit_rate, bool use_estimate) const {
    // Avoid dropping hitRate to zero when meeting a conservatively high hitrate in a child.
    // Happens at least when using non fast-search attributes, and with AND nodes.
    constexpr double MIN_INVERSE_HIT_RATIO = 0.10;
    double estimate = use_estimate ? child.estimate() : child.hit_ratio();
    double inverse_child_estimate = 1.0 - estimate;
    return (use_estimate || (inverse_child_estimate > MIN_INVERSE_HIT_RATIO))
        ? hit_rate * inverse_child_estimate
        : hit_rate;
}

//-----------------------------------------------------------------------------

OrBlueprint::~OrBlueprint() = default;

double
OrBlueprint::calculate_relative_estimate() const {
    return rel_est_or(get_children());
}

Blueprint::HitEstimate
OrBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return sat_sum(data, get_docid_limit());
}

FieldSpecBaseList
OrBlueprint::exposeFields() const
{
    return mixChildrenFields();
}

void
OrBlueprint::optimize_self(OptimizePass pass)
{
    if (pass == OptimizePass::FIRST) {
        for (size_t i = 0; (childCnt() > 1) && (i < childCnt()); ++i) {
            if (auto *child = getChild(i).asOr()) {
                while (child->childCnt() > 0) {
                    addChild(child->removeLastChild());
                }
                removeChild(i--);
            } else if (getChild(i).getState().estimate().empty) {
                removeChild(i--);
            }
        }
    }
    if (pass == OptimizePass::LAST) {
        optimize_source_blenders<OrBlueprint>(*this, 0);
    }
}

Blueprint::UP
OrBlueprint::get_replacement()
{
    if (childCnt() == 1) {
        return removeChild(0);
    }
    return {};
}

void
OrBlueprint::sort(Children &children) const
{
    std::sort(children.begin(), children.end(), TieredGreaterEstimate());
}

bool
OrBlueprint::inheritStrict(size_t) const
{
    return true;
}

SearchIterator::UP
OrBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                      bool strict, search::fef::MatchData & md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    if (should_do_termwise_eval(unpack_info, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, std::move(sub_searches), unpack_info);
        bool termwise_strict = (strict && inheritStrict(helper.first_termwise));
        auto termwise_search = OrSearch::create(helper.get_termwise_children(), termwise_strict);
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        }
        return OrSearch::create(std::move(rearranged), strict, helper.termwise_unpack);
    }
    return OrSearch::create(std::move(sub_searches), strict, unpack_info);
}

SearchIterator::UP
OrBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_or_filter(get_children(), strict, constraint);
}

uint8_t
OrBlueprint::calculate_cost_tier() const
{
    uint8_t cost_tier = State::COST_TIER_NORMAL;
    for (const Blueprint::UP &child : get_children()) {
        cost_tier = std::max(cost_tier, child->getState().cost_tier());
    }
    return cost_tier;
}

//-----------------------------------------------------------------------------
WeakAndBlueprint::~WeakAndBlueprint() = default;

double
WeakAndBlueprint::calculate_relative_estimate() const {
    double child_est = rel_est_or(get_children());
    double my_est = abs_to_rel_est(_n, get_docid_limit());
    return std::min(my_est, child_est);
}

Blueprint::HitEstimate
WeakAndBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    HitEstimate childEst = max(data);
    HitEstimate myEst(_n, false);
    if (childEst < myEst) {
        return childEst;
    }
    return myEst;
}

FieldSpecBaseList
WeakAndBlueprint::exposeFields() const
{
    return {};
}

void
WeakAndBlueprint::sort(Children &) const
{
    // order needs to stay the same as _weights
}

bool
WeakAndBlueprint::inheritStrict(size_t) const
{
    return true;
}

bool
WeakAndBlueprint::always_needs_unpack() const
{
    return true;
}

SearchIterator::UP
WeakAndBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                           bool strict, search::fef::MatchData &) const
{
    WeakAndSearch::Terms terms;
    assert(sub_searches.size() == childCnt());
    assert(_weights.size() == childCnt());
    for (size_t i = 0; i < sub_searches.size(); ++i) {
        // TODO: pass ownership with unique_ptr
        terms.emplace_back(sub_searches[i].release(),
                           _weights[i],
                           getChild(i).getState().estimate().estHits);
    }
    return WeakAndSearch::create(terms, _n, strict);
}

SearchIterator::UP
WeakAndBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_or_filter(get_children(), strict, constraint);
}

//-----------------------------------------------------------------------------

double
NearBlueprint::calculate_relative_estimate() const {
    return rel_est_and(get_children());
}

Blueprint::HitEstimate
NearBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return min(data);
}

FieldSpecBaseList
NearBlueprint::exposeFields() const
{
    return {};
}

void
NearBlueprint::sort(Children &children) const
{
    std::sort(children.begin(), children.end(), TieredLessEstimate());
}

bool
NearBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
NearBlueprint::createSearch(fef::MatchData &md, bool strict) const
{
    need_normal_features_for_children(*this, md);
    return IntermediateBlueprint::createSearch(md, strict);
}

SearchIterator::UP
NearBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                        bool strict, search::fef::MatchData &md) const
{
    search::fef::TermFieldMatchDataArray tfmda;
    for (size_t i = 0; i < childCnt(); ++i) {
        const State &cs = getChild(i).getState();
        for (size_t j = 0; j < cs.numFields(); ++j) {
            tfmda.add(cs.field(j).resolve(md));
        }
    }
    return SearchIterator::UP(new NearSearch(std::move(sub_searches), tfmda, _window, strict));
}

SearchIterator::UP
NearBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_and_filter(get_children(), strict, constraint);
}

//-----------------------------------------------------------------------------

double
ONearBlueprint::calculate_relative_estimate() const {
    return rel_est_and(get_children());
}

Blueprint::HitEstimate
ONearBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return min(data);
}

FieldSpecBaseList
ONearBlueprint::exposeFields() const
{
    return {};
}

void
ONearBlueprint::sort(Children &children) const
{
    // ordered near cannot sort children here
    (void)children;
}

bool
ONearBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
ONearBlueprint::createSearch(fef::MatchData &md, bool strict) const
{
    need_normal_features_for_children(*this, md);
    return IntermediateBlueprint::createSearch(md, strict);
}

SearchIterator::UP
ONearBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                         bool strict, search::fef::MatchData &md) const
{
    search::fef::TermFieldMatchDataArray tfmda;
    for (size_t i = 0; i < childCnt(); ++i) {
        const State &cs = getChild(i).getState();
        for (size_t j = 0; j < cs.numFields(); ++j) {
            tfmda.add(cs.field(j).resolve(md));
        }
    }
    // could sort sub_searches here
    // but then strictness inheritance would also need to be fixed
    return SearchIterator::UP(new ONearSearch(std::move(sub_searches), tfmda, _window, strict));
}

SearchIterator::UP
ONearBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_and_filter(get_children(), strict, constraint);
}

//-----------------------------------------------------------------------------

double
RankBlueprint::calculate_relative_estimate() const {
    return rel_est_first_child(get_children());
}

Blueprint::HitEstimate
RankBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    if (data.empty()) {
        return {};
    }
    return data[0];
}

FieldSpecBaseList
RankBlueprint::exposeFields() const
{
    return {};
}

void
RankBlueprint::optimize_self(OptimizePass pass)
{
    if (pass == OptimizePass::FIRST) {
        for (size_t i = 1; i < childCnt(); ++i) {
            if (getChild(i).getState().estimate().empty) {
                removeChild(i--);
            }
        }
    }
    if (pass == OptimizePass::LAST) {
        optimize_source_blenders<OrBlueprint>(*this, 1);
    }
}

Blueprint::UP
RankBlueprint::get_replacement()
{
    if (childCnt() == 1) {
        return removeChild(0);
    }
    return {};
}

void
RankBlueprint::sort(Children &children) const
{
    (void)children;
}

bool
RankBlueprint::inheritStrict(size_t i) const
{
    return (i == 0);
}

SearchIterator::UP
RankBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                        bool strict, search::fef::MatchData & md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    if (unpack_info.unpackAll()) {
        return RankSearch::create(std::move(sub_searches), strict);
    } else {
        MultiSearch::Children require_unpack;
        require_unpack.reserve(sub_searches.size());
        require_unpack.push_back(std::move(sub_searches[0]));
        for (size_t i(1); i < sub_searches.size(); i++) {
            if (unpack_info.needUnpack(i)) {
                require_unpack.push_back(std::move(sub_searches[i]));
            } else {
                sub_searches[i].reset();
            }
        }
        if (require_unpack.size() == 1) {
            return std::move(require_unpack[0]);
        } else {
            return RankSearch::create(std::move(require_unpack), strict);
        }
    }
}

SearchIterator::UP
RankBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_first_child_filter(get_children(), strict, constraint);
}

//-----------------------------------------------------------------------------

SourceBlenderBlueprint::SourceBlenderBlueprint(const ISourceSelector &selector) noexcept
    : _selector(selector)
{
}

SourceBlenderBlueprint::~SourceBlenderBlueprint() = default;

double
SourceBlenderBlueprint::calculate_relative_estimate() const {
    return rel_est_or(get_children());
}

Blueprint::HitEstimate
SourceBlenderBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    return max(data);
}

FieldSpecBaseList
SourceBlenderBlueprint::exposeFields() const
{
    return mixChildrenFields();
}

void
SourceBlenderBlueprint::sort(Children &) const
{
}

bool
SourceBlenderBlueprint::inheritStrict(size_t) const
{
    return true;
}

SearchIterator::UP
SourceBlenderBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                                 bool strict, search::fef::MatchData &) const
{
    SourceBlenderSearch::Children children;
    assert(sub_searches.size() == childCnt());
    for (size_t i = 0; i < sub_searches.size(); ++i) {
        // TODO: pass ownership with unique_ptr
        children.emplace_back(sub_searches[i].release(), getChild(i).getSourceId());
        assert(children.back().sourceId != 0xffffffff);
    }
    return SourceBlenderSearch::create(_selector.createIterator(), children, strict);
}

SearchIterator::UP
SourceBlenderBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_or_filter(get_children(), strict, constraint);
}

bool
SourceBlenderBlueprint::isCompatibleWith(const SourceBlenderBlueprint &other) const
{
    return (&_selector == &other._selector);
}

uint8_t
SourceBlenderBlueprint::calculate_cost_tier() const
{
    uint8_t cost_tier = State::COST_TIER_NORMAL;
    for (const Blueprint::UP &child : get_children()) {
        cost_tier = std::max(cost_tier, child->getState().cost_tier());
    }
    return cost_tier;
}

//-----------------------------------------------------------------------------

}
