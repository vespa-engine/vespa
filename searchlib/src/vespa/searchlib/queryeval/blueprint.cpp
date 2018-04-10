// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprint.h"
#include "leaf_blueprints.h"
#include "intermediate_blueprints.h"
#include "equiv_blueprint.h"
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/objects/objectdumper.h>
#include <vespa/vespalib/util/classname.h>
#include <map>

#include <vespa/log/log.h>
LOG_SETUP(".queryeval.blueprint");

namespace search::queryeval {

//-----------------------------------------------------------------------------

void maybe_eliminate_self(Blueprint* &self, Blueprint::UP replacement) {
    // replace with replacement
    if (replacement.get() != nullptr) {
        Blueprint *tmp = replacement.release();
        tmp->setParent(self->getParent());
        tmp->setSourceId(self->getSourceId());
        self->setParent(0);
        replacement.reset(self);
        self = tmp;
    }
    // replace with empty blueprint if empty
    if (self->getState().estimate().empty) {
        Blueprint::UP discard(self);
        self = new EmptyBlueprint(discard->getState().fields());
        self->setParent(discard->getParent());
        self->setSourceId(discard->getSourceId());
    }
}

//-----------------------------------------------------------------------------

Blueprint::HitEstimate
Blueprint::max(const std::vector<HitEstimate> &data)
{
    HitEstimate est;
    for (size_t i = 0; i < data.size(); ++i) {
        if (est.empty || est.estHits < data[i].estHits) {
            est = data[i];
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

Blueprint::State::State(const FieldSpecBaseList &fields_in)
    : _fields(fields_in),
      _estimate(),
      _tree_size(1),
      _allow_termwise_eval(true)
{
}

Blueprint::State::~State() { }

Blueprint::Blueprint()
    : _parent(0),
      _sourceId(0xffffffff),
      _docid_limit(0),
      _frozen(false)
{
}

Blueprint::~Blueprint()
{
}

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

const Blueprint &
Blueprint::root() const
{
    const Blueprint *bp = this;
    while (bp->_parent != nullptr) {
        bp = bp->_parent;
    }
    return *bp;
}

vespalib::string
Blueprint::asString() const
{
    vespalib::ObjectDumper dumper;
    visit(dumper, "", this);
    return dumper.toString();
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
            // visitor.visitString("name", spec.getName());
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
    visitor.visitInt("tree_size", state.tree_size());
    visitor.visitInt("allow_termwise_eval", state.allow_termwise_eval());
    visitor.closeStruct();
    visitor.visitInt("sourceId", _sourceId);
    visitor.visitInt("docid_limit", _docid_limit);
}

namespace blueprint {

//-----------------------------------------------------------------------------

void
StateCache::updateState() const
{
    calculateState().swap(_state);
    _stale = false;
}

} // namespace blueprint

//-----------------------------------------------------------------------------

IntermediateBlueprint::~IntermediateBlueprint()
{
    while (!_children.empty()) {
        delete _children.back();
        _children.pop_back();
    }
}

void
IntermediateBlueprint::setDocIdLimit(uint32_t limit)
{
    Blueprint::setDocIdLimit(limit);
    for (size_t i = 0; i < _children.size(); ++i) {
        _children[i]->setDocIdLimit(limit);
    }
}

Blueprint::HitEstimate
IntermediateBlueprint::calculateEstimate() const
{
    std::vector<HitEstimate> estimates;
    estimates.reserve(_children.size());
    for (size_t i = 0; i < _children.size(); ++i) {
        estimates.push_back(_children[i]->getState().estimate());
    }
    return combine(estimates);
}

uint32_t
IntermediateBlueprint::calculate_tree_size() const
{
    uint32_t nodes = 1;
    for (size_t i = 0; i < _children.size(); ++i) {
        nodes += _children[i]->getState().tree_size();
    }
    return nodes;
}

bool
IntermediateBlueprint::infer_allow_termwise_eval() const
{
    if (!supports_termwise_children()) {
        return false;
    }
    for (size_t i = 0; i < _children.size(); ++i) {
        if (!_children[i]->getState().allow_termwise_eval()) {
            return false;
        }
    }
    return true;
};

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
    for (size_t i = 0; i < _children.size(); ++i) {
        const State &childState = _children[i]->getState();
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
    for (MapPos pos = fieldMap.begin(); pos != fieldMap.end(); ++pos) {
        fieldList.add(*(pos->second));
    }
    return fieldList;
}

Blueprint::State
IntermediateBlueprint::calculateState() const
{
    State state(exposeFields());
    state.estimate(calculateEstimate());
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
IntermediateBlueprint::optimize(Blueprint* &self)
{
    assert(self == this);
    if (should_optimize_children()) {
        for (size_t i = 0; i < _children.size(); ++i) {
            _children[i]->optimize(_children[i]);
        }
    }
    optimize_self();
    sort(_children);
    maybe_eliminate_self(self, get_replacement());
}

SearchIterator::UP
IntermediateBlueprint::createSearch(fef::MatchData &md, bool strict) const
{
    MultiSearch::Children subSearches;
    subSearches.reserve(_children.size());
    for (size_t i = 0; i < _children.size(); ++i) {
        bool strictChild = (strict && inheritStrict(i));
        SearchIterator::UP search = _children[i]->createSearch(md, strictChild);
        subSearches.push_back(search.release());
    }
    return createIntermediateSearch(subSearches, strict, md);
}

IntermediateBlueprint::IntermediateBlueprint()
    : _children()
{
}

const Blueprint &
IntermediateBlueprint::getChild(size_t n) const
{
    assert(n < _children.size());
    return *_children[n];
}

Blueprint &
IntermediateBlueprint::getChild(size_t n)
{
    assert(n < _children.size());
    return *_children[n];
}

IntermediateBlueprint &
IntermediateBlueprint::addChild(Blueprint::UP child)
{
    _children.push_back(child.get());
    child.release()->setParent(this);
    notifyChange();
    return *this;
}

Blueprint::UP
IntermediateBlueprint::removeChild(size_t n)
{
    assert(n < _children.size());
    Blueprint::UP ret(_children[n]);
    _children.erase(_children.begin() + n);
    ret->setParent(0);
    notifyChange();
    return ret;
}

IntermediateBlueprint &
IntermediateBlueprint::insertChild(size_t n, Blueprint::UP child)
{
    assert(n <= _children.size());
    _children.insert(_children.begin() + n, child.get());
    child.release()->setParent(this);
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
IntermediateBlueprint::fetchPostings(bool strict)
{
    for (size_t i = 0; i < _children.size(); ++i) {
        bool strictChild = (strict && inheritStrict(i));
        _children[i]->fetchPostings(strictChild);
    }
}

void
IntermediateBlueprint::freeze()
{
    for (size_t i = 0; i < _children.size(); ++i) {
        _children[i]->freeze();
    }
    freeze_self();
}

namespace {

bool
areAnyParentsEquiv(const Blueprint * node)
{
    return (node == NULL)
           ? false
           : node->isEquiv()
             ? true
             : areAnyParentsEquiv(node->getParent());
}

bool
canBlueprintSkipUnpack(const Blueprint & bp, const fef::MatchData & md)
{
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
                LOG(debug, "Child[%ld] has %ld fields. canSkipUnpack='%s'.", i, cs.numFields(), canSkipUnpack ? "true" : "false");
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

LeafBlueprint::~LeafBlueprint() { }

void
LeafBlueprint::fetchPostings(bool strict)
{
    (void) strict;
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
LeafBlueprint::set_allow_termwise_eval(bool value)
{
    _state.allow_termwise_eval(value);
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
