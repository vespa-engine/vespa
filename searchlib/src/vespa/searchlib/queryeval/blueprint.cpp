// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprint.h"
#include "andnotsearch.h"
#include "andsearch.h"
#include "emptysearch.h"
#include "field_spec.hpp"
#include "flow_tuning.h"
#include "full_search.h"
#include "global_filter.h"
#include "leaf_blueprints.h"
#include "matching_elements_search.h"
#include "orsearch.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/objects/objectdumper.h>
#include <vespa/vespalib/objects/object2slime.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <map>

#include <vespa/log/log.h>
LOG_SETUP(".queryeval.blueprint");

using vespalib::Trinary;

namespace search::queryeval {

//-----------------------------------------------------------------------------

void maybe_eliminate_self(Blueprint* &self, Blueprint::UP replacement) {
    // replace with replacement
    if (replacement) {
        Blueprint::UP discard(self);
        self = replacement.release();
        self->setParent(discard->getParent());
        self->setSourceId(discard->getSourceId());
        self->setDocIdLimit(discard->get_docid_limit());
        discard->setParent(nullptr);
    }
    // replace with empty blueprint if empty, skip if already empty blueprint
    if ((self->as_empty() == nullptr) && self->getState().estimate().empty) {
        Blueprint::UP discard(self);
        self = new EmptyBlueprint(discard->getState().fields());
        self->setParent(discard->getParent());
        self->setSourceId(discard->getSourceId());
        self->setDocIdLimit(discard->get_docid_limit());
        discard->setParent(nullptr);
    }
}

//-----------------------------------------------------------------------------

thread_local Blueprint::Options Blueprint::_opts;

Blueprint::HitEstimate
Blueprint::max(const std::vector<HitEstimate> &data)
{
    HitEstimate est;
    for (const HitEstimate & hitEst : data) {
        if (est.empty || est.estHits < hitEst.estHits) {
            est = hitEst;
        }
    }
    return est;
}

Blueprint::HitEstimate
Blueprint::min(const std::vector<HitEstimate> &data)
{
    HitEstimate est;
    for (size_t i = 0; i < data.size(); ++i) {
        if (i == 0 || data[i].empty || data[i].estHits < est.estHits) {
            est = data[i];
        }
    }
    return est;
}

Blueprint::HitEstimate
Blueprint::sat_sum(const std::vector<HitEstimate> &data, uint32_t docid_limit)
{
    uint64_t sum = 0;
    bool empty = true;
    uint32_t limit = docid_limit;
    for (const auto &est: data) {
        sum += est.estHits;
        empty = (empty && est.empty);
        limit = std::max(limit, est.estHits);
    }
    return { uint32_t(std::min(sum, uint64_t(limit))), empty };
}

Blueprint::State::State() noexcept
    : _fields(),
      _estimateHits(0),
      _tree_size(1),
      _estimateEmpty(true),
      _allow_termwise_eval(true),
      _cost_tier(COST_TIER_NORMAL)
{}

Blueprint::State::State(FieldSpecBase field) noexcept
    : State()
{
    _fields.add(field);
}

Blueprint::State::State(FieldSpecBaseList fields_in) noexcept
    : _fields(std::move(fields_in)),
      _estimateHits(0),
      _tree_size(1),
      _estimateEmpty(true),
      _allow_termwise_eval(true),
      _cost_tier(COST_TIER_NORMAL)
{
}

Blueprint::State::~State() = default;

Blueprint::Blueprint() noexcept
    : _parent(nullptr),
      _flow_stats(0.0, 0.0, 0.0),
      _sourceId(0xffffffff),
      _docid_limit(0),
      _id(0),
      _strict(false),
      _frozen(false)
{
}

Blueprint::~Blueprint() = default;

void
Blueprint::resolve_strict(InFlow &in_flow) noexcept
{
    if (!in_flow.strict() && opt_allow_force_strict()) {
        auto stats = FlowStats::from(flow::DefaultAdapter(), this);
        if (flow::should_force_strict(stats, in_flow.rate())) {
            in_flow.force_strict();
        }
    }
    _strict = in_flow.strict();
}

uint32_t
Blueprint::enumerate(uint32_t next_id) noexcept
{
    set_id(next_id++);
    return next_id;
}

void
Blueprint::each_node_post_order(const std::function<void(Blueprint&)> &f)
{
    f(*this);
}

void
Blueprint::basic_plan(InFlow in_flow, uint32_t docid_limit)
{
    auto opts_guard = bind_opts(Options().sort_by_cost(true));
    setDocIdLimit(docid_limit);
    each_node_post_order([docid_limit](Blueprint &bp){
                             bp.update_flow_stats(docid_limit);
                         });
    sort(in_flow);
}

void
Blueprint::null_plan(InFlow in_flow, uint32_t docid_limit)
{
    auto opts_guard = bind_opts(Options().keep_order(true));
    setDocIdLimit(docid_limit);
    each_node_post_order([docid_limit](Blueprint &bp){
                             bp.update_flow_stats(docid_limit);
                         });
    sort(in_flow);
}

Blueprint::UP
Blueprint::optimize(Blueprint::UP bp) {
    Blueprint *root = bp.release();
    root->optimize(root, OptimizePass::FIRST);
    root->optimize(root, OptimizePass::LAST);
    return Blueprint::UP(root);
}

void
Blueprint::optimize_self(OptimizePass)
{
}

Blueprint::UP
Blueprint::get_replacement()
{
    return {};
}

void
Blueprint::set_global_filter(const GlobalFilter &, double)
{
}

void
Blueprint::set_lazy_filter(const GlobalFilter &)
{
}

const Blueprint &
Blueprint::root() const
{
    const Blueprint *bp = this;
    while (bp->_parent != nullptr) {
        bp = bp->_parent;
    }
    return *bp;
}

FlowStats
Blueprint::default_flow_stats(uint32_t docid_limit, uint32_t abs_est, size_t child_cnt)
{
    double rel_est = abs_to_rel_est(abs_est, docid_limit);
    double seek_cost = (child_cnt == 0) ? rel_est : (rel_est * 2.0);
    return {rel_est, 1.0 + child_cnt, seek_cost};
}

FlowStats
Blueprint::default_flow_stats(size_t child_cnt)
{
    return {flow::estimate_when_unknown(), 1.0 + child_cnt, 1.0 + child_cnt};
}

std::unique_ptr<MatchingElementsSearch>
Blueprint::create_matching_elements_search(const MatchingElementsFields &fields) const
{
    (void) fields;
    return {};
}

namespace {

Blueprint::FilterConstraint invert(Blueprint::FilterConstraint constraint) {
    if (constraint == Blueprint::FilterConstraint::UPPER_BOUND) {
        return Blueprint::FilterConstraint::LOWER_BOUND;
    }
    if (constraint == Blueprint::FilterConstraint::LOWER_BOUND) {
        return Blueprint::FilterConstraint::UPPER_BOUND;
    }
    abort();
}

template <typename Op> bool should_short_circuit(Trinary);
template <> bool should_short_circuit<AndSearch>(Trinary matches_any) { return (matches_any == Trinary::False); }
template <> bool should_short_circuit<OrSearch>(Trinary matches_any) { return (matches_any == Trinary::True); }

template <typename Op> bool should_prune(Trinary);
template <> bool should_prune<AndSearch>(Trinary matches_any) {
    return (matches_any == Trinary::True);
}
template <> bool should_prune<OrSearch>(Trinary matches_any) { return (matches_any == Trinary::False); }

template <typename Op>
std::unique_ptr<SearchIterator>
create_op_filter(std::span<const Blueprint::UP> children, Blueprint::FilterConstraint constraint)
{
    REQUIRE( ! children.empty());
    MultiSearch::Children list;
    std::unique_ptr<SearchIterator> spare;
    list.reserve(children.size());
    for (const auto & child : children) {
        auto filter = child->createFilterSearch(constraint);
        auto matches_any = filter->matches_any();
        if (should_short_circuit<Op>(matches_any)) {
            return filter;
        }
        if (should_prune<Op>(matches_any)) {
            spare = std::move(filter);
        } else {
            list.push_back(std::move(filter));
        }
    }
    if (list.empty()) {
        assert(spare);
        return spare;
    }
    if (list.size() == 1) {
        return std::move(list[0]);
    }
    UnpackInfo unpack_info;
    return Op::create(std::move(list), false, unpack_info);
}

}

std::unique_ptr<SearchIterator>
Blueprint::create_and_filter(std::span<const UP> children, Blueprint::FilterConstraint constraint)
{
    return create_op_filter<AndSearch>(children, constraint);
}

std::unique_ptr<SearchIterator>
Blueprint::create_or_filter(std::span<const UP> children, Blueprint::FilterConstraint constraint)
{
    return create_op_filter<OrSearch>(children, constraint);
}

std::unique_ptr<SearchIterator>
Blueprint::create_atmost_and_filter(std::span<const UP> children, Blueprint::FilterConstraint constraint)
{
    if (constraint == FilterConstraint::UPPER_BOUND) {
        return create_and_filter(children, constraint);
    } else {
        return std::make_unique<EmptySearch>();
    }
}

std::unique_ptr<SearchIterator>
Blueprint::create_atmost_or_filter(std::span<const UP> children, Blueprint::FilterConstraint constraint)
{
    if (constraint == FilterConstraint::UPPER_BOUND) {
        return create_or_filter(children, constraint);
    } else {
        return std::make_unique<EmptySearch>();
    }
}

std::unique_ptr<SearchIterator>
Blueprint::create_andnot_filter(std::span<const UP> children, Blueprint::FilterConstraint constraint)
{
    REQUIRE( ! children.empty() );
    MultiSearch::Children list;
    list.reserve(children.size());
    {
        auto filter = children[0]->createFilterSearch(constraint);
        if (filter->matches_any() == Trinary::False) {
            return filter;
        }
        list.push_back(std::move(filter));
    }
    for (size_t i = 1; i < children.size(); ++i) {
        auto filter = children[i]->createFilterSearch(invert(constraint));
        auto matches_any = filter->matches_any();
        if (matches_any == Trinary::True) {
            return std::make_unique<EmptySearch>();
        }
        if (matches_any == Trinary::Undefined) {
            list.push_back(std::move(filter));
        }
    }
    assert(!list.empty());
    if (list.size() == 1) {
        return std::move(list[0]);
    }
    return AndNotSearch::create(std::move(list), false);
}

std::unique_ptr<SearchIterator>
Blueprint::create_first_child_filter(std::span<const UP> children, Blueprint::FilterConstraint constraint)
{
    REQUIRE(!children.empty());
    return children[0]->createFilterSearch(constraint);
}

std::unique_ptr<SearchIterator>
Blueprint::create_default_filter(FilterConstraint constraint)
{
    if (constraint == FilterConstraint::UPPER_BOUND) {
        return std::make_unique<FullSearch>();
    } else {
        REQUIRE_EQ(constraint, FilterConstraint::LOWER_BOUND);
        return std::make_unique<EmptySearch>();
    }
}

std::shared_ptr<GlobalFilter>
Blueprint::create_lazy_filter() const {
    return GlobalFilter::create();
}

std::string
Blueprint::asString() const
{
    vespalib::ObjectDumper dumper;
    visit(dumper, "", this);
    return dumper.toString();
}

vespalib::slime::Cursor &
Blueprint::asSlime(const vespalib::slime::Inserter & inserter) const
{
    vespalib::slime::Cursor & cursor = inserter.insertObject();
    vespalib::Object2Slime dumper(cursor);
    visit(dumper, "", this);
    return cursor;
}

std::string
Blueprint::getClassName() const
{
    return vespalib::getClassName(*this);
}

void
Blueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    const State &state = getState();
    visitor.visitBool("isTermLike", state.isTermLike());
    if (state.isTermLike()) {
        visitor.openStruct("fields", "FieldList");
        for (size_t i = 0; i < state.numFields(); ++i) {
            const FieldSpecBase &spec = state.field(i);
            visitor.openStruct(vespalib::make_string("[%zu]", i), "Field");
            visitor.visitInt("fieldId", spec.getFieldId());
            visitor.visitInt("handle", spec.getHandle());
            visitor.visitBool("isFilter", spec.isFilter());
            visitor.closeStruct();
        }
        visitor.closeStruct();
    }
    visitor.openStruct("estimate", "HitEstimate");
    visitor.visitBool("empty", state.estimate().empty);
    visitor.visitInt("estHits", state.estimate().estHits);
    visitor.visitInt("cost_tier", state.cost_tier());
    visitor.visitInt("tree_size", state.tree_size());
    visitor.visitBool("allow_termwise_eval", state.allow_termwise_eval());
    visitor.closeStruct();
    visitor.visitFloat("relative_estimate", estimate());
    visitor.visitFloat("cost", cost());
    visitor.visitFloat("strict_cost", strict_cost());
    visitor.visitInt("sourceId", _sourceId);
    visitor.visitInt("docid_limit", _docid_limit);
    visitor.visitInt("id", _id);
    visitor.visitBool("strict", _strict);
}

