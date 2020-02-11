// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <algorithm>
#include <assert.h>
#include <queue>
#include <cinttypes>
#include "std-random.h"
#include "nns.h"

/*
 Todo:

 measure effect of:
 1) removing leftover backlinks during "shrink" operation
 2) refilling to low-watermark after 1) happens
 3) refilling to mid-watermark after 1) happens
 4) adding then removing 20% extra documents
 5) removing 20% first-added documents
 6) removing first-added documents while inserting new ones

 7) auto-tune search_k to ensure >= 50% recall on 1000 Q with k=100
 8) auto-tune search_k to ensure avg 90% recall on 1000 Q with k=100
 9) auto-tune search_k to ensure >= 90% reachability of 10000 docids

 10) timings for SIFT, GIST, and DEEP data (100k, 200k, 300k, 500k, 700k, 1000k)
 */

static size_t distcalls_simple;
static size_t distcalls_search_layer;
static size_t distcalls_other;
static size_t distcalls_heuristic;
static size_t distcalls_shrink;
static size_t distcalls_refill;
static size_t refill_needed_calls;

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
    double dist;
    uint32_t docid;
    HnswHit(uint32_t di, SqDist sq) : dist(sq.distance), docid(di) {}
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
    HnswLikeNns(uint32_t numDims, const DocVectorAccess<float> &dva)
        : NNS(numDims, dva),
          _nodes(),
          _entryId(0),
          _entryLevel(-1),
          _M(16),
          _efConstruction(200),
          _levelMultiplier(1.0 / log(1.0 * _M)),
          _rndGen(),
          _ops_counter(0)
    {
    }

    ~HnswLikeNns() { dumpStats(); }

    LinkList& getLinkList(uint32_t docid, uint32_t level) {
        // assert(docid < _nodes.size());
        // assert(level < _nodes[docid]._links.size());
        return _nodes[docid]._links[level];
    }

    const LinkList& getLinkList(uint32_t docid, uint32_t level) const {
        return _nodes[docid]._links[level];
    }

    // simple greedy search
    HnswHit search_layer_simple(Vector vector, HnswHit curPoint, uint32_t searchLevel) {
        bool keepGoing = true;
        while (keepGoing) {
            keepGoing = false;
            const LinkList& neighbors = getLinkList(curPoint.docid, searchLevel);
            for (uint32_t n_id : neighbors) {
                double dist = distance(vector, n_id);
                ++distcalls_simple;
                if (dist < curPoint.dist) {
                    curPoint = HnswHit(n_id, SqDist(dist));
                    keepGoing = true;
                }
            }
        }
        return curPoint;
    }

    void search_layer(Vector vector, FurthestPriQ &w,
                      uint32_t ef, uint32_t searchLevel);

    bool haveCloserDistance(HnswHit e, const LinkList &r) const {
        for (uint32_t prevId : r) {
            double dist = distance(e.docid, prevId);
            ++distcalls_heuristic;
            if (dist < e.dist) return true;
        }
        return false;
    }

    LinkList select_neighbors(const NearestList &neighbors, uint32_t curMax) const;

    LinkList remove_weakest(const NearestList &neighbors, uint32_t curMax, LinkList &removed) const;

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
            track_ops();
            return;
        }
        int searchLevel = _entryLevel;
        double entryDist = distance(vector, _entryId);
        ++distcalls_other;
        HnswHit entryPoint(_entryId, SqDist(entryDist));
        while (searchLevel > level) {
            entryPoint = search_layer_simple(vector, entryPoint, searchLevel);
            --searchLevel;
        }
        searchLevel = std::min(level, _entryLevel);
        FurthestPriQ w;
        w.push(entryPoint);
        while (searchLevel >= 0) {
            search_layer(vector, w, _efConstruction, searchLevel);
            LinkList neighbors = select_neighbors(w.peek(), _M);
            connect_new_node(docid, neighbors, searchLevel);
            each_shrink_ifneeded(neighbors, searchLevel);
            --searchLevel;
        }
        if (level > _entryLevel) {
            _entryLevel = level;
            _entryId = docid;
        }
        track_ops();
    }

    void track_ops() {
        _ops_counter++;
        if ((_ops_counter % 10000) == 0) {
            double div = _ops_counter;
            fprintf(stderr, "add / remove ops: %zu\n", _ops_counter);
            fprintf(stderr, "distance calls for layer: %zu is %.3f per op\n", distcalls_search_layer, distcalls_search_layer/ div);
            fprintf(stderr, "distance calls for heuristic: %zu is %.3f per op\n", distcalls_heuristic, distcalls_heuristic / div);
            fprintf(stderr, "distance calls for simple: %zu is %.3f per op\n", distcalls_simple, distcalls_simple / div);
            fprintf(stderr, "distance calls for shrink: %zu is %.3f per op\n", distcalls_shrink, distcalls_shrink / div);
            fprintf(stderr, "distance calls for refill: %zu is %.3f per op\n", distcalls_refill, distcalls_refill / div);
            fprintf(stderr, "distance calls for other: %zu is %.3f per op\n", distcalls_other, distcalls_other / div);
            fprintf(stderr, "refill needed calls: %zu is %.3f per op\n", refill_needed_calls, refill_needed_calls / div);
        }
    }                    

    void remove_link_from(uint32_t from_id, uint32_t remove_id, uint32_t level) {
        LinkList &links = getLinkList(from_id, level);
        links.remove_link(remove_id);
    }

    void refill_ifneeded(uint32_t my_id, const LinkList &replacements, uint32_t level) {
        LinkList &my_links = getLinkList(my_id, level);
        if (my_links.size() < 8) {
            ++refill_needed_calls;
            for (uint32_t repl_id : replacements) {
                if (repl_id == my_id) continue;
                if (my_links.has_link_to(repl_id)) continue;
                LinkList &other_links = getLinkList(repl_id, level);
                if (other_links.size() + 1 >= _M) continue;
                other_links.push_back(my_id);
                my_links.push_back(repl_id);
                if (my_links.size() >= _M) return;
            }
        }
    }

    void connect_new_node(uint32_t id, const LinkList &neighbors, uint32_t level);

    void shrink_links(uint32_t shrink_id, uint32_t maxLinks, uint32_t level) {
        LinkList &links = getLinkList(shrink_id, level);
        NearestList distances;
        for (uint32_t n_id : links) {
            double n_dist = distance(shrink_id, n_id);
            ++distcalls_shrink;
            distances.emplace_back(n_id, SqDist(n_dist));
        }
        LinkList lostLinks;
        LinkList oldLinks = links;
        links = remove_weakest(distances, maxLinks, lostLinks);
        for (uint32_t lost_id : lostLinks) {
            remove_link_from(lost_id, shrink_id, level);
            refill_ifneeded(lost_id, oldLinks, level);
        }
    }

    void each_shrink_ifneeded(const LinkList &neighbors, uint32_t level);

    void removeDoc(uint32_t docid) override {
        Node &node = _nodes[docid];
        bool need_new_entrypoint = (docid == _entryId);
        for (int level = node._links.size(); level-- > 0; ) {
            LinkList my_links;
            my_links.swap(node._links[level]);
            for (uint32_t n_id : my_links) {
                if (need_new_entrypoint) {
                    _entryId = n_id;
                    _entryLevel = level;
                    need_new_entrypoint = false;
                }
                remove_link_from(n_id, docid, level);
            }
            for (uint32_t n_id : my_links) {
                refill_ifneeded(n_id, my_links, level);
            }
        }
        node = Node(docid, 0, _M);
        if (need_new_entrypoint) {
            _entryLevel = -1;
            _entryId = 0;
            for (uint32_t i = 0; i < _nodes.size(); ++i) {
                if (_nodes[i]._links.size() > 0) {
                    _entryId = i;
                    _entryLevel = _nodes[i]._links.size() - 1;
                    break;
                }
            }
        }
        track_ops();
    }

    std::vector<NnsHit> topK(uint32_t k, Vector vector, uint32_t search_k) override {
        std::vector<NnsHit> result;
        if (_entryLevel < 0) return result;
        double entryDist = distance(vector, _entryId);
        ++distcalls_other;
        HnswHit entryPoint(_entryId, SqDist(entryDist));
        int searchLevel = _entryLevel;
        FurthestPriQ w;
        w.push(entryPoint);
        while (searchLevel > 0) {
            search_layer(vector, w, std::min(k, search_k), searchLevel);
            --searchLevel;
        }
        search_layer(vector, w, std::max(k, search_k), 0);
        while (w.size() > k) {
            w.pop();
        }
        NearestList tmp = w.steal();
        std::sort(tmp.begin(), tmp.end(), LesserDist());
        result.reserve(tmp.size());
        for (const auto & hit : tmp) {
            result.emplace_back(hit.docid, SqDist(hit.dist));
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
            shrink_links(old_id, maxLinks, level);
        }
    }
}

