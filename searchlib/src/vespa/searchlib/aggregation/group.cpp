// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "group.h"
#include "grouping.h"
#include <vespa/searchlib/expression/aggregationrefnode.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/objects/object2slime.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <cassert>

namespace search::aggregation {

using search::expression::AggregationRefNode;
using search::expression::ExpressionTree;
using vespalib::Serializer;
using vespalib::Deserializer;

namespace {

struct SortByGroupId {
    bool operator()(const Group::ChildP & a, const Group::ChildP & b) {
        return (a->cmpId(*b) < 0);
    }
};

struct SortByGroupRank {
    bool operator()(const Group::ChildP & a, const Group::ChildP & b) {
        return (a->cmpRank(*b) < 0);
    }
};

void reset(Group * & v) { v = nullptr; }
void destruct(Group * v) { delete v; }

void
destruct(Group::GroupList & l, size_t m)
{
    for (size_t i(0); i < m; i++) {
        destruct(l[i]);
    }
    delete [] l;
    l = nullptr;
}

}

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, Group, vespalib::Identifiable);

int
Group::cmpRank(const Group &rhs) const
{
    int diff(_aggr.cmp(rhs._aggr));
    return diff
               ? diff
               : ((_rank > rhs._rank)
                   ? -1
                   : ((_rank < rhs._rank) ? 1 : 0));
}

void
Group::selectMembers(const vespalib::ObjectPredicate &predicate, vespalib::ObjectOperation &operation) {
    if (_id.get()) {
        _id->select(predicate, operation);
    }
    _aggr.select(predicate, operation);
}

template <typename Doc>
void
Group::aggregate(const Grouping & grouping, uint32_t currentLevel, const Doc & doc, HitRank rank)
{
    if (currentLevel >= grouping.getFirstLevel()) {
        collect(doc, rank);
    }
    if (currentLevel < grouping.getLevels().size()) {
        groupNext(grouping.getLevels()[currentLevel], doc, rank);
    }
}

template <typename Doc>
void
Group::groupNext(const GroupingLevel & level, const Doc & doc, HitRank rank)
{
    const ExpressionTree &selector = level.getExpression();
    if (!selector.execute(doc, rank)) {
        throw std::runtime_error("Does not know how to handle failed select statements");
    }
    const ResultNode &selectResult = *selector.getResult();
    level.group(*this, selectResult, doc, rank);
}

Group *
Group::Value::groupSingle(const ResultNode & selectResult, HitRank rank, const GroupingLevel & level)
{
    if (_childInfo._childMap == nullptr) {
        assert(getChildrenSize() == 0);
        _childInfo._childMap = new GroupHash(1, GroupHasher(&_children), GroupEqual(&_children));
    }
    GroupHash & childMap = *_childInfo._childMap;
    Group * group(nullptr);
    auto found = childMap.find(selectResult);
    if (found == childMap.end()) { // group not present in child map
        if (level.allowMoreGroups(childMap.size())) {
            group = new Group(level.getGroupPrototype());
            group->setId(selectResult);
            group->setRank(rank);
            addChild(group);
            childMap.insert(getChildrenSize() - 1);
        }
    } else {
        uint32_t idx = *found;
        group = _children[idx];
        if ( ! level.isFrozen()) {
            group->updateRank(rank);
        }
    }
    return group;
}

void
Group::merge(const GroupingLevelList &levels, uint32_t firstLevel, uint32_t currentLevel, Group &b) {
    bool frozen = (currentLevel < firstLevel);    // is this level frozen ?
    _rank = std::max(_rank, b._rank);

    if (!frozen) { // should we merge collectors for this level ?
        _aggr.mergeCollectors(b._aggr);
    }
    _aggr.merge(levels, firstLevel, currentLevel, b._aggr);
}

void
Group::prune(const Group & b, uint32_t lastLevel, uint32_t currentLevel) {
    if (currentLevel >= lastLevel) {
        return;
    }
    _aggr.prune(b._aggr, lastLevel, currentLevel);
}

void
Group::mergePartial(const GroupingLevelList &levels, uint32_t firstLevel, uint32_t lastLevel,
                    uint32_t currentLevel, const Group & b) {
    bool frozen = (currentLevel < firstLevel);

    if (!frozen) {
        _aggr.mergeCollectors(b._aggr);
        // At this level, we must create a copy of the other nodes children.
        if (currentLevel >= lastLevel) {
            _aggr.mergeLevel(levels[currentLevel].getGroupPrototype(), b._aggr);
            return;
        }
    }
    _aggr.mergePartial(levels, firstLevel, lastLevel, currentLevel, b._aggr);
}