namespace blueprint {

//-----------------------------------------------------------------------------

void
StateCache::updateState() const
{
    assert(!frozen());
    _state = calculateState();
    _stale = false;
}

void
StateCache::notifyChange() {
    assert(!frozen());
    if (!_stale) {
        Blueprint::notifyChange();
        _stale = true;
    }
}

} // namespace blueprint

//-----------------------------------------------------------------------------

IntermediateBlueprint::~IntermediateBlueprint() = default;

void
IntermediateBlueprint::setDocIdLimit(uint32_t limit) noexcept
{
    Blueprint::setDocIdLimit(limit);
    for (Blueprint::UP &child : _children) {
        child->setDocIdLimit(limit);
    }
}

uint32_t
IntermediateBlueprint::enumerate(uint32_t next_id) noexcept
{
    set_id(next_id++);
    for (Blueprint::UP &child: _children) {
        next_id = child->enumerate(next_id);
    }
    return next_id;
}

void
IntermediateBlueprint::each_node_post_order(const std::function<void(Blueprint&)> &f)
{
    for (Blueprint::UP &child: _children) {
        child->each_node_post_order(f);
    }
    f(*this);
}

Blueprint::HitEstimate
IntermediateBlueprint::calculateEstimate() const
{
    std::vector<HitEstimate> estimates;
    estimates.reserve(_children.size());
    for (const Blueprint::UP &child : _children) {
        estimates.push_back(child->getState().estimate());
    }
    return combine(estimates);
}

