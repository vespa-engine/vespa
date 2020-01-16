// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <algorithm>
#include <assert.h>
#include <queue>
#include <random>
#include "nns.h"

using LinkList = std::vector<uint32_t>;

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
        }
        lastUsed->clear();
        return *lastUsed;
    }
};

struct HnswHit {
    float dist;
    uint32_t docid;
    HnswHit(uint32_t di, SqDist sq) : dist(sq.distance), docid(di) {}
};


using QueueEntry = HnswHit;
struct GreaterDist {
    bool operator() (const QueueEntry &lhs, const QueueEntry& rhs) const {
        return (rhs.dist < lhs.dist);
    }
};
struct LesserDist {
    bool operator() (const QueueEntry &lhs, const QueueEntry& rhs) const {
        return (lhs.dist < rhs.dist);
    }
};

using NearestList = std::vector<QueueEntry>;

struct NearestPriQ : std::priority_queue<QueueEntry, NearestList, GreaterDist>
{
};

struct FurthestPriQ : std::priority_queue<QueueEntry, NearestList, LesserDist>
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
    std::default_random_engine _rndGen;
    VisitedSetPool _visitedSetPool;

    double distance(Vector v, uint32_t id) const;

    double distance(uint32_t a, uint32_t b) const {
        Vector v = _dva.get(a);
        return distance(v, b);
    }

    int randomLevel() {
        std::uniform_real_distribution<double> distribution(0.0, 1.0);
        double r = -log(distribution(_rndGen)) * _levelMultiplier;
        return (int) r;
    }

public:
    HnswLikeNns(uint32_t numDims, const DocVectorAccess<float> &dva)
        : NNS(numDims, dva),
          _nodes(),
          _entryId(0),
          _entryLevel(-1),
          _M(16),
          _efConstruction(150),
          _levelMultiplier(1.0 / log(1.0 * _M))
    {
        _nodes.reserve(1234567);
    }

    ~HnswLikeNns() {}

    LinkList& getLinkList(uint32_t docid, uint32_t level) {
        // assert(docid < _nodes.size());
        // assert(level < _nodes[docid]._links.size());
        return _nodes[docid]._links[level];
    }

    // simple greedy search
    QueueEntry search_layer_simple(Vector vector, QueueEntry curPoint, uint32_t searchLevel) {
        bool keepGoing = true;
        while (keepGoing) {
            keepGoing = false;
            const LinkList& neighbors = getLinkList(curPoint.docid, searchLevel);
            for (uint32_t n_id : neighbors) {
                double dist = distance(vector, n_id);
                if (dist < curPoint.dist) {
                     curPoint = QueueEntry(n_id, SqDist(dist));
                     keepGoing = true;
                }
            }
        }
        return curPoint;
    }

    void search_layer_foradd(Vector vector, FurthestPriQ &w,
                             uint32_t ef, uint32_t searchLevel);

    FurthestPriQ search_layer(Vector vector, NearestList entryPoints,
                              uint32_t ef, uint32_t searchLevel) {
        VisitedSet &visited = _visitedSetPool.get(_nodes.size());
        NearestPriQ candidates;
        FurthestPriQ w;
        for (auto point : entryPoints) {
            candidates.push(point);
            w.push(point);
            visited.mark(point.docid);
        }
        double limd = std::numeric_limits<double>::max();
        while (! candidates.empty()) {
            QueueEntry cand = candidates.top();
            candidates.pop();
            if (cand.dist > limd) {
                break;
            }
            for (uint32_t e_id : getLinkList(cand.docid, searchLevel)) {
                if (visited.isMarked(e_id)) continue;
                visited.mark(e_id);
                double e_dist = distance(vector, e_id);
                if (e_dist < limd) {
                    candidates.emplace(e_id, SqDist(e_dist));
                    w.emplace(e_id, SqDist(e_dist));
                    if (w.size() > ef) {
                        w.pop();
                        limd = w.top().dist;
                    }
                }
            }
        }
        return w;
    }

    bool haveCloserDistance(QueueEntry e, const LinkList &r) const {
        for (uint32_t prevId : r) {
            double dist = distance(e.docid, prevId);
            if (dist < e.dist) return true;
        }
        return false;
    }

    LinkList select_neighbors(NearestPriQ &&w, uint32_t curMax) const;

    LinkList select_neighbors(const NearestList &neighbors, uint32_t curMax) {
        if (neighbors.size() <= curMax) {
            LinkList result;
            result.reserve(curMax+1);
            for (const auto & entry : neighbors) {
                result.push_back(entry.docid);
            }
            return result;
        }
        NearestPriQ w;
        for (const QueueEntry & entry : neighbors) {
            w.push(entry);
        }
        return select_neighbors(std::move(w), curMax);
    }

    void addDoc(uint32_t docid) override {
        Vector vector = _dva.get(docid);
        for (uint32_t id = _nodes.size(); id <= docid; ++id) {
            _nodes.emplace_back(id, 0, _M);
        }
        int level = randomLevel();
        assert(_nodes[docid]._links.size() == 0);
        _nodes[docid] = Node(docid, level+1, _M);
        if (_entryLevel < 0) {
            _entryId = docid;
            _entryLevel = level;
            return;
        }
        int searchLevel = _entryLevel;
        double entryDist = distance(vector, _entryId);
        QueueEntry entryPoint(_entryId, SqDist(entryDist));
        while (searchLevel > level) {
            entryPoint = search_layer_simple(vector, entryPoint, searchLevel);
            --searchLevel;
        }
        searchLevel = std::min(level, _entryLevel);
        FurthestPriQ w;
        w.push(entryPoint);
        while (searchLevel >= 0) {
            search_layer_foradd(vector, w, _efConstruction, searchLevel);
            uint32_t maxLinks = (searchLevel > 0) ? _M : (2 * _M);
            LinkList neighbors = select_neighbors(w.peek(), maxLinks);
            connect_new_node(docid, neighbors, searchLevel);
            each_shrink_ifneeded(neighbors, searchLevel);
            --searchLevel;
        }
        if (level > _entryLevel) {
            _entryLevel = level;
            _entryId = docid;
        }
    }

    void connect_new_node(uint32_t id, const LinkList &neighbors, uint32_t level);

    void each_shrink_ifneeded(const LinkList &neighbors, uint32_t level);

    void removeDoc(uint32_t ) override {
    }

    std::vector<NnsHit> topK(uint32_t k, Vector vector, uint32_t search_k) override {
        std::vector<NnsHit> result;
        if (_entryLevel < 0) return result;
        double entryDist = distance(vector, _entryId);
        QueueEntry entryPoint(_entryId, SqDist(entryDist));
        int searchLevel = _entryLevel;
        while (searchLevel > 0) {
            entryPoint = search_layer_simple(vector, entryPoint, searchLevel);
            --searchLevel;
        }
        NearestList entryPoints;
        entryPoints.push_back(entryPoint);
        FurthestPriQ w = search_layer(vector, entryPoints, std::max(k, search_k), 0);
        if (w.size() < k) {
            fprintf(stderr, "fewer than expected hits: k=%u, ks=%u, got=%zu\n",
                    k, search_k, w.size());
        }
        while (w.size() > k) {
            w.pop();
        }
        std::vector<QueueEntry> reversed;
        reversed.reserve(w.size());
        while (! w.empty()) {
            reversed.push_back(w.top());
            w.pop();
        }
        result.reserve(reversed.size());
        while (! reversed.empty()) {
            const QueueEntry &hit = reversed.back();
            result.emplace_back(hit.docid, SqDist(hit.dist));
            reversed.pop_back();
        }
        return result;
    }
};

