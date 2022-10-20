// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nns.h"
#include "std-random.h"
#include <assert.h>
#include <cinttypes>
#include <algorithm>
#include <queue>
#include <set>

using V = vespalib::ConstArrayRef<float>;
class AnnoyLikeNns;
inline namespace xpannoynns { struct Node; }

static size_t plane_dist_cnt = 0;
static size_t w_cen_dist_cnt = 0;
static size_t leaf_split_cnt = 0;
static size_t find_top_k_cnt = 0;
static size_t find_cand_cnt = 0;

using QueueNode = std::pair<double, Node *>;
using NodeQueue = std::priority_queue<QueueNode>;

inline namespace xpannoynns {

struct Node {
    Node() {}
    virtual ~Node() {}
    virtual Node *addDoc(uint32_t docid, V vector, AnnoyLikeNns &meta) = 0;
    virtual int remove(uint32_t docid, V vector) = 0;
    virtual void findCandidates(std::set<uint32_t> &cands, V vector, NodeQueue &queue, double minDist) const = 0;
    virtual void filterCandidates(std::set<uint32_t> &cands, V vector, NodeQueue &queue, double minDist, const BitVector &skipDocIds) const = 0;
    virtual void stats(std::vector<uint32_t> &depths) = 0;
};

}

struct LeafNode : public Node {
    std::vector<uint32_t> docids;

    LeafNode() : Node(), docids() { docids.reserve(128); }

    Node *addDoc(uint32_t docid, V vector, AnnoyLikeNns &meta) override;
    int remove(uint32_t docid, V vector) override;
    void findCandidates(std::set<uint32_t> &cands, V vector, NodeQueue &queue, double minDist) const override;
    void filterCandidates(std::set<uint32_t> &cands, V vector, NodeQueue &queue, double minDist, const BitVector &skipDocIds) const override;

    Node *split(AnnoyLikeNns &meta);
    virtual void stats(std::vector<uint32_t> &depths) override { depths.push_back(1); }
};

struct SplitNode : public Node {
    std::vector<float> hyperPlane;
    double offsetFromOrigo;
    Node *leftChildren;
    Node *rightChildren;

    SplitNode() : Node(), hyperPlane(), offsetFromOrigo(), leftChildren(), rightChildren() {}
    ~SplitNode();

    Node *addDoc(uint32_t docid, V vector, AnnoyLikeNns &meta) override;
    int remove(uint32_t docid, V vector) override;
    void findCandidates(std::set<uint32_t> &cands, V vector, NodeQueue &queue, double minDist) const override;
    void filterCandidates(std::set<uint32_t> &cands, V vector, NodeQueue &queue, double minDist, const BitVector &skipDocIds) const override;

    double planeDistance(V vector) const;
    virtual void stats(std::vector<uint32_t> &depths) override {
        size_t i = depths.size();
        leftChildren->stats(depths);
        rightChildren->stats(depths);
        while (i < depths.size()) { ++depths[i++]; }
    }
};

class AnnoyLikeNns : public NNS<float>
{
private:
    std::vector<Node *> _roots;
    RndGen _rndGen;
    static constexpr size_t numRoots = 50;

public:
    AnnoyLikeNns(uint32_t numDims, const DocVectorAccess<float> &dva)
        : NNS(numDims, dva), _roots(), _rndGen()
    {
        _roots.reserve(numRoots);
        for (size_t i = 0; i < numRoots; ++i) {
            _roots.push_back(new LeafNode());
        }
    }

    void dumpStats();

    ~AnnoyLikeNns() {
        dumpStats();
        for (Node *root : _roots) {
            delete root;
        }
    }

    void addDoc(uint32_t docid) override {
        V vector = _dva.get(docid);
        for (Node * &root : _roots) {
            root = root->addDoc(docid, vector, *this);
        }
    }

    void removeDoc(uint32_t docid) override {
        V vector = _dva.get(docid);
        for (Node * root : _roots) {
            root->remove(docid, vector);
        }
    }
    std::vector<NnsHit> topK(uint32_t k, Vector vector, uint32_t search_k) override;

    std::vector<NnsHit> topKfilter(uint32_t k, Vector vector, uint32_t search_k, const BitVector &bitvector) override;