uint8_t
IntermediateBlueprint::calculate_cost_tier() const
{
    uint8_t cost_tier = State::COST_TIER_MAX;
    for (const Blueprint::UP &child : _children) {
        cost_tier = std::min(cost_tier, child->getState().cost_tier());
    }
    return cost_tier;
}

uint32_t
IntermediateBlueprint::calculate_tree_size() const
{
    uint32_t nodes = 1;
    for (const Blueprint::UP &child : _children) {
        nodes += child->getState().tree_size();
    }
    return nodes;
}

bool
IntermediateBlueprint::infer_allow_termwise_eval() const
{
    if (!supports_termwise_children()) {
        return false;
    }
    for (const Blueprint::UP &child : _children) {
        if (!child->getState().allow_termwise_eval()) {
            return false;
        }
    }
    return true;
}

bool
IntermediateBlueprint::want_global_filter(GlobalFilterLimits& limits) const
{
    for (const Blueprint::UP &child : _children) {
        if (child->want_global_filter(limits)) {
            return true;
        }
    }
    return false;
}

size_t
IntermediateBlueprint::count_termwise_nodes(const UnpackInfo &unpack) const
{
    size_t termwise_nodes = 0;
    for (size_t i = 0; i < _children.size(); ++i) {
        const State &state = _children[i]->getState();
        if (state.allow_termwise_eval() && !unpack.needUnpack(i)) {
            termwise_nodes += state.tree_size();
        }
    }
    return termwise_nodes;
}

