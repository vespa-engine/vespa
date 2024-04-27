// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intermediate_blueprints.h"
#include "flow_tuning.h"
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

} // namespace search::queryeval::<unnamed>

//-----------------------------------------------------------------------------

FlowStats
AndNotBlueprint::calculate_flow_stats(uint32_t) const
{
    return {AndNotFlow::estimate_of(get_children()),
            AndNotFlow::cost_of(get_children(), false),
            AndNotFlow::cost_of(get_children(), true)};
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
                    auto orphan = grand_child->removeChild(0);
                    if (auto *orphan_and = orphan->asAnd()) {
                        while (orphan_and->childCnt() > 0) {
                            child->addChild(orphan_and->removeLastChild());
                        }
                    } else {
                        child->addChild(std::move(orphan));
                    }
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
AndNotBlueprint::sort(Children &children, InFlow in_flow) const
{
    if (opt_sort_by_cost()) {
        AndNotFlow::sort(children, in_flow.strict());
    } else {
        if (children.size() > 2) {
            std::sort(children.begin() + 1, children.end(), TieredGreaterEstimate());
        }
    }
}

SearchIterator::UP
AndNotBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                          search::fef::MatchData &md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    if (should_do_termwise_eval(unpack_info, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, std::move(sub_searches), unpack_info);
        bool termwise_strict = ((helper.first_termwise < childCnt()) &&
                                getChild(helper.first_termwise).strict());
        auto termwise_search = (helper.first_termwise == 0)
                               ? AndNotSearch::create(helper.get_termwise_children(), termwise_strict)
                               : OrSearch::create(helper.get_termwise_children(), termwise_strict);
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        }
        return AndNotSearch::create(std::move(rearranged), strict());
    }
    return AndNotSearch::create(std::move(sub_searches), strict());
}

SearchIterator::UP
AndNotBlueprint::createFilterSearch(FilterConstraint constraint) const
{
    return create_andnot_filter(get_children(), strict(), constraint);
}


AnyFlow
AndNotBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<AndNotFlow>(in_flow);
}

//-----------------------------------------------------------------------------

FlowStats
AndBlueprint::calculate_flow_stats(uint32_t) const {
    return {AndFlow::estimate_of(get_children()),
            AndFlow::cost_of(get_children(), false),
            AndFlow::cost_of(get_children(), true)};
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
AndBlueprint::sort(Children &children, InFlow in_flow) const
{
    if (opt_sort_by_cost()) {
        AndFlow::sort(children, in_flow.strict());
        if (opt_allow_force_strict()) {
            AndFlow::reorder_for_extra_strictness(children, in_flow, 3);
        }
    } else {
        std::sort(children.begin(), children.end(), TieredLessEstimate());
    }
}

SearchIterator::UP
AndBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                       search::fef::MatchData & md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    std::unique_ptr<AndSearch> search;
    if (should_do_termwise_eval(unpack_info, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, std::move(sub_searches), unpack_info);
        bool termwise_strict = ((helper.first_termwise < childCnt()) &&
                                getChild(helper.first_termwise).strict());
        auto termwise_search = AndSearch::create(helper.get_termwise_children(), termwise_strict);
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        } else {
            search = AndSearch::create(std::move(rearranged), strict(), helper.termwise_unpack);
        }
    } else {
        search = AndSearch::create(std::move(sub_searches), strict(), unpack_info);
    }
    search->estimate(getState().estimate().estHits);
    return search;
}

SearchIterator::UP
AndBlueprint::createFilterSearch(FilterConstraint constraint) const
{
    return create_and_filter(get_children(), strict(), constraint);
}

AnyFlow
AndBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<AndFlow>(in_flow);
}

//-----------------------------------------------------------------------------

OrBlueprint::~OrBlueprint() = default;

FlowStats
OrBlueprint::calculate_flow_stats(uint32_t) const {
    double est = OrFlow::estimate_of(get_children());
    return {est,
            OrFlow::cost_of(get_children(), false),
            OrFlow::cost_of(get_children(), true) + flow::heap_cost(est, get_children().size())};
}