    V getVector(uint32_t docid) const { return _dva.get(docid); }
    double uniformRnd() { return _rndGen.nextUniform(); } 
    uint32_t dims() const { return _numDims; }
};


double
SplitNode::planeDistance(V vector) const
{
    ++plane_dist_cnt;
    assert(vector.size() == hyperPlane.size());
    double dp = l2distCalc.product(&vector[0], &hyperPlane[0], vector.size());
    return dp - offsetFromOrigo;
}


Node *
LeafNode::addDoc(uint32_t docid, V, AnnoyLikeNns &meta)
{
    docids.push_back(docid);
    if (docids.size() > 127) {
        return split(meta);
    }
    return this;
}

struct WeightedCentroid {
    uint32_t cnt;
    std::vector<float> sum_point;
    std::vector<float> tmp_vector;
    WeightedCentroid(V vector)
        : cnt(1), sum_point(), tmp_vector(vector.size())
    {
        sum_point.reserve(vector.size());
        for (float val : vector) {
            sum_point.push_back(val);
        }
    }
    void add_v(V vector) {
        ++cnt;
        for (size_t i = 0; i < vector.size(); ++i) {
            sum_point[i] += vector[i];
        }
    }
    std::vector<float> norm_diff(WeightedCentroid other) {
        std::vector<float> r;
        const size_t sz = sum_point.size();
        double my_inv = 1.0 / cnt;
        double ot_inv = 1.0 / other.cnt;
        double sumSq = 0.0;
        r.reserve(sz);
        for (size_t i = 0; i < sz; ++i) {
            double d = (sum_point[i] * my_inv) - (other.sum_point[i] * ot_inv);
            r.push_back(d);
            sumSq += d*d;
        }
        if (sumSq > 0) {
            double invnorm = 1.0 / sqrt(sumSq);
            for (size_t i = 0; i < sz; ++i) {
                r[i] *= invnorm;
            }
        }
        return r;
    }
    std::vector<float> midpoint(WeightedCentroid other) {
        std::vector<float> r;
        size_t sz = sum_point.size();
        r.reserve(sz);
        double my_inv = 1.0 / cnt;
        double ot_inv = 1.0 / other.cnt;
        for (size_t i = 0; i < sz; ++i) {
            double mp = (sum_point[i] * my_inv) + (other.sum_point[i] * ot_inv);
            r.push_back(mp * 0.5);
        }
        return r;
    }
    double weightedDistance(V vector) {
        ++w_cen_dist_cnt;
        size_t sz = vector.size();
        for (size_t i = 0; i < sz; ++i) {
            tmp_vector[i] = vector[i] * cnt;
        }
        return l2distCalc.l2sq_dist(tmp_vector, sum_point) / cnt;
    }
    ~WeightedCentroid() {}
};