IntermediateBlueprint::IndexList
IntermediateBlueprint::find(const IPredicate & pred) const
{
    IndexList list;
    for (size_t i = 0; i < _children.size(); ++i) {
        if (pred.check(*_children[i])) {
            list.push_back(i);
        }
    }
    return list;
}

FieldSpecBaseList
IntermediateBlueprint::mixChildrenFields() const
{
    using Map = std::map<uint32_t, const FieldSpecBase*>;
    using MapVal = Map::value_type;
    using MapPos = Map::iterator;
    using MapRes = std::pair<MapPos, bool>;

    Map fieldMap;
    FieldSpecBaseList fieldList;
    for (const Blueprint::UP &child : _children) {
        const State &childState = child->getState();
        if (!childState.isTermLike()) {
            return fieldList; // empty: non-term-like child
        }
        for (size_t j = 0; j < childState.numFields(); ++j) {
            const FieldSpecBase &f = childState.field(j);
            MapRes res = fieldMap.insert(MapVal(f.getFieldId(), &f));
            if (!res.second) {
                const FieldSpecBase &other = *(res.first->second);
                if (other.getHandle() != f.getHandle()) {
                    return fieldList; // empty: conflicting children
                }
            }
        }
    }
    fieldList.reserve(fieldMap.size());
    for (const auto & entry : fieldMap) {
        fieldList.add(*entry.second);
    }
    return fieldList;
}

