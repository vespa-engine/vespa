// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <assert.h>
#include <queue>
#include <cinttypes>
#include "std-random.h"
#include "nns.h"

struct LinkList : std::vector<uint32_t>
{
    bool has_link_to(uint32_t id) const {
        auto iter = std::find(begin(), end(), id);
        return (iter != end());
    }
    void remove_link(uint32_t id) {
        uint32_t last = back();
        for (iterator iter = begin(); iter != end(); ++iter) {
            if (*iter == id) {
                *iter = last;
                pop_back();
                return;
            }
        }
        fprintf(stderr, "BAD missing link to remove: %u\n", id);
        abort();
    }
};

struct Node {
    std::vector<LinkList> _links;
    Node(uint32_t , uint32_t numLevels, uint32_t M)
        : _links(numLevels)
    {
        for (uint32_t i = 0; i < _links.size(); ++i) {
            _links[i].reserve((i == 0) ? (2 * M + 1) : (M+1));
        }
    }
};

struct VisitedSet
{
    using Mark = unsigned short;
    Mark *ptr;
    Mark curval;
    size_t sz;
    VisitedSet(const VisitedSet &) = delete;
    VisitedSet& operator=(const VisitedSet &) = delete;
    explicit VisitedSet(size_t size) {
        ptr = (Mark *)malloc(size * sizeof(Mark));
        curval = -1;
        sz = size;
        clear();
    }
    void clear() {
        ++curval;
        if (curval == 0) {
            memset(ptr, 0, sz * sizeof(Mark));
            ++curval;
        }
    }
    ~VisitedSet() { free(ptr); }
    void mark(size_t id) { ptr[id] = curval; }
    bool isMarked(size_t id) const { return ptr[id] == curval; }
};

struct VisitedSetPool
{
    std::unique_ptr<VisitedSet> lastUsed;
    VisitedSetPool() {
        lastUsed = std::make_unique<VisitedSet>(250);
    }
    ~VisitedSetPool() {}
    VisitedSet &get(size_t size) {
        if (size > lastUsed->sz) {
            lastUsed = std::make_unique<VisitedSet>(size*2);
        } else {
            lastUsed->clear();
        }
        return *lastUsed;
    }
};

struct HnswHit {
    double dist;
    uint32_t docid;
    HnswHit(uint32_t di, SqDist sq) noexcept : dist(sq.distance), docid(di) {}
};

struct GreaterDist {
    bool operator() (const HnswHit &lhs, const HnswHit& rhs) const {
        return (rhs.dist < lhs.dist);
    }
};
struct LesserDist {
    bool operator() (const HnswHit &lhs, const HnswHit& rhs) const {
        return (lhs.dist < rhs.dist);
    }
};

using NearestList = std::vector<HnswHit>;

struct NearestPriQ : std::priority_queue<HnswHit, NearestList, GreaterDist>
{
};

struct FurthestPriQ : std::priority_queue<HnswHit, NearestList, LesserDist>
{
    NearestList steal() {
        NearestList result;
        c.swap(result);
        return result;
    }
    const NearestList& peek() const { return c; }
};

class HnswLikeNns : public NNS<float>
{
private:
    std::vector<Node> _nodes;
    uint32_t _entryId;
    int _entryLevel;
    uint32_t _M;
    uint32_t _efConstruction;
    double _levelMultiplier;
    RndGen _rndGen;
    VisitedSetPool _visitedSetPool;
    size_t _ops_counter;

    double distance(Vector v, uint32_t id) const;

    double distance(uint32_t a, uint32_t b) const {
        Vector v = _dva.get(a);
        return distance(v, b);
    }

    int randomLevel() {
        double unif = _rndGen.nextUniform();
        double r = -log(1.0-unif) * _levelMultiplier;
        return (int) r;
    }

    uint32_t count_reachable() const;
    void dumpStats() const;

public:
    HnswLikeNns(uint32_t numDims, const DocVectorAccess<float> &dva);
    ~HnswLikeNns() { dumpStats(); }

    LinkList& getLinkList(uint32_t docid, uint32_t level) {
        return _nodes[docid]._links[level];
    }

    const LinkList& getLinkList(uint32_t docid, uint32_t level) const {
        return _nodes[docid]._links[level];
    }

    HnswHit search_layer_simple(Vector vector, HnswHit curPoint, uint32_t searchLevel);

    void search_layer(Vector vector, FurthestPriQ &w,
                      uint32_t ef, uint32_t searchLevel);
    void search_layer(Vector vector, FurthestPriQ &w,
                      VisitedSet &visited,
                      uint32_t ef, uint32_t searchLevel);
    void search_layer_with_filter(Vector vector, FurthestPriQ &w,
                                  uint32_t ef, uint32_t searchLevel,
                                  const BitVector &skipDocIds);
    void search_layer_with_filter(Vector vector, FurthestPriQ &w,
                                  VisitedSet &visited,
                                  uint32_t ef, uint32_t searchLevel,
                                  const BitVector &skipDocIds);

    bool haveCloserDistance(HnswHit e, const LinkList &r) const;

    LinkList select_neighbors(const NearestList &neighbors, uint32_t curMax) const;

    LinkList remove_weakest(const NearestList &neighbors, uint32_t curMax, LinkList &removed) const;

    void addDoc(uint32_t docid) override;

    void track_ops();

    void remove_link_from(uint32_t from_id, uint32_t remove_id, uint32_t level) {
        LinkList &links = getLinkList(from_id, level);
        links.remove_link(remove_id);
    }

    void refill_ifneeded(uint32_t my_id, const LinkList &replacements, uint32_t level);

    void connect_new_node(uint32_t id, const LinkList &neighbors, uint32_t level);

    void shrink_links(uint32_t shrink_id, uint32_t maxLinks, uint32_t level);

    void each_shrink_ifneeded(const LinkList &neighbors, uint32_t level);

    void removeDoc(uint32_t docid) override;

    std::vector<NnsHit> topK(uint32_t k, Vector vector, uint32_t search_k) override;

    std::vector<NnsHit> topKfilter(uint32_t k, Vector vector, uint32_t search_k, const BitVector &skipDocIds) override;
};
