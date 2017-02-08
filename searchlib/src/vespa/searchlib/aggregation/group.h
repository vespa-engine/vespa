// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rawrank.h"
#include "aggregationresult.h"
#include <vespa/searchlib/common/hitrank.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/fastos/dynamiclibrary.h>
#include <vector>

namespace search {
namespace aggregation {

class GroupingLevel;
class Grouping;

/**
 * Represents a Group instance. To make grouping fast, the serialization format and the group instance itself is very compact. The format is as follows:
 *
 * +-------------------------------------+-----------------+
 * | what                                | number of bytes |
 * +-------------------------------------+-----------------+
 * | result node id ptr                  | 8               |
 * | group rank                          | 8               |
 * | serialized length                   | 4               |
 * | group tag                           | 4               |
 * | aggregator vector                   | 8               |
 * | orderby vector                      | 2               |
 * | sub group vector                    | 8               |
 * | sub group vector size/temp hash map | 8               |
 * +-------------------------------------+-----------------+
 *
 * Total: 50 bytes
 */
class Group : public vespalib::Identifiable
{
public:
    using ResultNode = expression::ResultNode;
    using ExpressionNode = expression::ExpressionNode;
    using UP = std::unique_ptr<Group>;
    typedef Group * ChildP;
    typedef ChildP * GroupList;
    struct GroupEqual : public std::binary_function<ChildP, ChildP, bool> {
        GroupEqual(const GroupList * v) : _v(v) { }
        bool operator()(uint32_t a, uint32_t b) { return (*_v)[a]->getId().cmpFast((*_v)[b]->getId()) == 0; }
        const GroupList *_v;
    };
    struct GroupHasher {
        GroupHasher(const GroupList * v) : _v(v) { }
        size_t operator() (uint32_t arg) const { return (*_v)[arg]->getId().hash(); }
        const GroupList *_v;
    };
    struct GroupResult {
        GroupResult(const GroupList * v) : _v(v) { }
        const ResultNode & operator() (uint32_t arg) const { return (*_v)[arg]->getId(); }
        const GroupList *_v;
    };
    struct ResultLess : public std::binary_function<ResultNode::CP, ResultNode::CP, bool> {
        bool operator()(const ResultNode::CP & a, const ResultNode::CP & b) { return a->cmpFast(*b) < 0; }
    };
    struct ResultEqual : public std::binary_function<ResultNode, ResultNode, bool> {
        bool operator()(const ResultNode & a, const ResultNode & b) { return a.cmpFast(b) == 0; }
    };
    struct ResultHash {
        size_t operator() (const ResultNode & arg) const { return arg.hash(); }
    };

    typedef ExpressionNode::CP * ExpressionVector;
    typedef vespalib::hash_set<uint32_t, GroupHasher, GroupEqual > GroupHash;
    typedef std::vector<GroupingLevel>         GroupingLevelList;

private:
    ResultNode::CP   _id;                   // the label of this group, separating it from other groups
    RawRank          _rank;                 // The default rank taken from the highest hit relevance.
    uint32_t         _packedLength;         // Length of the 3 vectors below
    uint32_t         _tag;                  // Opaque tag used to identify the group by the client.

   // The collectors and expressions stored by this group. Currently, both aggregation results and expressions used by orderby() are stored in this
   // array to save 8 bytes in the Group size. This makes it important to use the getAggr() and expr() methods for accessing elements,
   // as they will correctly offset the index to the correct place in the array.
    ExpressionVector _aggregationResults;

    uint8_t          _orderBy[2];           // How this group is ranked, negative means reverse rank.
    ChildP          *_children;             // the sub-groups of this group. Great care must be taken to ensure proper destruct.
    union ChildInfo {
        GroupHash *_childMap;               // child map used during aggregation
        size_t     _allChildren;            // Keep real number of children.
    }                _childInfo;