Blueprint::State
IntermediateBlueprint::calculateState() const
{
    State state(exposeFields());
    state.estimate(calculateEstimate());
    state.cost_tier(calculate_cost_tier());
    state.allow_termwise_eval(infer_allow_termwise_eval());
    state.tree_size(calculate_tree_size());
    return state;
}

bool
IntermediateBlueprint::should_do_termwise_eval(const UnpackInfo &unpack, double match_limit) const
{
    if (root().hit_ratio() <= match_limit) {
        return false; // global hit density too low
    }
    if (getState().allow_termwise_eval() && unpack.empty() &&
        has_parent() && getParent()->supports_termwise_children())
    {
        return false; // higher up will be better
    }
    return (count_termwise_nodes(unpack) > 1);
}

void
IntermediateBlueprint::optimize(Blueprint* &self, OptimizePass pass)
{
    assert(self == this);
    for (auto &child : _children) {
        auto *child_ptr = child.release();
        child_ptr->optimize(child_ptr, pass);
        child.reset(child_ptr);
    }
    optimize_self(pass);
    if (pass == OptimizePass::LAST) {
        update_flow_stats(get_docid_limit());
    }
    maybe_eliminate_self(self, get_replacement());
}

void
IntermediateBlueprint::sort(InFlow in_flow)
{
    resolve_strict(in_flow);
    if (!opt_keep_order()) [[likely]] {
        sort(_children, in_flow);
    }
    auto flow = my_flow(in_flow);
    for (const auto & child : _children) {
        child->sort(InFlow(flow.strict(), flow.flow()));
        flow.add(child->estimate());
    }
}

void
IntermediateBlueprint::set_global_filter(const GlobalFilter &global_filter, double estimated_hit_ratio)
{
    GlobalFilterLimits limits;  // Not used here, just checking if filter is wanted
    for (auto & child : _children) {
        if (child->want_global_filter(limits)) {
            child->set_global_filter(global_filter, estimated_hit_ratio);
        }
    }
}

void
IntermediateBlueprint::set_lazy_filter(const GlobalFilter &lazy_filter)
{
    for (auto & child : _children) {
        child->set_lazy_filter(lazy_filter);
    }
}

SearchIterator::UP
IntermediateBlueprint::createSearchImpl(fef::MatchData &md) const
{
    MultiSearch::Children subSearches;
    subSearches.reserve(_children.size());
    for (const auto & child : _children) {
        subSearches.push_back(child->createSearch(md));
    }
    return createIntermediateSearch(std::move(subSearches), md);
}

IntermediateBlueprint::IntermediateBlueprint() noexcept = default;

IntermediateBlueprint &
IntermediateBlueprint::addChild(Blueprint::UP child)
{
    child->setParent(this);
    _children.push_back(std::move(child));
    notifyChange();
    return *this;
}

Blueprint::UP
IntermediateBlueprint::removeChild(size_t n)
{
    assert(n < _children.size());
    Blueprint::UP ret = std::move(_children[n]);
    _children.erase(_children.begin() + n);
    ret->setParent(nullptr);
    notifyChange();
    return ret;
}

IntermediateBlueprint &
IntermediateBlueprint::insertChild(size_t n, Blueprint::UP child)
{
    assert(n <= _children.size());
    child->setParent(this);
    _children.insert(_children.begin() + n, std::move(child));
    notifyChange();
    return *this;
}

void
IntermediateBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    StateCache::visitMembers(visitor);
    visit(visitor, "children", _children);
}

void
IntermediateBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    auto flow = my_flow(InFlow(strict(), execInfo.hit_rate()));
    for (const auto & child : _children) {
        double nextHitRate = flow.flow();
        child->fetchPostings(ExecuteInfo::create(nextHitRate, execInfo));
        flow.add(child->estimate());
    }
}

void
IntermediateBlueprint::freeze()
{
    for (auto &child: _children) {
        child->freeze();
    }
    freeze_self();
}

void
IntermediateBlueprint::set_matching_phase(MatchingPhase matching_phase) noexcept
{
    for (auto &child : _children) {
        child->set_matching_phase(matching_phase);
    }
}

