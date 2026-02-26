// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intermediate_blueprints.h"
#include "flow_tuning.h"
#include "andnotsearch.h"
#include "andsearch.h"
#include "orsearch.h"
#include "nearsearch.h"
#include "ranksearch.h"
#include "lazy_filter.h"
#include "leaf_blueprints.h"
#include "sourceblendersearch.h"
#include "termwise_blueprint_helper.h"
#include "isourceselector.h"
#include "field_spec.hpp"
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>
#include <vespa/vespalib/util/require.h>

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
    sources.push_back(std::make_unique<CombineType>());
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

} // namespace search::queryeval::<unnamed>

//-----------------------------------------------------------------------------

AndNotBlueprint::AndNotBlueprint()
    : IntermediateBlueprint(),
      _elementwise(false)
{
}

AndNotBlueprint::AndNotBlueprint(bool elementwise)
    : IntermediateBlueprint(),
      _elementwise(elementwise)
{
}

AndNotBlueprint::~AndNotBlueprint() = default;

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
            if (auto *child = getChild(i).asOr()) {
                while (child->childCnt() > 0) {
                    addChild(child->removeLastChild());
                }
                removeChild(i--);
            } else if (getChild(i).getState().estimate().empty && !opt_preserve_children()) {
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
        termwise_search->set_id(id());
        helper.insert_termwise(std::move(termwise_search), termwise_strict);
        auto rearranged = helper.get_result();
        if (rearranged.size() == 1) {
            return std::move(rearranged[0]);
        }
        return AndNotSearch::create(std::move(rearranged), strict());
    }
    return AndNotSearch::create(std::move(sub_searches), _elementwise, strict());
}

SearchIterator::UP
AndNotBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    if (_elementwise && constraint == FilterConstraint::UPPER_BOUND) {
        return create_first_child_filter(get_children(), constraint);
    }
    return create_andnot_filter(get_children(), constraint);
}

std::shared_ptr<GlobalFilter>
AndNotBlueprint::create_lazy_filter() const
{
    auto &children = get_children();
    REQUIRE(!children.empty());
    return children[0]->create_lazy_filter();
}

AnyFlow
AndNotBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<AndNotFlow>(in_flow);
}

//-----------------------------------------------------------------------------

AndBlueprint::~AndBlueprint() = default;

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
        for (size_t i = 0; childCnt() > 1 && i < childCnt(); ++i) {
            if (auto *child = getChild(i).asAnd()) {
                while (child->childCnt() > 0) {
                    addChild(child->removeLastChild());
                }
                removeChild(i--);
            } else if (getChild(i).asAlwaysTrue() != nullptr) {
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
        termwise_search->set_id(id());
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
AndBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_and_filter(get_children(), constraint);
}

std::shared_ptr<GlobalFilter>
AndBlueprint::create_lazy_filter() const {
    std::vector<std::shared_ptr<GlobalFilter>> lazy_filters;
    for (const auto & child : get_children()) {
        auto lazy_filter = child->create_lazy_filter();
        if (lazy_filter->is_active()) {
            lazy_filters.push_back(std::move(lazy_filter));
        }
    }

    if (lazy_filters.size() == 1) {
        return lazy_filters[0];
    }

    if (lazy_filters.size() > 1) {
        return AndFilter::create(std::move(lazy_filters));
    }

    return GlobalFilter::create();
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
            } else if (getChild(i).getState().estimate().empty && !opt_preserve_children()) {
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
        termwise_search->set_id(id());
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
OrBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_or_filter(get_children(), constraint);
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

WeakAndBlueprint::WeakAndBlueprint(uint32_t n, wand::StopWordStrategy stop_word_strategy, bool thread_safe)
    : _scores(WeakAndPriorityQueue::createHeap(n, thread_safe)),
      _n(n),
      _stop_word_strategy(stop_word_strategy),
      _weights(),
      _matching_phase(MatchingPhase::FIRST_PHASE)
{}

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

Blueprint::HitEstimate
WeakAndBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    auto or_est = sat_sum(data, get_docid_limit());
    if (or_est.estHits < _n) {
        return or_est;
    }
    // use average of target hits and OR estimate
    return {(_n + or_est.estHits + 1) / 2, false};
}

FieldSpecBaseList
WeakAndBlueprint::exposeFields() const
{
    return {};
}

void
WeakAndBlueprint::optimize_self(OptimizePass pass)
{
    if (pass == OptimizePass::FIRST && !_stop_word_strategy.keep_all()) {
        uint32_t min_est = 0;
        uint32_t min_est_idx = 0;
        vespalib::SmallVector<uint32_t,16> drop;
        for (size_t i = 0; i < childCnt(); ++i) {
            uint32_t child_est = getChild(i).getState().estimate().estHits;
            if (_stop_word_strategy.should_drop(child_est)) {
                drop.push_back(i);
            }
            if (i == 0 || child_est < min_est) {
                min_est = child_est;
                min_est_idx = i;
            }
        }
        while (!drop.empty()) {
            uint32_t idx = drop.back();
            drop.pop_back();
            if (idx != min_est_idx || _stop_word_strategy.allow_drop_all()) {
                removeChild(idx);
                _weights.erase(_weights.begin() + idx);
            }
        }
    }
}

Blueprint::UP
WeakAndBlueprint::get_replacement()
{
    if (childCnt() == 0) {
        return std::make_unique<EmptyBlueprint>();
    }
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
        terms.emplace_back(sub_searches[i].release(), _weights[i],
                           getChild(i).getState().estimate().estHits);
    }
    bool readonly_scores_heap = (_matching_phase != MatchingPhase::FIRST_PHASE);
    wand::MatchParams innerParams{*_scores, _stop_word_strategy, wand::DEFAULT_PARALLEL_WAND_SCORES_ADJUST_FREQUENCY, get_docid_limit()};
    return WeakAndSearch::create(terms, innerParams, wand::Bm25TermFrequencyScorer(get_docid_limit()), _n, strict(),
                                 readonly_scores_heap);
}