Group &
Group::setRank(RawRank r)
{
    _rank = std::isnan(r) ? -HUGE_VAL : r;
    return *this;
}

Group &
Group::updateRank(RawRank r)
{
    return setRank(std::max(_rank, r));
}

Serializer &
Group::onSerialize(Serializer & os) const {
    _aggr.validate_id_order();
    os << _id << _rank;
    _aggr.serialize(os);
    return os;
}

Deserializer &
Group::onDeserialize(Deserializer & is) {
    is >> _id >> _rank;
    _aggr.deserialize(is);
    _aggr.validate_id_order();
    return is;
}

void
Group::visitMembers(vespalib::ObjectVisitor &visitor) const {
    visit(visitor, "id", _id);
    visit(visitor, "rank", _rank);
    _aggr.visitMembers(visitor);
}

Group::Group() noexcept
    : _id(),
      _rank(0),
      _aggr()
{ }

Group::Group(const Group & rhs) = default;
Group & Group::operator = (const Group & rhs) = default;

Group::~Group() = default;

Group &
Group::partialCopy(const Group & rhs) {
    setId(*rhs._id);
    _rank = rhs._rank;
    _aggr.partialCopy(rhs._aggr);
    return *this;
}

template void Group::aggregate(const Grouping & grouping, uint32_t currentLevel, const DocId & doc, HitRank rank);
template void Group::aggregate(const Grouping & grouping, uint32_t currentLevel, const document::Document & doc, HitRank rank);

int
Group::Value::cmp(const Value & rhs) const {
    int diff(0);
    for (size_t i(0), m(getOrderBySize()); (diff == 0) && (i < m); i++) {
        uint32_t index = std::abs(getOrderBy(i)) - 1;
        diff = expr(index).getResult()->cmp(*rhs.expr(index).getResult()) * getOrderBy(i);
    }
    return diff;
}

void
Group::Value::addExpressionResult(ExpressionNode::UP expressionNode)
{
    uint32_t newSize = getAggrSize() + getExprSize() + 1;
    auto n = new ExpressionNode::CP[newSize];
    for (uint32_t i(0); i < (newSize - 1); i++) {
        n[i] = std::move(_aggregationResults[i]);
    }
    n[newSize - 1].reset(expressionNode.release());
    delete [] _aggregationResults;
    _aggregationResults = n;
    setExprSize(getExprSize()+1);
    setupAggregationReferences();
}

void
Group::Value::addAggregationResult(ExpressionNode::UP aggr)
{
    size_t newSize = getAggrSize() + 1 + getExprSize();
    auto n = new ExpressionNode::CP[newSize];
    for (size_t i(0), m(getAggrSize()); i < m; i++) {
        n[i] = std::move(_aggregationResults[i]);
    }
    n[getAggrSize()].reset(aggr.release());
    // Copy expressions after aggregationresults
    for (size_t i(getAggrSize()); i < newSize - 1; i++) {
        n[i + 1] = std::move(_aggregationResults[i]);
    }
    delete [] _aggregationResults;
    _aggregationResults = n;
    setAggrSize(getAggrSize() + 1);
}

void
Group::Value::setAggrSize(uint32_t v) {
    assert(v < 0x10000);
    _packedLength = (_packedLength & ~0xffff) | v;
}

void
Group::Value::setExprSize(uint32_t v) {
    assert(v < sizeof(_orderBy)*2);
    _packedLength = (_packedLength & ~0xf0000) | (v << 16);
}

void
Group::Value::setOrderBySize(uint32_t v) {
    assert(v < sizeof(_orderBy)*2);
    _packedLength = (_packedLength & ~0xf00000) | (v << 20);
}

template <typename Doc>
void Group::Value::collect(const Doc & doc, HitRank rank)
{
    for(size_t i(0), m(getAggrSize()); i < m; i++) {
        getAggr(i)->aggregate(doc, rank);
    }
}

void
Group::Value::addResult(ExpressionNode::UP aggr)
{
    assert(getExprSize() < 15);
    addAggregationResult(std::move(aggr));
    addExpressionResult(std::make_unique<AggregationRefNode>(getAggrSize() - 1));
    setupAggregationReferences();
}