namespace {

bool
areAnyParentsEquiv(const Blueprint * node) {
    return (node != nullptr) && (node->isEquiv() || areAnyParentsEquiv(node->getParent()));
}

bool
emptyUnpackInfo(const IntermediateBlueprint * intermediate, const fef::MatchData & md) {
    return intermediate != nullptr && intermediate->calculateUnpackInfo(md).empty();
}

bool
canBlueprintSkipUnpack(const Blueprint & bp, const fef::MatchData & md) {
    if (bp.always_needs_unpack()) {
        return false;
    }
    return bp.isWhiteList() ||
           (bp.getState().numFields() != 0) ||
           emptyUnpackInfo(bp.asIntermediate(), md);
}

}

UnpackInfo
IntermediateBlueprint::calculateUnpackInfo(const fef::MatchData & md) const
{
    UnpackInfo unpackInfo;
    bool allNeedUnpack(true);
    if ( ! areAnyParentsEquiv(getParent()) ) {
        for (size_t i = 0; i < childCnt(); ++i) {
            if (may_need_unpack(i)) {
                const Blueprint & child = getChild(i);
                const State &cs = child.getState();
                bool canSkipUnpack(canBlueprintSkipUnpack(child, md));
                LOG(debug, "Child[%ld] has %ld fields. canSkipUnpack='%s'.",
                    i, cs.numFields(), canSkipUnpack ? "true" : "false");
                for (size_t j = 0; canSkipUnpack && (j < cs.numFields()); ++j) {
                    if ( ! cs.field(j).resolve(md)->isNotNeeded()) {
                        LOG(debug, "Child[%ld].field(%ld).fieldId=%d need unpack.", i, j, cs.field(j).getFieldId());
                        canSkipUnpack = false;
                    }
                }
                if ( canSkipUnpack) {
                    allNeedUnpack = false;
                } else {
                    unpackInfo.add(i);
                }
            } else {
                allNeedUnpack = false;
            }
        }
    }
    if (allNeedUnpack) {
        unpackInfo.forceAll();
    }
    LOG(spam, "UnpackInfo for %s \n is \n %s", asString().c_str(), unpackInfo.toString().c_str());
    return unpackInfo;
}


//-----------------------------------------------------------------------------

void
LeafBlueprint::fetchPostings(const ExecuteInfo &)
{
}

void
LeafBlueprint::freeze()
{
    freeze_self();
}

void
LeafBlueprint::set_matching_phase(MatchingPhase) noexcept
{
}

SearchIterator::UP
LeafBlueprint::createSearchImpl(fef::MatchData &md) const
{
    const State &state = getState();
    fef::TermFieldMatchDataArray tfmda;
    tfmda.reserve(state.numFields());
    for (size_t i = 0; i < state.numFields(); ++i) {
        tfmda.add(state.field(i).resolve(md));
    }
    return createLeafSearch(tfmda, md);
}

// default implementation
SearchIterator::UP LeafBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, fef::MatchData &) const {
    return createLeafSearch(tfmda);
}

bool
LeafBlueprint::getRange(search::NumericRangeSpec &) const {
    return false;
}

void
LeafBlueprint::optimize(Blueprint* &self, OptimizePass pass)
{
    assert(self == this);
    optimize_self(pass);
    if (pass == OptimizePass::LAST) {
        update_flow_stats(get_docid_limit());
    }
    maybe_eliminate_self(self, get_replacement());
}

void
LeafBlueprint::set_cost_tier(uint32_t value)
{
    assert(value < 0x100);
    _state.cost_tier(value);
    notifyChange();
}

void
LeafBlueprint::set_tree_size(uint32_t value)
{
    _state.tree_size(value);
    notifyChange();
}

//-----------------------------------------------------------------------------

void
SimpleLeafBlueprint::sort(InFlow in_flow)
{
    resolve_strict(in_flow);
}

}

void visit(vespalib::ObjectVisitor &self, std::string_view name,
           const search::queryeval::Blueprint *obj)
{
    if (obj != nullptr) {
        self.openStruct(name, obj->getClassName());
        obj->visitMembers(self);
        self.closeStruct();
    } else {
        self.visitNull(name);
    }
}

void visit(vespalib::ObjectVisitor &self, std::string_view name,
           const search::queryeval::Blueprint &obj)
{
    visit(self, name, &obj);
}
