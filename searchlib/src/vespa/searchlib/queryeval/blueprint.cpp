// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprint.h"
#include "leaf_blueprints.h"
#include "emptysearch.h"
#include "full_search.h"
#include "field_spec.hpp"
#include "andsearch.h"
#include "orsearch.h"
#include "andnotsearch.h"
#include "matching_elements_search.h"
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
      _want_global_filter(false),
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
      _want_global_filter(false),
      _cost_tier(COST_TIER_NORMAL)
{
}

Blueprint::State::~State() = default;

Blueprint::Blueprint() noexcept
    : _parent(nullptr),
      _flow_stats(0.0, 0.0, 0.0),
      _sourceId(0xffffffff),
      _docid_limit(0),
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

double
Blueprint::estimate_actual_cost(InFlow in_flow) const noexcept
{
    double res = estimate_strict_cost_diff(in_flow);
    if (in_flow.strict()) {
        res += strict_cost();
    } else {
        res += in_flow.rate() * cost();
    }
    return res;
}

double
Blueprint::estimate_strict_cost_diff(InFlow &in_flow) const noexcept
{
    if (in_flow.strict()) {
        REQUIRE(strict());
    } else if (strict()) {
        double rate = in_flow.rate();
        in_flow.force_strict();
        return flow::strict_cost_diff(estimate(), rate);
    }
    return 0.0;
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
    return {0.5, 1.0 + child_cnt, 1.0 + child_cnt};
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

template <typename Op> bool should_prune(Trinary, bool, bool);
template <> bool should_prune<AndSearch>(Trinary matches_any, bool strict, bool first_child) {
    return (matches_any == Trinary::True) && !(strict && first_child);
}
template <> bool should_prune<OrSearch>(Trinary matches_any, bool, bool) { return (matches_any == Trinary::False); }

template <typename Op>
std::unique_ptr<SearchIterator>
create_op_filter(const Blueprint::Children &children, bool strict, Blueprint::FilterConstraint constraint)
{
    REQUIRE( ! children.empty());
    MultiSearch::Children list;
    std::unique_ptr<SearchIterator> spare;
    list.reserve(children.size());
    for (size_t i = 0; i < children.size(); ++i) {
        auto filter = children[i]->createFilterSearch(constraint);
        auto matches_any = filter->matches_any();
        if (should_short_circuit<Op>(matches_any)) {
            return filter;
        }
        if (should_prune<Op>(matches_any, strict, list.empty())) {
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
    return Op::create(std::move(list), strict, unpack_info);
}

}

std::unique_ptr<SearchIterator>
Blueprint::create_and_filter(const Children &children, bool strict, Blueprint::FilterConstraint constraint)
{
    return create_op_filter<AndSearch>(children, strict, constraint);
}

std::unique_ptr<SearchIterator>
Blueprint::create_or_filter(const Children &children, bool strict, Blueprint::FilterConstraint constraint)
{
    return create_op_filter<OrSearch>(children, strict, constraint);
}

std::unique_ptr<SearchIterator>
Blueprint::create_atmost_and_filter(const Children &children, bool strict, Blueprint::FilterConstraint constraint)
{
    if (constraint == FilterConstraint::UPPER_BOUND) {
        return create_and_filter(children, strict, constraint);
    } else {
        return std::make_unique<EmptySearch>();
    }
}

std::unique_ptr<SearchIterator>
Blueprint::create_atmost_or_filter(const Children &children, bool strict, Blueprint::FilterConstraint constraint)
{
    if (constraint == FilterConstraint::UPPER_BOUND) {
        return create_or_filter(children, strict, constraint);
    } else {
        return std::make_unique<EmptySearch>();
    }
}

std::unique_ptr<SearchIterator>
Blueprint::create_andnot_filter(const Children &children, bool strict, Blueprint::FilterConstraint constraint)
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
    return AndNotSearch::create(std::move(list), strict);
}

std::unique_ptr<SearchIterator>
Blueprint::create_first_child_filter(const Children &children, Blueprint::FilterConstraint constraint)
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

vespalib::string
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

vespalib::string
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

void
IntermediateBlueprint::each_node_post_order(const std::function<void(Blueprint&)> &f)
{
    for (Blueprint::UP &child : _children) {
        f(*child);
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
IntermediateBlueprint::infer_want_global_filter() const
{
    for (const Blueprint::UP &child : _children) {
        if (child->getState().want_global_filter()) {
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
    state.want_global_filter(infer_want_global_filter());
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

double
IntermediateBlueprint::estimate_self_cost(InFlow) const noexcept
{
    return 0.0;
}

double
IntermediateBlueprint::estimate_actual_cost(InFlow in_flow) const noexcept
{
    double res = estimate_strict_cost_diff(in_flow);
    auto cost_of = [](const auto &child, InFlow child_flow)noexcept{
                       return child->estimate_actual_cost(child_flow);
                   };
    res += flow::actual_cost_of(flow::DefaultAdapter(), _children, my_flow(in_flow), cost_of);
    res += estimate_self_cost(in_flow);
    return res;
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
    for (size_t i = 0; i < _children.size(); ++i) {
        _children[i]->sort(InFlow(flow.strict(), flow.flow()));
        flow.add(_children[i]->estimate());
    }
}

void
IntermediateBlueprint::set_global_filter(const GlobalFilter &global_filter, double estimated_hit_ratio)
{
    for (auto & child : _children) {
        if (child->getState().want_global_filter()) {
            child->set_global_filter(global_filter, estimated_hit_ratio);
        }
    }
}

SearchIterator::UP
IntermediateBlueprint::createSearch(fef::MatchData &md) const
{
    MultiSearch::Children subSearches;
    subSearches.reserve(_children.size());
    for (size_t i = 0; i < _children.size(); ++i) {
        subSearches.push_back(_children[i]->createSearch(md));
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
    for (size_t i = 0; i < _children.size(); ++i) {
        double nextHitRate = flow.flow();
        Blueprint & child = *_children[i];
        child.fetchPostings(ExecuteInfo::create(nextHitRate, execInfo));
        flow.add(child.estimate());
    }
}

void
IntermediateBlueprint::freeze()
{
    for (Blueprint::UP &child: _children) {
        child->freeze();
    }
    freeze_self();
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
            if (isPositive(i)) {
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

SearchIterator::UP
LeafBlueprint::createSearch(fef::MatchData &md) const
{
    const State &state = getState();
    fef::TermFieldMatchDataArray tfmda;
    tfmda.reserve(state.numFields());
    for (size_t i = 0; i < state.numFields(); ++i) {
        tfmda.add(state.field(i).resolve(md));
    }
    return createLeafSearch(tfmda);
}

bool
LeafBlueprint::getRange(vespalib::string &, vespalib::string &) const {
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
LeafBlueprint::set_want_global_filter(bool value)
{
    _state.want_global_filter(value);
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

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
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

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::Blueprint &obj)
{
    visit(self, name, &obj);
}