void
Group::Value::addOrderBy(ExpressionNode::UP orderBy, bool ascending)
{
    addExpressionResult(std::move(orderBy));
    setOrderBy(getOrderBySize(), (ascending ? getExprSize() : -getExprSize()));
    setOrderBySize(getOrderBySize() + 1);
}

void
Group::Value::addChild(Group * child)
{
    const size_t sz(getChildrenSize());
    if (_children == nullptr) {
        _children = new ChildP[4];
    } else if ((sz >=4) && vespalib::Optimized::msbIdx(sz) == vespalib::Optimized::lsbIdx(sz)) {
        auto n = new ChildP[sz*2];
        for (size_t i(0), m(getChildrenSize()); i < m; i++) {
            n[i] = _children[i];
        }
        delete [] _children;
        _children = n;
    }
    _children[sz] = child;
    setChildrenSize(sz + 1);
}

void
Group::Value::select(const vespalib::ObjectPredicate &predicate, vespalib::ObjectOperation &operation) {
    uint32_t totalSize = getAggrSize() + getExprSize();
    for (uint32_t i(0); i < totalSize; i++) {
        _aggregationResults[i]->select(predicate, operation);
    }
}

void
Group::Value::preAggregate()
{
    assert(_childInfo._childMap == nullptr);
    _childInfo._childMap = new GroupHash(getChildrenSize()*2, GroupHasher(&_children), GroupEqual(&_children));
    GroupHash & childMap = *_childInfo._childMap;
    size_t i = 0;
    for (ChildP cp : iterateChildren()) {
        cp->preAggregate();
        childMap.insert(i++);
    }
}

void
Group::Value::postAggregate()
{
    delete _childInfo._childMap;
    _childInfo._childMap = nullptr;
    for (ChildP cp : iterateChildren()) {
        cp->postAggregate();
    }
}

void
Group::Value::executeOrderBy()
{
    for (size_t i(0), m(getExprSize()); i < m; i++) {
        ExpressionNode & e(expr(i));
        e.prepare(false); // TODO: What should we do about this flag?
        e.execute();
    }
}

void
Group::Value::sortById()
{
    std::sort(_children, _children + getChildrenSize(), SortByGroupId());
    for (ChildP cp : iterateChildren()) {
        cp->sortById();
    }
}

void
Group::Value::mergeCollectors(const Value &rhs) {
    for(size_t i(0), m(getAggrSize()); i < m; i++) {
        getAggr(i)->merge(rhs.getAggr(i));
    }
}

void
Group::Value::mergeLevel(const Group & protoType, const Value & b) {
    for (ChildP cp : b.iterateChildren()) {
        auto g(new Group(protoType));
        g->partialCopy(*cp);
        addChild(g);
    }
}

void
Group::Value::merge(const std::vector<GroupingLevel> &levels,
                    uint32_t firstLevel, uint32_t currentLevel, const Value &b)
{
    auto z = new ChildP[getChildrenSize() + b.getChildrenSize()];
    size_t kept(0);
    auto mine = iterateChildren();
    auto others = b.iterateChildren();
    ChildP * px = mine.begin();
    ChildP * ex = mine.end();
    ChildP * py = others.begin();
    ChildP * ey = others.end();
    while (px != ex && py != ey) {
        int c = (*px)->cmpId(**py);
        if (c == 0) {
            (*px)->merge(levels, firstLevel, currentLevel + 1, **py);
            z[kept++] = *px;
            reset(*px);
            ++px;
            ++py;
        } else if (c < 0) {
            z[kept++] = *px;
            reset(*px);
            ++px;
        } else {
            z[kept++] = *py;
            reset(*py);
            ++py;
        }
    }
    for (; px != ex; ++px) {
        z[kept++] = *px;
        reset(*px);
    }
    for (; py != ey; ++py) {
        z[kept++] = *py;
        reset(*py);
    }
    std::swap(_children, z);
    destruct(z, getAllChildrenSize());
    setChildrenSize(kept);
    clearAllChildrenSize();
}

