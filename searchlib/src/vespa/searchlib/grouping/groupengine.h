// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "collect.h"
#include <vespa/searchlib/aggregation/groupinglevel.h>
#include <vespa/vespalib/util/sort.h>

namespace search::grouping {

class GroupEngine : protected Collect
{
public:
    class GroupHash {
    public:
        GroupHash(const GroupEngine & engine) : _engine(engine) { }
        uint32_t operator () (GroupRef a) const { return _engine.hash(a); }
        uint32_t operator () (const expression::ResultNode & a) const { return a.hash(); }
    private:
        const GroupEngine & _engine;
    };
    class GroupEqual {
    public:
        GroupEqual(const GroupEngine & engine) : _engine(engine) { }
        bool operator () (GroupRef a, GroupRef b) const { return _engine.cmpId(a, b) == 0; }
        bool operator () (const expression::ResultNode & a, GroupRef b) const { return a.cmpFast(_engine.getGroupId(b)) == 0; }
        bool operator () (GroupRef a, const expression::ResultNode & b) const { return _engine.getGroupId(a).cmpFast(b) == 0; }
    private:
        const GroupEngine & _engine;
    };
    class GroupIdLess {
    public:
        GroupIdLess(const GroupEngine & engine) : _engine(engine) { }
        bool operator () (GroupRef a, GroupRef b) const { return _engine.cmpId(a, b) < 0; }
    private:
        const GroupEngine & _engine;
    };
    class GroupRankRadix {
    public:
        GroupRankRadix(const GroupEngine & engine) : _engine(engine) { }
        uint64_t operator () (GroupRef a) const { return _engine.rankRadix(a); }
    private:
        const GroupEngine & _engine;
    };
    class GroupRankLess {
    public:
        GroupRankLess(const GroupEngine & engine) : _engine(engine) { }
        bool operator () (GroupRef a, GroupRef b) const { return _engine.cmpRank(a, b) < 0; }
    private:
        const GroupEngine & _engine;
    };
    class GroupResult {
    public:
        GroupResult(const GroupEngine & engine) : _engine(engine) { }
        const expression::ResultNode & operator() (GroupRef v) const { return _engine.getGroupId(v); }
    private:
        const GroupEngine & _engine;
    };

    typedef vespalib::hash_set<GroupRef, GroupHash, GroupEqual> Children;

    /**
     * @param request The request creating this engine.
     * @param level This is my level. 0 is the top level.
     * @param nextEngine This is the engine handling the next level.
     * @param frozen Tell if this level can create new groups or not.
     */
    GroupEngine(const aggregation::GroupingLevel * request, size_t level, GroupEngine * nextEngine, bool frozen);
    virtual ~GroupEngine();

    /**
     * @param children The list of children already present.
     * @param docId The docid of the hit
     * @param rank The rank of the hit
     **/
    virtual GroupRef group(Children & children, uint32_t docId, double rank);
    virtual void group(uint32_t docId, double rank);
    virtual void merge(Children & children, const GroupEngine & b);
    virtual void merge(const GroupEngine & b);

    std::unique_ptr<Children> createChildren();

    virtual aggregation::Group::UP getGroup(GroupRef ref) const;
    aggregation::Group::UP getRootGroup() const { return getGroup(GroupRef(0)); }

    GroupRef preFillEngine(const aggregation::Group & r, size_t depth);

protected:
    GroupEngine(const aggregation::GroupingLevel * request, size_t level);
    void groupNext(uint32_t docId, double rank);
    virtual GroupRef createGroup(const expression::ResultNode & id);
private:
    int cmpRank(GroupRef a, GroupRef b) const {
        //Here there is room for improvement
        //Most critical inner loop.
#if 0
        return cmpAggr(a, b);
#else
#if 1
        int diff(cmpAggr(a, b));
        return diff
                   ? diff
                   : ((_rank[a] > _rank[b])
                       ? -1
                       : ((_rank[a] < _rank[b]) ? 1 : 0));
#else
        return (_rank[a] > _rank[b])
                   ? -1
                   : ((_rank[a] < _rank[b]) ? 1 : 0);
#endif
#endif
    }
    size_t hash(GroupRef a) const { return _idScratch->hash(&_ids[getIdBase(a)]); }
    uint64_t rankRadix(GroupRef a) const { return vespalib::convertForSort<double, false>::convert(_rank[a]); }
    int cmpId(GroupRef a, GroupRef b) const {
        return _idScratch->cmpMem(&_ids[getIdBase(a)], &_ids[getIdBase(b)]);
    }
    GroupRef createFullGroup(const expression::ResultNode & id);
    const expression::ResultNode & getGroupId(GroupRef ref) const { return getGroupId(ref, *_idScratch); }
    const expression::ResultNode & getGroupId(GroupRef ref, expression::ResultNode & r) const {
        r.decode(&_ids[getIdBase(ref)]);
        return r;
    }
    size_t getIdBase(GroupRef g) const { return _idByteSize*g; }

    using IdList = std::unique_ptr<expression::ResultNodeVector>;
    using GroupBacking = std::vector<Children *>;
    using RankV = std::vector<double>;
    using IdBacking = std::vector<uint8_t>;

    const aggregation::GroupingLevel   * _request;
    GroupEngine     * _nextEngine;     // This is the engine for the next level.
    size_t            _idByteSize;     // Correct fixed size of memory needed for one id.
    IdBacking         _ids;            // These are all the group ids at this level.
    expression::ResultNode::UP _idScratch;  // Used for typing the ids.
    RankV             _rank;           // This is the rank of the group. TODO handle with ordinary aggregator.
    GroupBacking      _groupBacking;   // These are all the children at this level. Vector<HashTable<GroupRef()>>
    size_t            _level;          // This is my level
    bool              _frozen;         // If set no more groups will be created at this level.
};

}