void
HnswLikeNns::search_layer(Vector vector, FurthestPriQ &w,
                          uint32_t ef, uint32_t searchLevel)
{
    NearestPriQ candidates;
    VisitedSet &visited = _visitedSetPool.get(_nodes.size());

    for (const HnswHit & entry : w.peek()) {
        candidates.push(entry);
        visited.mark(entry.docid);
    }
    double limd = std::numeric_limits<double>::max();
    while (! candidates.empty()) {
        HnswHit cand = candidates.top();
        if (cand.dist > limd) {
            break;
        }
        candidates.pop();
        for (uint32_t e_id : getLinkList(cand.docid, searchLevel)) {
            if (visited.isMarked(e_id)) continue;
            visited.mark(e_id);
            double e_dist = distance(vector, e_id);
            ++distcalls_search_layer;
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
HnswLikeNns::remove_weakest(const NearestList &neighbors, uint32_t curMax, LinkList &lost) const
{
    LinkList result;
    result.reserve(curMax+1);
    NearestPriQ w;
    for (const auto & entry : neighbors) {
        w.push(entry);
    }
    while (! w.empty()) {
        HnswHit e = w.top();
        w.pop();
        if (result.size() == curMax || haveCloserDistance(e, result)) {
            lost.push_back(e.docid);
        } else {
            result.push_back(e.docid);
        }
    }
    return result;
}

#ifdef NO_BACKFILL
LinkList
HnswLikeNns::select_neighbors(const NearestList &neighbors, uint32_t curMax) const
{
    LinkList result;
    result.reserve(curMax+1);
    bool needFiltering = (neighbors.size() > curMax);
    NearestPriQ w;
    for (const auto & entry : neighbors) {
        w.push(entry);
    }
    while (! w.empty()) {
        HnswHit e = w.top();
        w.pop();
        if (needFiltering && haveCloserDistance(e, result)) {
            continue;
        }
        result.push_back(e.docid);
        if (result.size() == curMax) return result;
    }
    return result;
}
#else
LinkList
HnswLikeNns::select_neighbors(const NearestList &neighbors, uint32_t curMax) const
{
    LinkList result;
    result.reserve(curMax+1);
    bool needFiltering = (neighbors.size() > curMax);
    NearestPriQ w;
    for (const auto & entry : neighbors) {
        w.push(entry);
    }
    LinkList backfill;
    while (! w.empty()) {
        HnswHit e = w.top();
        w.pop();
        if (needFiltering && haveCloserDistance(e, result)) {
            backfill.push_back(e.docid);
            continue;
        }
        result.push_back(e.docid);
        if (result.size() == curMax) return result;
    }
    if (result.size() * 4 < curMax) {
        for (uint32_t fill_id : backfill) {
            result.push_back(fill_id);
            if (result.size() * 4 >= curMax) break;
        }
    }
    return result;
}
#endif

void
HnswLikeNns::connect_new_node(uint32_t id, const LinkList &neighbors, uint32_t level) {
    LinkList &newLinks = getLinkList(id, level);
    for (uint32_t neigh_id : neighbors) {
        LinkList &oldLinks = getLinkList(neigh_id, level);
        newLinks.push_back(neigh_id);
        oldLinks.push_back(id);
    }
}

uint32_t
HnswLikeNns::count_reachable() const {
    VisitedSet visited(_nodes.size());
    int level = _entryLevel;
    LinkList curList;
    curList.push_back(_entryId);
    visited.mark(_entryId);
    uint32_t idx = 0;
    while (level >= 0) {
        while (idx < curList.size()) {
            uint32_t id = curList[idx++];
            const LinkList &links = getLinkList(id, level);
            for (uint32_t n_id : links) {
                if (visited.isMarked(n_id)) continue;
                visited.mark(n_id);
                curList.push_back(n_id);
            }
        }
        --level;
        idx = 0;
    }
    return curList.size();
}

void
HnswLikeNns::dumpStats() const {
    std::vector<uint32_t> levelCounts;
    levelCounts.resize(_entryLevel + 2);
    std::vector<uint32_t> outLinkHist;
    outLinkHist.resize(2 * _M + 2);
    uint32_t symmetrics = 0;
    uint32_t level1links = 0;
    uint32_t both_l_links = 0;
    fprintf(stderr, "stats for HnswLikeNns with %zu nodes, entry level = %d, entry id = %u\n",
            _nodes.size(), _entryLevel, _entryId);
    
    for (uint32_t id = 0; id < _nodes.size(); ++id) {
        const auto &node = _nodes[id];
        uint32_t levels = node._links.size();
        levelCounts[levels]++;
        if (levels < 1) {
            outLinkHist[0]++;
            continue;
        }
        const LinkList &link_list = getLinkList(id, 0);
        uint32_t numlinks = link_list.size();
        outLinkHist[numlinks]++;
        if (numlinks < 1) {
            fprintf(stderr, "node with %u links: id %u\n", numlinks, id);
        }
        bool all_sym = true;
        for (uint32_t n_id : link_list) {
            const LinkList &neigh_list = getLinkList(n_id, 0);
            if (! neigh_list.has_link_to(id)) {
                fprintf(stderr, "BAD: %u has link to neighbor %u, but backlink is missing\n", id, n_id);
                all_sym = false;
            }
        }
        if (all_sym) ++symmetrics;
        if (levels < 2) continue;
        const LinkList &link_list_1 = getLinkList(id, 1);
        for (uint32_t n_id : link_list_1) {
            ++level1links;
            if (link_list.has_link_to(n_id)) ++both_l_links;
        }
    }
    for (uint32_t l = 0; l < levelCounts.size(); ++l) {
        fprintf(stderr, "Nodes on %u levels: %u\n", l, levelCounts[l]);
    }
    fprintf(stderr, "reachable nodes %u / %zu\n",
            count_reachable(), _nodes.size() - levelCounts[0]);
    fprintf(stderr, "level 1 links overlapping on l0: %u / total: %u\n",
            both_l_links, level1links);
    for (uint32_t l = 0; l < outLinkHist.size(); ++l) {
        if (outLinkHist[l] != 0) {
            fprintf(stderr, "Nodes with %u outward links on L0: %u\n", l, outLinkHist[l]);
        }
    }
    fprintf(stderr, "Symmetric in-out nodes: %u\n", symmetrics);
}

std::unique_ptr<NNS<float>>
make_hnsw_nns(uint32_t numDims, const DocVectorAccess<float> &dva)
{
    return std::make_unique<HnswLikeNns>(numDims, dva);
}