void
Group::Value::prune(const Value & b, uint32_t lastLevel, uint32_t currentLevel) {
    auto keep = new ChildP[b.getChildrenSize()];
    size_t kept(0);
    auto mine = iterateAllChildren();
    auto others = b.iterateChildren();
    ChildP * px = mine.begin();
    ChildP * ex = mine.end();
    const ChildP * py = others.begin();
    const ChildP * ey = others.end();
    // Assumes that both lists are ordered by group id
    while (py != ey && px != ex) {
        if ((*py)->cmpId(**px) > 0) {
            px++;
        } else if ((*py)->cmpId(**px) == 0) {
            keep[kept++] = (*px);
            (*px)->prune((**py), lastLevel, currentLevel + 1);
            reset(*px);
            px++;
            py++;
        } else if ((*py)->cmpId(**px) < 0) {
            py++;
        }
    }
    std::swap(_children, keep);
    destruct(keep, getAllChildrenSize());
    setChildrenSize(kept);
    clearAllChildrenSize();
}

void
Group::Value::mergePartial(const GroupingLevelList &levels, uint32_t firstLevel, uint32_t lastLevel,
                           uint32_t currentLevel, const Value & b)
{
    auto mine = iterateChildren();
    auto others = b.iterateChildren();
    ChildP * px = mine.begin();
    ChildP * ex = mine.end();
    const ChildP * py = others.begin();
    const ChildP * ey = others.end();
    // Assumes that both lists are ordered by group id
    while (py != ey && px != ex) {
        if ((*py)->cmpId(**px) > 0) {
            px++;
        } else if ((*py)->cmpId(**px) == 0) {
            (*px)->mergePartial(levels, firstLevel, lastLevel, currentLevel + 1, **py);
            px++;
            py++;
        } else if ((*py)->cmpId(**px) < 0) {
            py++;
        }
    }
}

void
Group::Value::postMerge(const std::vector<GroupingLevel> &levels, uint32_t firstLevel, uint32_t currentLevel)
{
    bool frozen = (currentLevel < firstLevel);    // is this level frozen ?

    if (!frozen) {
        for(size_t i(0), m(getAggrSize()); i < m; i++) {
            getAggr(i)->postMerge();
        }
    }
    bool hasNext = (currentLevel < levels.size()); // is there a next level ?
    if (!hasNext) { // we have reached the bottom of the tree
        return;
    }
    for (ChildP cp : iterateChildren()) {
        cp->executeOrderBy();
    }
    int64_t maxGroups = levels[currentLevel].getPrecision();
    for (size_t i(getChildrenSize()); i < getAllChildrenSize(); i++) {
        destruct(_children[i]);
        reset(_children[i]);
    }
    setAllChildrenSize(getChildrenSize());
    if (getChildrenSize() > (uint64_t)maxGroups) { // prune groups
        std::sort(_children, _children + getChildrenSize(), SortByGroupRank());
        setChildrenSize(maxGroups);
    }
    for (ChildP cp : iterateChildren()) {
        cp->postMerge(levels, firstLevel, currentLevel + 1);
    }
}

bool
Group::Value::needResort() const
{
    bool resort(needFullRank());
    for (const ChildP *it(_children), *mt(_children + getChildrenSize()); !resort && (it != mt); ++it) {
        resort = (*it)->needResort();
    }
    return resort;
}

namespace {

std::string child_id_as_string(size_t i, Group* group) {
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    vespalib::slime::Cursor & cursor = inserter.insertObject();
    vespalib::Object2Slime dumper(cursor);
    std::string name = "child[" + std::to_string(i) + "].id";
    const expression::ResultNode* id = nullptr;
    if (group != nullptr && group->hasId()) {
        id = &group->getId();
    }
    visit(dumper, name, id);
    vespalib::SimpleBuffer buf;
    vespalib::slime::JsonFormat::encode(slime, buf, true);
    return buf.get().make_string();
}

}

void
Group::Value::validate_id_order() const {
    if (getChildrenSize() > 1) {
        for (size_t i(1), m(getChildrenSize()); i < m; i++) {
            if (!(_children[i]->cmpId(*_children[i-1]) > 0)) {
                auto msg = vespalib::make_string("Group::Value::validate_id_order: Expected %s > %s, %u children",
                    child_id_as_string(i, _children[i]).c_str(),
                    child_id_as_string(i - 1, _children[i - 1]).c_str(),
                    getChildrenSize());
                throw vespalib::IllegalArgumentException(msg);
            }
        }
    }
}

Serializer &
Group::Value::serialize(Serializer & os) const {

    os << uint32_t(getOrderBySize());
    for (size_t i(0), m(getOrderBySize()); i < m; i++) {
        os << int32_t(getOrderBy(i));
    }
    os << uint32_t(getAggrSize());
    for(size_t i(0), m(getAggrSize()); i < m; i++) {
        os << getAggrCP(i);
    }
    os << uint32_t(getExprSize());
    for(size_t i(0), m(getExprSize()); i < m; i++) {
        os << getExprCP(i);
    }
    os << uint32_t(getChildrenSize());
    for (size_t i(0), m(getChildrenSize()); i < m; i++) {
        os << *_children[i];
    }
    return os << _tag;
}

