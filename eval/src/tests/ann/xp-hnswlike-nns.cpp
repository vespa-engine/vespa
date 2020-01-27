// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <algorithm>
#include <assert.h>
#include <queue>
#include "std-random.h"
#include "nns.h"

static uint64_t distcalls_simple;
static uint64_t distcalls_search_layer;
static uint64_t distcalls_other;
static uint64_t distcalls_heuristic;
static uint64_t distcalls_shrink;
static uint64_t distcalls_refill;
static uint64_t refill_needed_calls;

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
          _rndGen()
    {
        _nodes.reserve(1234567);
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
        if (_nodes.size() % 10000 == 0) {
            double div = _nodes.size();
            fprintf(stderr, "added docs: %d\n", (int)div);
            fprintf(stderr, "distance calls for layer: %zu is %.3f per doc\n", distcalls_search_layer, distcalls_search_layer/ div);
            fprintf(stderr, "distance calls for heuristic: %zu is %.3f per doc\n", distcalls_heuristic, distcalls_heuristic / div);
            fprintf(stderr, "distance calls for simple: %zu is %.3f per doc\n", distcalls_simple, distcalls_simple / div);
            fprintf(stderr, "distance calls for shrink: %zu is %.3f per doc\n", distcalls_shrink, distcalls_shrink / div);
            fprintf(stderr, "distance calls for refill: %zu is %.3f per doc\n", distcalls_refill, distcalls_refill / div);
            fprintf(stderr, "distance calls for other: %zu is %.3f per doc\n", distcalls_other, distcalls_other / div);
            fprintf(stderr, "refill needed calls: %zu is %.3f per doc\n", refill_needed_calls, refill_needed_calls / div);
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
                if (other_links.size() >= _M) continue;
                other_links.push_back(my_id);
                my_links.push_back(repl_id);
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
            const LinkList &my_links = node._links[level];
            for (uint32_t n_id : my_links) {
                if (need_new_entrypoint) {
                    _entryId = n_id;
                    _entryLevel = level;
		    need_new_entrypoint = false;
                }
                remove_link_from(n_id, docid, level);
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
    }

    std::vector<NnsHit> topK(uint32_t k, Vector vector, uint32_t search_k) override {
        std::vector<NnsHit> result;
        if (_entryLevel < 0) return result;
        double entryDist = distance(vector, _entryId);
        ++distcalls_other;
        HnswHit entryPoint(_entryId, SqDist(entryDist));
        int searchLevel = _entryLevel;
        while (searchLevel > 0) {
            entryPoint = search_layer_simple(vector, entryPoint, searchLevel);
            --searchLevel;
        }
        FurthestPriQ w;
        w.push(entryPoint);
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

void
HnswLikeNns::dumpStats() const {
    std::vector<uint32_t> inLinkCounters;
    inLinkCounters.resize(_nodes.size());
    std::vector<uint32_t> outLinkCounters;
    outLinkCounters.resize(_nodes.size());
    std::vector<uint32_t> levelCounts;
    levelCounts.resize(_entryLevel + 2);
    std::vector<uint32_t> outLinkHist;
    outLinkHist.resize(2 * _M + 2);
    fprintf(stderr, "stats for HnswLikeNns with %zu nodes, entry level = %d, entry id = %u\n",
            _nodes.size(), _entryLevel, _entryId);
    for (uint32_t id = 0; id < _nodes.size(); ++id) {
        const auto &node = _nodes[id];
        uint32_t levels = node._links.size();
        levelCounts[levels]++;
        if (levels < 1) {
            outLinkCounters[id] = 0;
            outLinkHist[0]++;
            continue;
        }
        const LinkList &link_list = getLinkList(id, 0);
        uint32_t numlinks = link_list.size();
        outLinkCounters[id] = numlinks;
        outLinkHist[numlinks]++;
        if (numlinks < 2) {
            fprintf(stderr, "node with %u links: id %u\n", numlinks, id);
            for (uint32_t n_id : link_list) {
                const LinkList &neigh_list = getLinkList(n_id, 0);
                fprintf(stderr, "neighbor id %u has %zu links\n", n_id, neigh_list.size());
                if (! neigh_list.has_link_to(id)) {
                    fprintf(stderr, "BAD neighbor %u is missing backlink\n", n_id);
                }
            }
        }
        for (uint32_t n_id : link_list) {
            inLinkCounters[n_id]++;
        }
    }
    for (uint32_t l = 0; l < levelCounts.size(); ++l) {
        fprintf(stderr, "Nodes on %u levels: %u\n", l, levelCounts[l]);
    }
    for (uint32_t l = 0; l < outLinkHist.size(); ++l) {
        fprintf(stderr, "Nodes with %u outward links on L0: %u\n", l, outLinkHist[l]);
    }
    uint32_t symmetrics = 0;
    std::vector<uint32_t> inLinkHist;
    for (uint32_t id = 0; id < _nodes.size(); ++id) {
        uint32_t cnt = inLinkCounters[id];
        while (cnt >= inLinkHist.size()) inLinkHist.push_back(0);
        inLinkHist[cnt]++;
        if (cnt == outLinkCounters[id]) ++symmetrics;
    }
    for (uint32_t l = 0; l < inLinkHist.size(); ++l) {
        fprintf(stderr, "Nodes with %u inward links on L0: %u\n", l, inLinkHist[l]);
    }
    fprintf(stderr, "Symmetric in-out nodes: %u\n", symmetrics);
}


std::unique_ptr<NNS<float>>
make_hnsw_nns(uint32_t numDims, const DocVectorAccess<float> &dva)
{
    return std::make_unique<HnswLikeNns>(numDims, dva);
}