double
OrBlueprint::estimate_self_cost(InFlow in_flow) const noexcept {
    return in_flow.strict() ? flow::heap_cost(estimate(), get_children().size()) : 0.0;
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
OrBlueprint::sort(Children &children, InFlow in_flow) const
{
    if (opt_sort_by_cost()) {
        OrFlow::sort(children, in_flow.strict());
    } else {
        std::sort(children.begin(), children.end(), TieredGreaterEstimate());
    }
}

SearchIterator::UP
OrBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                      search::fef::MatchData & md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    if (should_do_termwise_eval(unpack_info, md.get_termwise_limit())) {
        TermwiseBlueprintHelper helper(*this, std::move(sub_searches), unpack_info);
        bool termwise_strict = ((helper.first_termwise < childCnt()) &&
                                getChild(helper.first_termwise).strict());
        auto termwise_search = OrSearch::create(helper.get_termwise_children(), termwise_strict);
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        }
        return OrSearch::create(std::move(rearranged), strict(), helper.termwise_unpack);
    }
    return OrSearch::create(std::move(sub_searches), strict(), unpack_info);
}

SearchIterator::UP
OrBlueprint::createFilterSearch(FilterConstraint constraint) const
{
    return create_or_filter(get_children(), strict(), constraint);
}

AnyFlow
OrBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<OrFlow>(in_flow);
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

AnyFlow
WeakAndBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<OrFlow>(in_flow);
}

WeakAndBlueprint::~WeakAndBlueprint() = default;

FlowStats
WeakAndBlueprint::calculate_flow_stats(uint32_t docid_limit) const {
    double child_est = OrFlow::estimate_of(get_children());
    double my_est = abs_to_rel_est(_n, docid_limit);
    double est = (child_est + my_est) / 2.0;
    return {est,
            OrFlow::cost_of(get_children(), false),
            OrFlow::cost_of(get_children(), true) + flow::heap_cost(est, get_children().size())};
}

double
WeakAndBlueprint::estimate_self_cost(InFlow in_flow) const noexcept {
    return in_flow.strict() ? flow::heap_cost(estimate(), get_children().size()) : 0.0;
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

Blueprint::UP
WeakAndBlueprint::get_replacement()
{
    if (childCnt() == 1) {
        return removeChild(0);
    }
    return {};
}

void
WeakAndBlueprint::sort(Children &, InFlow) const
{
    // order needs to stay the same as _weights
}

bool
WeakAndBlueprint::always_needs_unpack() const
{
    return true;
}

SearchIterator::UP
WeakAndBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                           search::fef::MatchData &) const
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
    return WeakAndSearch::create(terms, _n, strict());
}

SearchIterator::UP
WeakAndBlueprint::createFilterSearch(FilterConstraint constraint) const
{
    return create_atmost_or_filter(get_children(), strict(), constraint);
}

//-----------------------------------------------------------------------------

AnyFlow
NearBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<AndFlow>(in_flow);
}

FlowStats
NearBlueprint::calculate_flow_stats(uint32_t) const {
    double est = AndFlow::estimate_of(get_children()); 
    return {est,
            AndFlow::cost_of(get_children(), false) + childCnt() * est,
            AndFlow::cost_of(get_children(), true) + childCnt() * est};
}

double
NearBlueprint::estimate_self_cost(InFlow) const noexcept {
    return childCnt() * estimate();
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
NearBlueprint::sort(Children &children, InFlow in_flow) const
{
    if (opt_sort_by_cost()) {
        AndFlow::sort(children, in_flow.strict());
    } else {
        std::sort(children.begin(), children.end(), TieredLessEstimate());
    }
}

SearchIterator::UP
NearBlueprint::createSearch(fef::MatchData &md) const
{
    need_normal_features_for_children(*this, md);
    return IntermediateBlueprint::createSearch(md);
}

SearchIterator::UP
NearBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                        search::fef::MatchData &md) const
{
    search::fef::TermFieldMatchDataArray tfmda;
    for (size_t i = 0; i < childCnt(); ++i) {
        const State &cs = getChild(i).getState();
        for (size_t j = 0; j < cs.numFields(); ++j) {
            tfmda.add(cs.field(j).resolve(md));
        }
    }
    return SearchIterator::UP(new NearSearch(std::move(sub_searches), tfmda, _window, strict()));
}

