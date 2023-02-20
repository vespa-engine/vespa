// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rawrank.h"
#include "aggregationresult.h"
#include <vespa/searchlib/common/hitrank.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vector>

namespace search::aggregation {

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
class Group final : public vespalib::Identifiable
{
public:
    using ResultNode = expression::ResultNode;
    using ExpressionNode = expression::ExpressionNode;
    using UP = std::unique_ptr<Group>;
    using ChildP = Group *;
    using GroupList = ChildP *;
    struct GroupEqual {
        GroupEqual(const GroupList * v) : _v(v) { }
        bool operator()(uint32_t a, uint32_t b) { return (*_v)[a]->getId().cmpFast((*_v)[b]->getId()) == 0; }
        bool operator()(const Group & a, uint32_t b) { return a.getId().cmpFast((*_v)[b]->getId()) == 0; }
        bool operator()(uint32_t a, const Group & b) { return (*_v)[a]->getId().cmpFast(b.getId()) == 0; }
        bool operator()(const ResultNode & a, uint32_t b) { return a.cmpFast((*_v)[b]->getId()) == 0; }
        bool operator()(uint32_t a, const ResultNode & b) { return (*_v)[a]->getId().cmpFast(b) == 0; }
        const GroupList *_v;
    };
    struct GroupHasher {
        GroupHasher(const GroupList * v) : _v(v) { }
        size_t operator() (uint32_t arg) const { return (*_v)[arg]->getId().hash(); }
        size_t operator() (const Group & arg) const { return arg.getId().hash(); }
        size_t operator() (const ResultNode & arg) const { return arg.hash(); }
        const GroupList *_v;
    };

    using GroupingLevelList = std::vector<GroupingLevel>;

    class Value {
    public:
        Value();
        Value(const Value & rhs);
        Value & operator =(const Value & rhs);
        Value(Value &&) noexcept;
        Value & operator = (Value &&) noexcept;
        ~Value() noexcept;
        void swap(Value & rhs);

        VESPA_DLL_LOCAL int cmp(const Value & rhs) const;
        void addExpressionResult(ExpressionNode::UP expressionNode);
        void addAggregationResult(ExpressionNode::UP aggr);
        void addResult(ExpressionNode::UP aggr);
        void setupAggregationReferences();
        void addOrderBy(ExpressionNode::UP orderBy, bool ascending);
        void select(const vespalib::ObjectPredicate &predicate, vespalib::ObjectOperation &operation);
        void preAggregate();
        void postAggregate();
        void executeOrderBy();
        void sortById();
        void mergeCollectors(const Value & rhs);
        void execute();
        bool needResort() const;
        void assertIdOrder() const;
        void visitMembers(vespalib::ObjectVisitor &visitor) const;
        vespalib::Serializer & serialize(vespalib::Serializer & os) const;
        vespalib::Deserializer & deserialize(vespalib::Deserializer & is);
        void mergeLevel(const Group & protoType, const Value & b);
        void mergePartial(const GroupingLevelList &levels, uint32_t firstLevel, uint32_t lastLevel,
                          uint32_t currentLevel, const Value & b);
        void merge(const GroupingLevelList & levels, uint32_t firstLevel, uint32_t currentLevel, const Value & rhs);
        void prune(const Value & b, uint32_t lastLevel, uint32_t currentLevel);
        void postMerge(const std::vector<GroupingLevel> &levels, uint32_t firstLevel, uint32_t currentLevel);
        void partialCopy(const Value & rhs);
        VESPA_DLL_LOCAL Group * groupSingle(const ResultNode & selectResult, HitRank rank, const GroupingLevel & level);