Node *
LeafNode::split(AnnoyLikeNns &meta)
{
    ++leaf_split_cnt;
    uint32_t dims = meta.dims();
    uint32_t retries = 3;
retry:
    uint32_t p1i = uint32_t(meta.uniformRnd() * docids.size());
    uint32_t p2i = uint32_t(meta.uniformRnd() * (docids.size()-1));
    if (p2i >= p1i) ++p2i;
    uint32_t p1d = docids[p1i];
    uint32_t p2d = docids[p2i];
    V p1 = meta.getVector(p1d);
    V p2 = meta.getVector(p2d);

    double sumsq = 0;
    for (size_t i = 0; i < dims; ++i) {
        double d = p1[i] - p2[i];
        sumsq += d*d;
    }
    if ((!(sumsq > 0)) && (retries-- > 0)) {
        goto retry;
    }
    WeightedCentroid centroid1(p1);
    WeightedCentroid centroid2(p2);
#if 1
    for (size_t i = 0; (i * 1) < docids.size(); ++i) {
        size_t p3i = (p1i + p2i + i) % docids.size();
        uint32_t p3d = docids[p3i];
        V p3 = meta.getVector(p3d);
        double dist_c1 = centroid1.weightedDistance(p3);
        double dist_c2 = centroid2.weightedDistance(p3);
        bool use_c1 = false;
        if (dist_c1 < dist_c2) {
            use_c1 = true;
        } else if (dist_c1 > dist_c2) {
            use_c1 = false;
        } else if (centroid1.cnt < centroid2.cnt) {
            use_c1 = true;
        }
        if (use_c1) {
            centroid1.add_v(p3);
        } else {
            centroid2.add_v(p3);
        }
    }
#endif
    std::vector<float> diff = centroid1.norm_diff(centroid2);
    std::vector<float> mp = centroid1.midpoint(centroid2);
    double off = l2distCalc.product(diff, mp);

    SplitNode *s = new SplitNode();
    s->hyperPlane = std::move(diff);
    s->offsetFromOrigo = off;

    std::vector<uint32_t> leftDs;
    std::vector<uint32_t> rightDs;
    leftDs.reserve(128);
    rightDs.reserve(128);

    for (uint32_t docid : docids) {
        V vector = meta.getVector(docid);
        double dist = s->planeDistance(vector);
        bool left = false;
        if (dist < 0) {
            left = true;
        } else if (!(dist > 0)) {
            left = (leftDs.size() < rightDs.size());
        }
        if (left) {
            leftDs.push_back(docid);
        } else {
            rightDs.push_back(docid);
        }
    }

#if 0
    fprintf(stderr, "splitting leaf node numChildren %u\n", numChildren);
    fprintf(stderr, "dims = %u\n", dims);
    fprintf(stderr, "p1 idx=%u, docid=%u VSZ=%zu\n", p1i, p1d, p1.size());
    fprintf(stderr, "p2 idx=%u, docid=%u VSZ=%zu\n", p2i, p2d, p2.size());
    fprintf(stderr, "diff %zu sumsq = %g\n", diff.size(), sumsq);
    fprintf(stderr, "offset from origo = %g\n", off);
    fprintf(stderr, "split left=%zu, right=%zu\n", leftDs.size(), rightDs.size());
#endif

    LeafNode *newRightNode = new LeafNode();
    newRightNode->docids = std::move(rightDs);
    s->rightChildren = newRightNode;
    this->docids = std::move(leftDs);
    s->leftChildren = this;
    return s;
}

int
LeafNode::remove(uint32_t docid, V)
{
    auto iter = std::remove(docids.begin(), docids.end(), docid);
    int removed = docids.end() - iter;
    docids.erase(iter, docids.end());
    return removed;
}

void
LeafNode::findCandidates(std::set<uint32_t> &cands, V, NodeQueue &, double) const
{
    for (uint32_t d : docids) {
        cands.insert(d);
    }
}

void
LeafNode::filterCandidates(std::set<uint32_t> &cands, V, NodeQueue &, double, const BitVector &skipDocIds) const
{
    for (uint32_t d : docids) {
        if (skipDocIds.isSet(d)) continue;
        cands.insert(d);
    }
}


SplitNode::~SplitNode()
{
    delete leftChildren;
    delete rightChildren;
}

Node *
SplitNode::addDoc(uint32_t docid, V vector, AnnoyLikeNns &meta)
{
    double d = planeDistance(vector);
    if (d < 0) {
        leftChildren = leftChildren->addDoc(docid, vector, meta);
    } else {
        rightChildren = rightChildren->addDoc(docid, vector, meta);
    }
    return this;
}

int
SplitNode::remove(uint32_t docid, V vector)
{
    double d = planeDistance(vector);
    if (d < 0) {
        int r = leftChildren->remove(docid, vector);
        return r;
    } else {
        int r = rightChildren->remove(docid, vector);
        return r;
    }
}

void
SplitNode::findCandidates(std::set<uint32_t> &, V vector, NodeQueue &queue, double minDist) const
{
    double d = planeDistance(vector);
    // fprintf(stderr, "push 2 nodes dist %g\n", d);
    queue.push(std::make_pair(std::min(-d, minDist), leftChildren));
    queue.push(std::make_pair(std::min(d, minDist), rightChildren));
}

void
SplitNode::filterCandidates(std::set<uint32_t> &, V vector, NodeQueue &queue, double minDist, const BitVector &) const
{
    double d = planeDistance(vector);
    // fprintf(stderr, "push 2 nodes dist %g\n", d);
    queue.push(std::make_pair(std::min(-d, minDist), leftChildren));
    queue.push(std::make_pair(std::min(d, minDist), rightChildren));
}