SearchIterator::UP
NearBlueprint::createFilterSearch(FilterConstraint constraint) const
{
    return create_atmost_and_filter(get_children(), strict(), constraint);
}

//-----------------------------------------------------------------------------

AnyFlow
ONearBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<AndFlow>(in_flow);
}

FlowStats
ONearBlueprint::calculate_flow_stats(uint32_t) const {
    double est = AndFlow::estimate_of(get_children());
    return {est,
            AndFlow::cost_of(get_children(), false) + childCnt() * est,
            AndFlow::cost_of(get_children(), true) + childCnt() * est};
}

double
ONearBlueprint::estimate_self_cost(InFlow) const noexcept {
    return childCnt() * estimate();
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
ONearBlueprint::sort(Children &, InFlow) const
{
    // ordered near cannot sort children here
}

SearchIterator::UP
ONearBlueprint::createSearch(fef::MatchData &md) const
{
    need_normal_features_for_children(*this, md);
    return IntermediateBlueprint::createSearch(md);
}

SearchIterator::UP
ONearBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                         search::fef::MatchData &md) const
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
    return SearchIterator::UP(new ONearSearch(std::move(sub_searches), tfmda, _window, strict()));
}

SearchIterator::UP
ONearBlueprint::createFilterSearch(FilterConstraint constraint) const
{
    return create_atmost_and_filter(get_children(), strict(), constraint);
}

//-----------------------------------------------------------------------------

FlowStats
RankBlueprint::calculate_flow_stats(uint32_t) const {
    if (childCnt() == 0) {
        return {0.0, 0.0, 0.0};
    }
    return {getChild(0).estimate(),
            getChild(0).cost(),
            getChild(0).strict_cost()};
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
RankBlueprint::sort(Children &, InFlow) const
{
}

SearchIterator::UP
RankBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                        search::fef::MatchData & md) const
{
    UnpackInfo unpack_info(calculateUnpackInfo(md));
    if (unpack_info.unpackAll()) {
        return RankSearch::create(std::move(sub_searches), strict());
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
            return RankSearch::create(std::move(require_unpack), strict());
        }
    }
}

SearchIterator::UP
RankBlueprint::createFilterSearch(FilterConstraint constraint) const
{
    return create_first_child_filter(get_children(), constraint);
}

AnyFlow
RankBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<RankFlow>(in_flow);
}

//-----------------------------------------------------------------------------

AnyFlow
SourceBlenderBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<BlenderFlow>(in_flow);
}

SourceBlenderBlueprint::SourceBlenderBlueprint(const ISourceSelector &selector) noexcept
    : _selector(selector)
{
}

SourceBlenderBlueprint::~SourceBlenderBlueprint() = default;

FlowStats
SourceBlenderBlueprint::calculate_flow_stats(uint32_t) const {
    double my_cost = 0.0;
    double my_strict_cost = 0.0;
    for (const auto &child: get_children()) {
        my_cost = std::max(my_cost, child->cost());
        my_strict_cost = std::max(my_strict_cost, child->strict_cost());
    }
    return {OrFlow::estimate_of(get_children()), my_cost, my_strict_cost};
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
SourceBlenderBlueprint::sort(Children &, InFlow) const
{
}

SearchIterator::UP
SourceBlenderBlueprint::createIntermediateSearch(MultiSearch::Children sub_searches,
                                                 search::fef::MatchData &) const
{
    SourceBlenderSearch::Children children;
    assert(sub_searches.size() == childCnt());
    for (size_t i = 0; i < sub_searches.size(); ++i) {
        // TODO: pass ownership with unique_ptr
        children.emplace_back(sub_searches[i].release(), getChild(i).getSourceId());
        assert(children.back().sourceId != 0xffffffff);
    }
    return SourceBlenderSearch::create(_selector.createIterator(), children, strict());
}

SearchIterator::UP
SourceBlenderBlueprint::createFilterSearch(FilterConstraint constraint) const
{
    return create_atmost_or_filter(get_children(), strict(), constraint);
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