        GroupList groups() const { return _children; }
        void addChild(Group * child);
        uint32_t getAggrSize()    const { return _packedLength & 0xffff; }
        uint32_t getExprSize()    const { return (_packedLength >> 16) & 0x0f; }
        uint32_t getOrderBySize() const { return (_packedLength >> 20) & 0x0f; }
        uint32_t getChildrenSize()   const { return _childrenLength; }
        uint32_t getExpr(uint32_t i) const { return getAggrSize() + i; }
        int32_t getOrderBy(uint32_t i) const {
            int32_t v((_orderBy[i/2] >> (4*(i%2))) & 0x0f);
            return (v & 0x8) ? -(v&0x7) : v;
        }

        const AggregationResult & getAggregationResult(size_t i) const { return static_cast<const AggregationResult &>(*_aggregationResults[i]); }
        AggregationResult & getAggregationResult(size_t i) { return static_cast<AggregationResult &>(*_aggregationResults[i]); }
        const Group & getChild(size_t i) const { return *_children[i]; }

        template <typename Doc>
        void collect(const Doc & docId, HitRank rank);
    private:

        using  ExpressionVector = ExpressionNode::CP *;
        using GroupHash = vespalib::hash_set<uint32_t, GroupHasher, GroupEqual >;
        void setAggrSize(uint32_t v);
        void setExprSize(uint32_t v);
        void setOrderBySize(uint32_t v);
        void setChildrenSize(uint32_t v) { _childrenLength = v; }
        AggregationResult * getAggr(size_t i) { return static_cast<AggregationResult *>(_aggregationResults[i].get()); }
        const AggregationResult & getAggr(size_t i) const { return static_cast<const AggregationResult &>(*_aggregationResults[i]); }
        const ExpressionNode::CP & getAggrCP(size_t i) const { return _aggregationResults[i]; }
        const ExpressionNode::CP & getExprCP(size_t i) const { return _aggregationResults[getExpr(i)]; }
        ExpressionNode & expr(size_t i)  { return *_aggregationResults[getExpr(i)]; }
        const ExpressionNode & expr(size_t i)  const { return *_aggregationResults[getExpr(i)]; }
        size_t getAllChildrenSize() const { return std::max(static_cast<size_t>(getChildrenSize()), _childInfo._allChildren); }
        void setOrderBy(uint32_t i, int32_t v) {
            if (v < 0) {
                v = -v;
                v = v | 0x8;
            }
            _orderBy[i/2]  = (_orderBy[i/2] & (0xf0 >> (4*(i%2)))) | (v << (4*(i%2)));
        }
        bool needFullRank() const { return getOrderBySize() != 0; }

        // The collectors and expressions stored by this group. Currently, both aggregation results and expressions used by orderby() are stored in this
        // array to save 8 bytes in the Group size. This makes it important to use the getAggr() and expr() methods for accessing elements,
        // as they will correctly offset the index to the correct place in the array.
        ExpressionVector _aggregationResults;

        ChildP          *_children;             // the sub-groups of this group. Great care must be taken to ensure proper destruct.
        union ChildInfo {
            GroupHash *_childMap;               // child map used during aggregation
            size_t     _allChildren;            // Keep real number of children.
        }                _childInfo;
        uint32_t         _childrenLength;
        uint32_t         _tag;                  // Opaque tag used to identify the group by the client.
        uint32_t         _packedLength;         // Length of aggr and expr vectors.
        uint8_t          _orderBy[4];           // How this group is ranked, negative means reverse rank.
    };
private:
    ResultNode::CP   _id;                   // the label of this group, separating it from other groups
    RawRank          _rank;                 // The default rank taken from the highest hit relevance.
    Value            _aggr;

    Group & partialCopy(const Group & rhs);

    template <typename Doc>
    VESPA_DLL_LOCAL void groupNext(const GroupingLevel & level, const Doc & docId, HitRank rank);
public:
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, Group);
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    Group();
    Group(const Group & rhs);
    Group & operator =(const Group & rhs);
    Group(Group &&) noexcept = default;
    Group & operator = (Group &&) noexcept = default;
    ~Group();