Deserializer &
Group::Value::deserialize(Deserializer & is) {
    uint32_t count(0);
    is >> count;
    assert(count < sizeof(_orderBy)*2);
    setOrderBySize(count);
    for(uint32_t i(0); i < count; i++) {
        int32_t tmp(0);
        is >> tmp;
        assert((-7<= tmp) && (tmp <= 7));
        setOrderBy(i, tmp);
    }
    uint32_t aggrSize(0);
    is >> aggrSize;
    // To avoid protocol changes, we must first deserialize the aggregation
    // results into a temporary buffer, and then reallocate the actual
    // vector when we know the total size. Then we copy the temp buffer and
    // deserialize the rest to the end of the vector.
    auto tmpAggregationResults = new ExpressionNode::CP[aggrSize];
    setAggrSize(aggrSize);
    for(uint32_t i(0); i < aggrSize; i++) {
        is >> tmpAggregationResults[i];
    }
    uint32_t exprSize(0);
    is >> exprSize;
    delete [] _aggregationResults;

    _aggregationResults = new ExpressionNode::CP[aggrSize + exprSize];
    for (uint32_t i(0); i < aggrSize; i++) {
        _aggregationResults[i] = tmpAggregationResults[i];
    }
    delete [] tmpAggregationResults;

    setExprSize(exprSize);
    for (uint32_t i(aggrSize); i < aggrSize + exprSize; i++) {
        is >> _aggregationResults[i];
    }
    setupAggregationReferences();
    is >> count;
    destruct(_children, getAllChildrenSize());
    clearAllChildrenSize();
    _children = new ChildP[std::max(4ul, 2ul << vespalib::Optimized::msbIdx(count))];
    for (uint32_t i(0); i < count; i++) {
        auto group = std::make_unique<Group>();
        is >> *group;
        _children[i] = group.release();
        setChildrenSize(i + 1);
    }
    is >> _tag;
    return is;
}

void
Group::Value::visitMembers(vespalib::ObjectVisitor &visitor) const {
//    visit(visitor, "orderBy",               _orderBy);
    visitor.openStruct("orderBy", "[]");
    visit(visitor, "size", getOrderBySize());
    for (size_t i(0), m(getOrderBySize()); i < m; i++) {
        visit(visitor, vespalib::make_string("[%lu]", i), getOrderBy(i));
    }
    visitor.closeStruct();
//    visit(visitor, "aggregationResults",    _aggregationResults);
    visitor.openStruct("aggregationresults", "[]");
    visit(visitor, "size", getAggrSize());
    for (size_t i(0), m(getAggrSize()); i < m; i++) {
        visit(visitor, vespalib::make_string("[%lu]", i), getAggrCP(i));
    }
    visitor.closeStruct();
//    visit(visitor, "expressionResults",     _expressionResults);
    visitor.openStruct("expressionResults", "[]");
    visit(visitor, "size", getExprSize());
    for (size_t i(0), m(getExprSize()); i < m; i++) {
        visit(visitor, vespalib::make_string("[%lu]", i), getExprCP(i));
    }
    visitor.closeStruct();
    //visit(visitor, "children",              _children);
    visitor.openStruct("children", "[]");
    visit(visitor, "size", getChildrenSize());
    for (size_t i(0), m(getChildrenSize()); i < m; i++) {
        visit(visitor, vespalib::make_string("[%lu]", i), getChild(i));
    }
    visitor.closeStruct();
    visit(visitor, "tag",                   _tag);
}

Group::Value::Value() noexcept :
    _aggregationResults(nullptr),
    _children(nullptr),
    _childInfo(),
    _childrenLength(0),
    _tag(-1),
    _packedLength(0),
    _orderBy()
{
    memset(_orderBy, 0, sizeof(_orderBy));
    _childInfo._childMap = nullptr;
}