    bool needFullRank() const { return getOrderBySize() != 0; }
    Group & partialCopy(const Group & rhs);
    void setOrderBy(uint32_t i, int32_t v) {
        if (v < 0) {
            v = -v;
            v = v | 0x8;
        }
        _orderBy[i/2]  = (_orderBy[i/2] & (0xf0 >> (4*(i%2)))) | (v << (4*(i%2)));
    }
    uint32_t getExprSize()    const { return (_packedLength >> 4) & 0x03; }
    void setAggrSize(uint32_t v)    { _packedLength = (_packedLength & ~0x0f) | v; }
    void setExprSize(uint32_t v)    { _packedLength = (_packedLength & ~0x30) | (v << 4); }
    void setOrderBySize(uint32_t v) { _packedLength = (_packedLength & ~0xc0) | (v << 6); }
    void setChildrenSize(uint32_t v) { _packedLength = (_packedLength & ~0xffffff00) | (v << 8); }
    AggregationResult * getAggr(size_t i) { return static_cast<AggregationResult *>(_aggregationResults[i].get()); }
    const AggregationResult & getAggr(size_t i) const { return static_cast<const AggregationResult &>(*_aggregationResults[i]); }
    const ExpressionNode::CP & getAggrCP(size_t i) const { return _aggregationResults[i]; }
    const ExpressionNode::CP & getExprCP(size_t i) const { return _aggregationResults[getExpr(i)]; }
    ExpressionNode & expr(size_t i)  { return *_aggregationResults[getExpr(i)]; }
    const ExpressionNode & expr(size_t i)  const { return *_aggregationResults[getExpr(i)]; }
    static void reset(Group * & v) { v = NULL; }
    static void destruct(Group * v) { if (v) { delete v; } }
    static void destruct(GroupList & l, size_t sz);
    void addChild(Group * child);
    void setupAggregationReferences();
    size_t getAllChildrenSize() const { return std::max(static_cast<size_t>(getChildrenSize()), _childInfo._allChildren); }
    template <typename Doc>
    VESPA_DLL_LOCAL void groupNext(const GroupingLevel & level, const Doc & docId, HitRank rank);
public:
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, Group);
    DECLARE_NBO_SERIALIZE;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    Group();
    Group(const Group & rhs);
    Group & operator =(const Group & rhs);
    virtual ~Group();
    void swap(Group & rhs);

    int cmpId(const Group &rhs) const { return _id->cmpFast(*rhs._id); }
    int cmpRank(const Group &rhs) const;
    Group & setRank(RawRank r);
    Group & updateRank(RawRank r);
    RawRank getRank() const { return _rank; }

    VESPA_DLL_LOCAL Group * groupSingle(const ResultNode & result, HitRank rank, const GroupingLevel & level);

    bool hasId() const { return (_id.get() != NULL); }
    const ResultNode &getId() const { return *_id; }

    Group unchain() const { return *this; }

    Group &setId(const ResultNode &id)  { _id.reset(static_cast<ResultNode *>(id.clone())); return *this; }
    Group &addAggregationResult(const ExpressionNode::CP &result);
    Group &addResult(const ExpressionNode::CP &aggr);
    Group &addExpressionResult(const ExpressionNode::CP &expressionNode);
    Group &addOrderBy(const ExpressionNode::CP & orderBy, bool ascending);
    Group &addChild(const Group &child) { addChild(new Group(child)); return *this; }
    Group &addChild(Group::UP child) { addChild(child.release()); return *this; }

    /**
     * Prunes this tree, keeping only the nodes found in another
     * tree.
     *
     * @param b The tree containing the nodes that should be kept.
     * @param lastLevel The last level on which to perform pruning.
     * @param currentLevel The current level on which to perform pruning.
     **/
    void prune(const Group & b, uint32_t lastLevel, uint32_t currentLevel);

    /**
     * Recursively checks if any itself or any children needs a full resort.
     * Then all hits must be processed and should be doen before any hit sorting.
     */
    bool needResort() const;

    virtual void selectMembers(const vespalib::ObjectPredicate &predicate,
                               vespalib::ObjectOperation &operation);

    void preAggregate();
    template <typename Doc>
    VESPA_DLL_LOCAL void aggregate(const Grouping & grouping, uint32_t currentLevel, const Doc & docId, HitRank rank);

    template <typename Doc>
    void collect(const Doc & docId, HitRank rank);
    void postAggregate();
    void merge(const std::vector<GroupingLevel> &levels, uint32_t firstLevel, uint32_t currentLevel, Group &b);
    void executeOrderBy();

    /**
     * Merge children and results of another tree within the unfrozen parts of
     * this tree.
     *
     * @param b The tree to pick children and results from.
     * @param firstLevel The first level to merge.
     * @param lastLevel The last level to merge.
     * @param currentLevel The current level on which merging should be done.
     **/
    void mergePartial(const std::vector<GroupingLevel> &levels, uint32_t firstLevel, uint32_t lastLevel, uint32_t currentLevel, const Group & b);
    void postMerge(const std::vector<GroupingLevel> &levels, uint32_t firstLevel, uint32_t currentLevel);
    void sortById();
    uint32_t getChildrenSize()   const { return (_packedLength >> 8); }
    const Group & getChild(size_t i) const { return *_children[i]; }
    GroupList groups() const { return _children; }
    const AggregationResult & getAggregationResult(size_t i) const { return static_cast<const AggregationResult &>(*_aggregationResults[i]); }
    AggregationResult & getAggregationResult(size_t i) { return static_cast<AggregationResult &>(*_aggregationResults[i]); }
    uint32_t getAggrSize()    const { return _packedLength & 0x0f; }
    uint32_t getOrderBySize() const { return (_packedLength >> 6) & 0x03; }
    uint32_t getExpr(uint32_t i) const { return getAggrSize() + i; }
    int32_t getOrderBy(uint32_t i) const {
        int32_t v((_orderBy[i/2] >> (4*(i%2))) & 0x0f);
        return (v & 0x8) ? -(v&0x7) : v;
    }
};

}
}
