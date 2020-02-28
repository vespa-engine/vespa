// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

std::vector<TopK> bruteforceResults;

double computeDistance(const PointVector &query, uint32_t docid) {
    const PointVector &docvector = generatedDocs[docid];
    return l2distCalc.l2sq_dist(query, docvector);
}

struct BfHitComparator {
    bool operator() (const Hit &lhs, const Hit& rhs) const {
        if (lhs.distance < rhs.distance) return false;
        if (lhs.distance > rhs.distance) return true;
        return (lhs.docid > rhs.docid);
    }
};

class BfHitHeap {
private:
    size_t _size;
    vespalib::PriorityQueue<Hit, BfHitComparator> _priQ;
public:
    explicit BfHitHeap(size_t maxSize) : _size(maxSize), _priQ() {
        _priQ.reserve(maxSize);
    }
    ~BfHitHeap() {}
    void maybe_use(const Hit &hit) {
        if (_priQ.size() < _size) {
            _priQ.push(hit);
        } else if (hit.distance < _priQ.front().distance) {
            _priQ.front() = hit;
            _priQ.adjust();
        }
    }
    std::vector<Hit> bestHits() {
        std::vector<Hit> result;
        size_t i = _priQ.size();
        result.resize(i);
        while (i-- > 0) {
            result[i] = _priQ.front();
            _priQ.pop_front();
        }
        return result;
    }
};

TopK bruteforce_nns(const PointVector &query) {
    TopK result;
    BfHitHeap heap(result.K);
    for (uint32_t docid = 0; docid < EFFECTIVE_DOCS; ++docid) {
        const PointVector &docvector = generatedDocs[docid];
        double d = l2distCalc.l2sq_dist(query, docvector);
        Hit h(docid, d);
        heap.maybe_use(h);
    }
    std::vector<Hit> best = heap.bestHits();
    for (size_t i = 0; i < result.K; ++i) {
        result.hits[i] = best[i];
    }
    return result;
}

void verifyBF(uint32_t qid) {
    const PointVector &query = generatedQueries[qid];
    TopK &result = bruteforceResults[qid];
    double min_distance = result.hits[0].distance;
    for (uint32_t i = 0; i < EFFECTIVE_DOCS; ++i) {
        double dist = computeDistance(query, i);
        if (dist < min_distance) {
            fprintf(stderr, "WARN dist %.9g < mindist %.9g\n", dist, min_distance);
        }
        EXPECT_FALSE(dist+0.000001 < min_distance);
    }
}