double
HnswLikeNns::distance(Vector v, uint32_t b) const
{
    Vector w = _dva.get(b);
    return l2distCalc.l2sq_dist(v, w);
}

void
HnswLikeNns::each_shrink_ifneeded(const LinkList &neighbors, uint32_t level) {
        uint32_t maxLinks = (level > 0) ? _M : (2 * _M);
        for (uint32_t old_id : neighbors) {
            LinkList &oldLinks = getLinkList(old_id, level);
            if (oldLinks.size() > maxLinks) {
                NearestPriQ w;
                for (uint32_t n_id : oldLinks) {
                    double n_dist = distance(old_id, n_id);
                    w.emplace(n_id, SqDist(n_dist));
                }
                oldLinks = select_neighbors(std::move(w), maxLinks);
            }
        }
}

void
HnswLikeNns::search_layer_foradd(Vector vector, FurthestPriQ &w,
                                 uint32_t ef, uint32_t searchLevel)
{
        NearestPriQ candidates;
        VisitedSet &visited = _visitedSetPool.get(_nodes.size());

        for (const QueueEntry& entry : w.peek()) {
            candidates.push(entry);
            visited.mark(entry.docid);
        }

        double limd = std::numeric_limits<double>::max();
        while (! candidates.empty()) {
            QueueEntry cand = candidates.top();
            candidates.pop();
            if (cand.dist > limd) {
                break;
            }
            for (uint32_t e_id : getLinkList(cand.docid, searchLevel)) {
                if (visited.isMarked(e_id)) continue;
                visited.mark(e_id);
                double e_dist = distance(vector, e_id);
                if (e_dist < limd) {
                    candidates.emplace(e_id, SqDist(e_dist));
                    w.emplace(e_id, SqDist(e_dist));
                    if (w.size() > ef) {
                        w.pop();
                        limd = w.top().dist;
                    }
                }
            }
        }
        return;
}

LinkList
HnswLikeNns::select_neighbors(NearestPriQ &&w, uint32_t curMax) const {
        LinkList result;
        result.reserve(curMax+1);
        while (! w.empty()) {
            QueueEntry e = w.top();
            w.pop();
            if (haveCloserDistance(e, result)) continue;
            result.push_back(e.docid);
            if (result.size() >= curMax) break;
        }
        return result;
}

void
HnswLikeNns::connect_new_node(uint32_t id, const LinkList &neighbors, uint32_t level) {
        LinkList &newLinks = getLinkList(id, level);
        for (uint32_t neigh_id : neighbors) {
            LinkList &oldLinks = getLinkList(neigh_id, level);
            newLinks.push_back(neigh_id);
            oldLinks.push_back(id);
        }
}


std::unique_ptr<NNS<float>>
make_hnsw_nns(uint32_t numDims, const DocVectorAccess<float> &dva)
{
    return std::make_unique<HnswLikeNns>(numDims, dva);
}