std::vector<NnsHit>
AnnoyLikeNns::topK(uint32_t k, Vector vector, uint32_t search_k)
{
    ++find_top_k_cnt;
    std::vector<float> tmp;
    tmp.resize(_numDims);
    vespalib::ArrayRef<float> tmpArr(tmp);

    std::vector<NnsHit> r;
    r.reserve(k);
    std::set<uint32_t> candidates;
    NodeQueue queue;
    // fprintf(stderr, "find %u candidates\n", k);
    for (Node *root : _roots) {
        double dist = std::numeric_limits<double>::max();
        queue.push(std::make_pair(dist, root));
    }
    while ((candidates.size() < std::max(k, search_k)) && (queue.size() > 0)) {
        const QueueNode& top = queue.top();
        double md = top.first;
        // fprintf(stderr, "find candidates: node with min distance %g\n", md);
        Node *n = top.second;
        queue.pop();
        n->findCandidates(candidates, vector, queue, md);
        ++find_cand_cnt;
    }
#if 0
    while (queue.size() > 0) {
        const QueueNode& top = queue.top();
        fprintf(stderr, "discard candidates: node with distance %g\n", top.first);
        queue.pop();
    }
#endif
    for (uint32_t docid : candidates) {
        double dist = l2distCalc.l2sq_dist(vector, _dva.get(docid), tmpArr);
        NnsHit hit(docid, SqDist(dist));
        r.push_back(hit);
    }
    std::sort(r.begin(), r.end(), NnsHitComparatorLessDistance());
    while (r.size() > k) r.pop_back();
    return r;
}

std::vector<NnsHit>
AnnoyLikeNns::topKfilter(uint32_t k, Vector vector, uint32_t search_k, const BitVector &skipDocIds)
{
    ++find_top_k_cnt;
    std::vector<NnsHit> r;
    r.reserve(k);
    std::set<uint32_t> candidates;
    NodeQueue queue;
    for (Node *root : _roots) {
        double dist = std::numeric_limits<double>::max();
        queue.push(std::make_pair(dist, root));
    }
    while ((candidates.size() < std::max(k, search_k)) && (queue.size() > 0)) {
        const QueueNode& top = queue.top();
        double md = top.first;
        // fprintf(stderr, "find candidates: node with min distance %g\n", md);
        Node *n = top.second;
        queue.pop();
        n->filterCandidates(candidates, vector, queue, md, skipDocIds);
        ++find_cand_cnt;
    }
    for (uint32_t docid : candidates) {
        if (skipDocIds.isSet(docid)) continue;
        double dist = l2distCalc.l2sq_dist(vector, _dva.get(docid));
        NnsHit hit(docid, SqDist(dist));
        r.push_back(hit);
    }
    std::sort(r.begin(), r.end(), NnsHitComparatorLessDistance());
    while (r.size() > k) r.pop_back();
    return r;
}



void
AnnoyLikeNns::dumpStats() {
    fprintf(stderr, "stats for AnnoyLikeNns:\n");
    fprintf(stderr, "planeDistance() calls: %zu\n", plane_dist_cnt);
    fprintf(stderr, "weightedDistance() calls: %zu\n", w_cen_dist_cnt);
    fprintf(stderr, "leaf split() calls: %zu\n", leaf_split_cnt);
    fprintf(stderr, "topK() calls: %zu\n", find_top_k_cnt);
    fprintf(stderr, "findCandidates() calls: %zu\n", find_cand_cnt);
    std::vector<uint32_t> depths;
    _roots[0]->stats(depths);
    std::vector<uint32_t> counts;
    for (uint32_t deep : depths) {
        while (counts.size() <= deep) counts.push_back(0);
        counts[deep]++;
    }
    fprintf(stderr, "depths for %zu leaves [\n", depths.size());
    for (uint32_t deep = 0; deep < counts.size(); ++deep) {
        if (counts[deep] > 0) {
            fprintf(stderr, "%u deep count %u\n", deep, counts[deep]);
        }
    }
    fprintf(stderr, "]\n");
}

std::unique_ptr<NNS<float>>
make_annoy_nns(uint32_t numDims, const DocVectorAccess<float> &dva)
{
    return std::make_unique<AnnoyLikeNns>(numDims, dva);
}