    int cmpId(const Group &rhs) const { return _id->cmpFast(*rhs._id); }
    int cmpRank(const Group &rhs) const;
    Group & setRank(RawRank r);
    Group & updateRank(RawRank r);
    RawRank getRank() const { return _rank; }

    Group * groupSingle(const ResultNode & result, HitRank rank, const GroupingLevel & level) {
        return _aggr.groupSingle(result, rank, level);
    }

    bool hasId() const { return static_cast<bool>(_id); }
    const ResultNode &getId() const { return *_id; }

    Group unchain() const { return *this; }

    Group &setId(const ResultNode &id)  { _id.reset(id.clone()); return *this; }
    Group &addAggregationResult(ExpressionNode::UP result) {
        _aggr.addAggregationResult(std::move(result));
        return *this;
    }
    Group &addResult(ExpressionNode::UP aggr) {
        _aggr.addResult(std::move(aggr));
        return *this;
    }
    Group &addResult(const ExpressionNode & aggr) { return addResult(ExpressionNode::UP(aggr.clone())); }

    Group &addOrderBy(ExpressionNode::UP orderBy, bool ascending) {
        _aggr.addOrderBy(std::move(orderBy), ascending); return *this;
    }
    Group &addOrderBy(const ExpressionNode & orderBy, bool ascending) {
        return addOrderBy(ExpressionNode::UP(orderBy.clone()), ascending);
    }
    Group &addChild(const Group &child) { _aggr.addChild(new Group(child)); return *this; }
    Group &addChild(Group::UP child) { _aggr.addChild(child.release()); return *this; }

    GroupList groups()               const { return _aggr.groups(); }
    uint32_t getAggrSize()           const { return _aggr.getAggrSize(); }
    uint32_t getOrderBySize()        const { return _aggr.getOrderBySize(); }
    uint32_t getExpr(uint32_t i)     const { return _aggr.getExpr(i); }
    int32_t  getOrderBy(uint32_t i)  const { return _aggr.getOrderBy(i); }
    uint32_t getChildrenSize()       const { return _aggr.getChildrenSize(); }
    const Group & getChild(size_t i) const { return _aggr.getChild(i); }

    const AggregationResult & getAggregationResult(size_t i) const { return _aggr.getAggregationResult(i); }
    AggregationResult &       getAggregationResult(size_t i)       { return _aggr.getAggregationResult(i); }

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
     * Then all hits must be processed and should be done before any hit sorting.
     */
    bool needResort() const { return _aggr.needResort(); }

    void selectMembers(const vespalib::ObjectPredicate &predicate, vespalib::ObjectOperation &operation) override;

    void preAggregate() { return _aggr.preAggregate(); }
    template <typename Doc>
    VESPA_DLL_LOCAL void aggregate(const Grouping & grouping, uint32_t currentLevel, const Doc & docId, HitRank rank);

    template <typename Doc>
    void collect(const Doc & docId, HitRank rank) { _aggr.collect(docId, rank); }
    void postAggregate() { _aggr.postAggregate(); }
    void merge(const std::vector<GroupingLevel> &levels, uint32_t firstLevel, uint32_t currentLevel, Group &b);
    void executeOrderBy() { _aggr.executeOrderBy(); }
    void sortById() { _aggr.sortById(); }

    /**
     * Merge children and results of another tree within the unfrozen parts of
     * this tree.
     *
     * @param b The tree to pick children and results from.
     * @param firstLevel The first level to merge.
     * @param lastLevel The last level to merge.
     * @param currentLevel The current level on which merging should be done.
     **/
    void mergePartial(const std::vector<GroupingLevel> &levels, uint32_t firstLevel, uint32_t lastLevel,
                      uint32_t currentLevel, const Group & b);
    void postMerge(const std::vector<GroupingLevel> &levels, uint32_t firstLevel, uint32_t currentLevel) {
        _aggr.postMerge(levels, firstLevel, currentLevel);
    }
};

}