SearchIterator::UP
WeakAndBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_atmost_or_filter(get_children(), constraint);
}

void
WeakAndBlueprint::set_matching_phase(MatchingPhase matching_phase) noexcept
{
    _matching_phase = matching_phase;
    if (matching_phase != MatchingPhase::FIRST_PHASE) {
        /*
         * During first phase matching, the scores heap is adjusted by
         * the iterators. The minimum score is increased when the
         * scores heap is full while handling a matching document with
         * a higher score than the worst existing one.
         *
         * During later matching phases, only the original minimum
         * score is used, and the heap is not updated by the
         * iterators. This ensures that all documents considered a hit
         * by the first phase matching will also be considered as hits
         * by the later matching phases.
         */
        _scores->set_min_score(1);
    }
}


//-----------------------------------------------------------------------------

NearBlueprint::~NearBlueprint() = default;

void
NearBlueprint::optimize(Blueprint* &self, OptimizePass pass)
{
    auto opts_guard = bind_opts(get_thread_opts().preserve_children(true));
    IntermediateBlueprint::optimize(self, pass);
}

AnyFlow
NearBlueprint::my_flow(InFlow in_flow) const
{
    size_t num_positive_terms = sat_sub(get_children().size(), _num_negative_terms);
    return AnyFlow::create<AndFlow>(in_flow, num_positive_terms);
}

FlowStats
NearBlueprint::calculate_flow_stats(uint32_t) const {
    size_t num_positive_terms = sat_sub(get_children().size(), _num_negative_terms);
    auto positive_terms = std::span(get_children().data(), num_positive_terms);
    double est = AndFlow::estimate_of(positive_terms);
    return {est,
            AndFlow::cost_of(positive_terms, false) + childCnt() * est,
            AndFlow::cost_of(positive_terms, true) + childCnt() * est};
}

Blueprint::HitEstimate
NearBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    // Only consider positive terms for hit estimate
    size_t positive_count = sat_sub(data.size(), _num_negative_terms);
    std::vector<HitEstimate> positive_data(data.begin(), data.begin() + positive_count);
    return min(positive_data);
}

FieldSpecBaseList
NearBlueprint::exposeFields() const
{
    return {};
}

void
NearBlueprint::sort(Children &children, InFlow in_flow) const
{
    (void) in_flow;
    // Only sort positive terms; negative terms must stay at the end
    size_t positive_count = sat_sub(children.size(), _num_negative_terms);
    std::sort(children.begin(), children.begin() + positive_count, TieredLessEstimate());
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
    return std::make_unique<NearSearch>(std::move(sub_searches), tfmda, _window, _num_negative_terms, _exclusion_distance, _element_gap_inspector, strict());
}

SearchIterator::UP
NearBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    size_t positive_count = sat_sub(get_children().size(), _num_negative_terms);
    if (positive_count > 0) {
        return create_atmost_and_filter(std::span(get_children().data(), positive_count), constraint);
    }
    return std::make_unique<EmptySearch>();
}

//-----------------------------------------------------------------------------

ONearBlueprint::~ONearBlueprint() = default;

void
ONearBlueprint::optimize(Blueprint* &self, OptimizePass pass)
{
    auto opts_guard = bind_opts(get_thread_opts().preserve_children(true));
    IntermediateBlueprint::optimize(self, pass);
}

AnyFlow
ONearBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<AndFlow>(in_flow);
}

FlowStats
ONearBlueprint::calculate_flow_stats(uint32_t) const {
    size_t num_positive_terms = sat_sub(get_children().size(), _num_negative_terms);
    auto positive_terms = std::span(get_children().data(), num_positive_terms);
    double est = AndFlow::estimate_of(positive_terms);
    return {est,
            AndFlow::cost_of(positive_terms, false) + childCnt() * est,
            AndFlow::cost_of(positive_terms, true) + childCnt() * est};
}

Blueprint::HitEstimate
ONearBlueprint::combine(const std::vector<HitEstimate> &data) const
{
    // Only consider positive terms for hit estimate
    size_t positive_count = sat_sub(data.size(), _num_negative_terms);
    std::vector<HitEstimate> positive_data(data.begin(), data.begin() + positive_count);
    return min(positive_data);
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
    return std::make_unique<ONearSearch>(std::move(sub_searches), tfmda, _window, _num_negative_terms, _exclusion_distance, _element_gap_inspector, strict());
}

SearchIterator::UP
ONearBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    size_t positive_count = sat_sub(get_children().size(), _num_negative_terms);
    if (positive_count > 0) {
        return create_atmost_and_filter(std::span(get_children().data(), positive_count), constraint);
    }
    return std::make_unique<EmptySearch>();
}

//-----------------------------------------------------------------------------

RankBlueprint::~RankBlueprint() = default;

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
            if (getChild(i).getState().estimate().empty && !opt_preserve_children()) {
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
RankBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
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
    double my_est = OrFlow::estimate_of(get_children());
    return {my_est, my_cost + 1.0, my_strict_cost + my_est};
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
SourceBlenderBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_atmost_or_filter(get_children(), constraint);
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