Group::Value::Value(const Value & rhs) :
    _aggregationResults(nullptr),
    _children(nullptr),
    _childInfo(),
    _childrenLength(rhs._childrenLength),
    _tag(rhs._tag),
    _packedLength(rhs._packedLength),
    _orderBy()
{
    _childInfo._childMap = nullptr;
    memcpy(_orderBy, rhs._orderBy, sizeof(_orderBy));
    uint32_t totalAggrSize = rhs.getAggrSize() + rhs.getExprSize();
    if (totalAggrSize > 0) {
        _aggregationResults = new ExpressionNode::CP[totalAggrSize];
        for (size_t i(0), m(totalAggrSize); i < m; i++) {
            _aggregationResults[i] = rhs._aggregationResults[i];
        }
        setupAggregationReferences();
    }

    if (  rhs.getChildrenSize() > 0 ) {
        _children = new ChildP[std::max(4ul, 2ul << vespalib::Optimized::msbIdx(rhs.getChildrenSize()))];
        size_t i(0);
        for (const ChildP cp : rhs.iterateChildren()) {
            _children[i++] = new Group(*cp);
        }
    }
}

Group::Value::Value(Value && rhs) noexcept :
    _aggregationResults(rhs._aggregationResults),
    _children(rhs._children),
    _childInfo(rhs._childInfo),
    _childrenLength(rhs._childrenLength),
    _tag(rhs._tag),
    _packedLength(rhs._packedLength),
    _orderBy()
{
    memcpy(_orderBy, rhs._orderBy, sizeof(_orderBy));

    rhs.setChildrenSize(0);
    rhs._aggregationResults = nullptr;
    rhs.clearAllChildrenSize();
    rhs._children = nullptr;
}

Group::Value &
Group::Value::operator =(Value && rhs) noexcept {
    _childrenLength = rhs._childrenLength;
    _tag = rhs._tag;
    _packedLength = rhs._packedLength;
    _aggregationResults = rhs._aggregationResults;
    _children = rhs._children;
    _childInfo = rhs._childInfo;
    memcpy(_orderBy, rhs._orderBy, sizeof(_orderBy));

    rhs.setChildrenSize(0);
    rhs._aggregationResults = nullptr;
    rhs.clearAllChildrenSize();
    rhs._children = nullptr;
    return *this;
}

Group::Value &
Group::Value::operator =(const Value & rhs) {
    Value tmp(rhs);
    tmp.swap(*this);
    return *this;
}

Group::Value::~Value() noexcept
{
    destruct(_children, getAllChildrenSize());
    setChildrenSize(0);
    clearAllChildrenSize();
    delete [] _aggregationResults;
}

void
Group::Value::swap(Value & rhs)
{
    std::swap(_aggregationResults, rhs._aggregationResults);
    std::swap(_children, rhs._children);
    std::swap(_childInfo._childMap, rhs._childInfo._childMap);
    {
        int8_t tmp[sizeof(_orderBy)];
        memcpy(tmp, _orderBy, sizeof(_orderBy));
        memcpy(_orderBy, rhs._orderBy, sizeof(_orderBy));
        memcpy(rhs._orderBy, tmp, sizeof(_orderBy));
    }
    std::swap(_childrenLength, rhs._childrenLength);
    std::swap(_tag, rhs._tag);
    std::swap(_packedLength, rhs._packedLength);
}


void
Group::Value::partialCopy(const Value & rhs) {
    uint32_t totalAggrSize = getAggrSize() + getExprSize();
    for(size_t i(0), m(totalAggrSize); i < m; i++) {
        _aggregationResults[i] = rhs._aggregationResults[i];
    }
    for(size_t i(0), m(getAggrSize()); i < m; i++) {
        getAggr(i)->reset();
    }
    setAggrSize(rhs.getAggrSize());
    setOrderBySize(rhs.getOrderBySize());
    setExprSize(rhs.getExprSize());
    setupAggregationReferences();
    memcpy(_orderBy, rhs._orderBy, sizeof(_orderBy));
}

namespace {
    class AggregationRefNodeConfigure : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    public:
        AggregationRefNodeConfigure(expression::ExpressionNode::CP * & exprVec) : _exprVec(exprVec) { }
    private:
        void execute(vespalib::Identifiable &obj) override { static_cast<AggregationRefNode&>(obj).locateExpression(_exprVec); }
        bool check(const vespalib::Identifiable &obj) const override { return obj.inherits(AggregationRefNode::classId); }
        expression::ExpressionNode::CP * & _exprVec;
    };
}

void
Group::Value::setupAggregationReferences()
{
    AggregationRefNodeConfigure exprRefSetup(_aggregationResults);
    select(exprRefSetup, exprRefSetup);
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_group() {}
