// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprint.h"
#include "leaf_blueprints.h"
#include "intermediate_blueprints.h"
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
#include <type_traits>
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
    // replace with empty blueprint if empty
    if (self->getState().estimate().empty) {
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

Blueprint::State::State(const FieldSpecBaseList &fields_in)
    : _fields(fields_in),
      _estimate(),
      _cost_tier(COST_TIER_NORMAL),
      _tree_size(1),
      _allow_termwise_eval(true),
      _want_global_filter(false)
{
}

Blueprint::State::~State() = default;

Blueprint::Blueprint()
    : _parent(0),
      _sourceId(0xffffffff),
      _docid_limit(0),
      _frozen(false)
{
}

Blueprint::~Blueprint() = default;

Blueprint::UP
Blueprint::optimize(Blueprint::UP bp) {
    Blueprint *root = bp.release();
    root->optimize(root);
    return Blueprint::UP(root);
}

void
Blueprint::optimize_self()
{
}

Blueprint::UP
Blueprint::get_replacement()
{
    return Blueprint::UP();
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

std::unique_ptr<MatchingElementsSearch>
Blueprint::create_matching_elements_search(const MatchingElementsFields &fields) const
{
    (void) fields;
    return std::unique_ptr<MatchingElementsSearch>();
};

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

template <typename Op> bool inherit_strict(size_t);
template <> bool inherit_strict<AndSearch>(size_t i) { return (i == 0); }
template <> bool inherit_strict<OrSearch>(size_t) { return true; }

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
    REQUIRE(children.size() > 0);
    MultiSearch::Children list;
    std::unique_ptr<SearchIterator> spare;
    list.reserve(children.size());
    for (size_t i = 0; i < children.size(); ++i) {
        auto strict_child = strict && inherit_strict<Op>(i);
        auto filter = children[i]->createFilterSearch(strict_child, constraint);
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
    REQUIRE(children.size() > 0);
    MultiSearch::Children list;
    list.reserve(children.size());
    {
        auto filter = children[0]->createFilterSearch(strict, constraint);
        if (filter->matches_any() == Trinary::False) {
            return filter;
        }
        list.push_back(std::move(filter));
    }
    for (size_t i = 1; i < children.size(); ++i) {
        auto filter = children[i]->createFilterSearch(false, invert(constraint));
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
Blueprint::create_first_child_filter(const Children &children, bool strict, Blueprint::FilterConstraint constraint)
{
    REQUIRE(children.size() > 0);
    return children[0]->createFilterSearch(strict, constraint);
}

std::unique_ptr<SearchIterator>
Blueprint::create_default_filter(bool, FilterConstraint constraint)
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
    visitor.visitInt("sourceId", _sourceId);
    visitor.visitInt("docid_limit", _docid_limit);
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
    Blueprint::notifyChange();
    _stale = true;
}

} // namespace blueprint

//-----------------------------------------------------------------------------

IntermediateBlueprint::~IntermediateBlueprint() = default;

void
IntermediateBlueprint::setDocIdLimit(uint32_t limit)
{
    Blueprint::setDocIdLimit(limit);
    for (Blueprint::UP &child : _children) {
        child->setDocIdLimit(limit);
    }
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

uint32_t
IntermediateBlueprint::calculate_cost_tier() const
{
    uint32_t cost_tier = State::COST_TIER_MAX;
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
};

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
    typedef std::map<uint32_t, const FieldSpecBase*> Map;
    typedef Map::value_type                      MapVal;
    typedef Map::iterator                        MapPos;
    typedef std::pair<MapPos, bool>              MapRes;

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

double
IntermediateBlueprint::computeNextHitRate(const Blueprint & child, double hitRate) const
{
    (void) child;
    return hitRate;
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
IntermediateBlueprint::optimize(Blueprint* &self)
{
    assert(self == this);
    if (should_optimize_children()) {
        for (auto &child : _children) {
            auto *child_ptr = child.release();
            child_ptr->optimize(child_ptr);
            child.reset(child_ptr);
        }
    }
    optimize_self();
    sort(_children);
    maybe_eliminate_self(self, get_replacement());
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
IntermediateBlueprint::createSearch(fef::MatchData &md, bool strict) const
{
    MultiSearch::Children subSearches;
    subSearches.reserve(_children.size());
    for (size_t i = 0; i < _children.size(); ++i) {
        bool strictChild = (strict && inheritStrict(i));
        subSearches.push_back(_children[i]->createSearch(md, strictChild));
    }
    return createIntermediateSearch(std::move(subSearches), strict, md);
}

IntermediateBlueprint::IntermediateBlueprint() = default;

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
    double nextHitRate = execInfo.hitRate();
    for (size_t i = 0; i < _children.size(); ++i) {
        Blueprint & child = *_children[i];
        child.fetchPostings(ExecuteInfo::create(execInfo.isStrict() && inheritStrict(i), nextHitRate));
        nextHitRate = computeNextHitRate(child, nextHitRate);
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
areAnyParentsEquiv(const Blueprint * node)
{
    return (node == nullptr)
           ? false
           : node->isEquiv()
             ? true
             : areAnyParentsEquiv(node->getParent());
}

bool
canBlueprintSkipUnpack(const Blueprint & bp, const fef::MatchData & md)
{
    if (bp.always_needs_unpack()) {
        return false;
    }
    return (bp.isWhiteList() ||
            (bp.getState().numFields() != 0) ||
            (bp.isIntermediate() &&
             static_cast<const IntermediateBlueprint &>(bp).calculateUnpackInfo(md).empty()));
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

LeafBlueprint::LeafBlueprint(const FieldSpecBaseList &fields, bool allow_termwise_eval)
    : _state(fields)
{
    _state.allow_termwise_eval(allow_termwise_eval);
}

LeafBlueprint::~LeafBlueprint() = default;

void
LeafBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    (void) execInfo;
}

void
LeafBlueprint::freeze()
{
    freeze_self();
}

SearchIterator::UP
LeafBlueprint::createSearch(fef::MatchData &md, bool strict) const
{
    const State &state = getState();
    fef::TermFieldMatchDataArray tfmda;
    tfmda.reserve(state.numFields());
    for (size_t i = 0; i < state.numFields(); ++i) {
        tfmda.add(state.field(i).resolve(md));
    }
    return createLeafSearch(tfmda, strict);
}

bool
LeafBlueprint::getRange(vespalib::string &, vespalib::string &) const {
    return false;
}

void
LeafBlueprint::optimize(Blueprint* &self)
{
    assert(self == this);
    optimize_self();
    maybe_eliminate_self(self, get_replacement());
}

void
LeafBlueprint::setEstimate(HitEstimate est)
{
    _state.estimate(est);
    notifyChange();
}

void
LeafBlueprint::set_cost_tier(uint32_t value)
{
    _state.cost_tier(value);
    notifyChange();
}

void
LeafBlueprint::set_allow_termwise_eval(bool value)
{
    _state.allow_termwise_eval(value);
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

}

//-----------------------------------------------------------------------------

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::Blueprint *obj)
{
    if (obj != 0) {
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
