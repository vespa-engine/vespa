// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nns.h"
#include "std-random.h"
#include <assert.h>
#include <string.h>
#include <algorithm>
#include <queue>
#include <set>
#include <vespa/vespalib/util/priority_queue.h>

using V = vespalib::ConstArrayRef<float>;

#define NUM_HASH_WORDS 4
#define IGNORE_BITS 32
#define HIST_SIZE (64*NUM_HASH_WORDS + 1)

struct LsMaskHash {
    uint64_t bits[NUM_HASH_WORDS];
    uint64_t mask[NUM_HASH_WORDS];
    LsMaskHash() {
        memset(bits, 0xff, sizeof bits); 
        memset(mask, 0xff, sizeof mask); 
    }
};

static inline int hash_dist(const LsMaskHash &h1, const LsMaskHash &h2) {
    int cnt = 0;
    for (size_t o = 0; o < NUM_HASH_WORDS; ++o) {
        uint64_t hx = h1.bits[o] ^ h2.bits[o];
        hx &= (h1.mask[o] | h2.mask[o]);
        cnt += __builtin_popcountl(hx);
    }
    return cnt;
}

struct Multiplier {
    std::vector<float> multiplier;
    Multiplier(size_t dims) : multiplier(dims, 0.0) {}
};

LsMaskHash mask_hash_from_pv(V p, std::vector<Multiplier> rpMatrix) {
    LsMaskHash result;
    float transformed[NUM_HASH_WORDS][64];
    std::vector<double> squares;
    for (size_t o = 0; o < NUM_HASH_WORDS; ++o) {
        uint64_t hash = 0;
        for (size_t bit = 0; bit < 64; ++bit) {
            hash <<= 1u;
            V m = rpMatrix[bit+64*o].multiplier;
            double dotproduct = l2distCalc.product(m, p);
            if (dotproduct > 0.0) {
                hash |= 1u;
            }
            double sq = dotproduct * dotproduct;
            transformed[o][bit] = sq;
            squares.push_back(sq);
        }
        result.bits[o] = hash;
    }
    std::sort(squares.begin(), squares.end());
    double lim = squares[IGNORE_BITS*NUM_HASH_WORDS-1];
    for (size_t o = 0; o < NUM_HASH_WORDS; ++o) {
        uint64_t mask = 0;
        for (size_t bit = 0; bit < 64; ++bit) {
            mask <<= 1u;
            if (transformed[o][bit] > lim) {
                mask |= 1u;
            }
        }
        result.mask[o] = mask;
    }
    return result;
}

class RpLshNns : public NNS<float>
{
private:
    RndGen _rndGen;
    std::vector<Multiplier> _transformationMatrix;
    std::vector<LsMaskHash> _generated_doc_hashes;

public:
    RpLshNns(uint32_t numDims, const DocVectorAccess<float> &dva)
        : NNS(numDims, dva), _rndGen()
    {
        _transformationMatrix.reserve(NUM_HASH_WORDS*64);
        for (size_t i = 0; i < NUM_HASH_WORDS*64; i++) {
            _transformationMatrix.emplace_back(numDims);
            Multiplier &mult = _transformationMatrix.back();
            for (float &v : mult.multiplier) {
                v = _rndGen.nextNormal();
            }
        }
        fprintf(stderr, "ignore bits for lsh: %d*%d=%d\n",
                IGNORE_BITS, NUM_HASH_WORDS, IGNORE_BITS*NUM_HASH_WORDS);
        _generated_doc_hashes.reserve(100000);
    }

    ~RpLshNns() {
    }

    void addDoc(uint32_t docid) override {
        V vector = _dva.get(docid);
        LsMaskHash hash = mask_hash_from_pv(vector, _transformationMatrix);
        if (_generated_doc_hashes.size() == docid) {
            _generated_doc_hashes.push_back(hash);
            return;
        }
        while (_generated_doc_hashes.size() <= docid) {
            _generated_doc_hashes.push_back(LsMaskHash());
        }
        _generated_doc_hashes[docid] = hash;
    }
    void removeDoc(uint32_t docid) override {
        if (_generated_doc_hashes.size() > docid) {
            _generated_doc_hashes[docid] = LsMaskHash();
        }
    }
    std::vector<NnsHit> topK(uint32_t k, Vector vector, uint32_t search_k) override;
    std::vector<NnsHit> topKfilter(uint32_t k, Vector vector, uint32_t search_k, const BitVector &bitvector) override;

    V getVector(uint32_t docid) const { return _dva.get(docid); }
    double uniformRnd() { return _rndGen.nextUniform(); } 
    uint32_t dims() const { return _numDims; }
};


struct LshHit {
    double distance;
    uint32_t docid;
    int hash_distance;
    LshHit() noexcept : distance(0.0), docid(0u), hash_distance(0) {}
    LshHit(int id, double dist, int hd = 0)
        : distance(dist), docid(id), hash_distance(hd) {}
};

struct LshHitComparator {
    bool operator() (const LshHit &lhs, const LshHit& rhs) const {
        if (lhs.distance < rhs.distance) return false;
        if (lhs.distance > rhs.distance) return true;
        return (lhs.docid > rhs.docid);
    }
};

class LshHitHeap {
private:
    size_t _size;
    vespalib::PriorityQueue<LshHit, LshHitComparator> _priQ;
    std::vector<int> hd_histogram;
public:
    explicit LshHitHeap(size_t maxSize) : _size(maxSize), _priQ() {
        _priQ.reserve(maxSize);
    }
    ~LshHitHeap() {}
    bool maybe_use(const LshHit &hit) {
        if (_priQ.size() < _size) {
            _priQ.push(hit);
            uint32_t newHd = hit.hash_distance;
            while (hd_histogram.size() <= newHd) {
                hd_histogram.push_back(0);
            }
            hd_histogram[newHd]++;
        } else if (hit.distance < _priQ.front().distance) {
            uint32_t oldHd = _priQ.front().hash_distance;
            uint32_t newHd = hit.hash_distance;
            while (hd_histogram.size() <= newHd) {
                hd_histogram.push_back(0);
            }
            hd_histogram[newHd]++;
            hd_histogram[oldHd]--;
            _priQ.front() = hit;
            _priQ.adjust();
            return true;
        }
        return false;
    }
    int limitHashDistance() {
        size_t sz = _priQ.size();
        uint32_t sum = 0;
        for (uint32_t i = 0; i < hd_histogram.size(); ++i) {
            sum += hd_histogram[i];
            if (sum >= ((3*sz)/4)) return i;
        }
        return 99999;
    }
    std::vector<LshHit> bestLshHits() {
        std::vector<LshHit> result;
        size_t sz = _priQ.size();
        result.resize(sz);
        for (size_t i = sz; i-- > 0; ) {
            result[i] = _priQ.front();
            _priQ.pop_front();
        }
        return result;
    }
};

std::vector<NnsHit>
RpLshNns::topKfilter(uint32_t k, Vector vector, uint32_t search_k, const BitVector &skipDocIds)
{
    std::vector<NnsHit> result;
    result.reserve(k);

    std::vector<float> tmp(_numDims);
    vespalib::ArrayRef<float> tmpArr(tmp);

    LsMaskHash query_hash = mask_hash_from_pv(vector, _transformationMatrix);
    LshHitHeap heap(std::max(k, search_k));
    int limit_hash_dist = 99999;
    size_t docidLimit = _generated_doc_hashes.size();
    for (uint32_t docid = 0; docid < docidLimit; ++docid) {
        if (skipDocIds.isSet(docid)) continue;
        int hd = hash_dist(query_hash, _generated_doc_hashes[docid]);
        if (hd <= limit_hash_dist) {
            double dist = l2distCalc.l2sq_dist(vector, _dva.get(docid), tmpArr);
            LshHit h(docid, dist, hd);
            if (heap.maybe_use(h)) {
                limit_hash_dist = heap.limitHashDistance();
            }
        }
    }
    std::vector<LshHit> best = heap.bestLshHits();
    size_t numHits = std::min((size_t)k, best.size());
    for (size_t i = 0; i < numHits; ++i) {
        result.emplace_back(best[i].docid, SqDist(best[i].distance));
    }
    return result;
}

std::vector<NnsHit>
RpLshNns::topK(uint32_t k, Vector vector, uint32_t search_k)
{
    std::vector<NnsHit> result;
    result.reserve(k);

    std::vector<float> tmp(_numDims);
    vespalib::ArrayRef<float> tmpArr(tmp);

    LsMaskHash query_hash = mask_hash_from_pv(vector, _transformationMatrix);
    LshHitHeap heap(std::max(k, search_k));
    int limit_hash_dist = 99999;
    int histogram[HIST_SIZE];
    memset(histogram, 0, sizeof histogram);
    size_t docidLimit = _generated_doc_hashes.size();
    for (uint32_t docid = 0; docid < docidLimit; ++docid) {
        int hd = hash_dist(query_hash, _generated_doc_hashes[docid]);
        histogram[hd]++;
        if (hd <= limit_hash_dist) {
            double dist = l2distCalc.l2sq_dist(vector, _dva.get(docid), tmpArr);
            LshHit h(docid, dist, hd);
            if (heap.maybe_use(h)) {
                limit_hash_dist = heap.limitHashDistance();
            }
        }
    }
    std::vector<LshHit> best = heap.bestLshHits();
    size_t numHits = std::min((size_t)k, best.size());
    for (size_t i = 0; i < numHits; ++i) {
        result.emplace_back(best[i].docid, SqDist(best[i].distance));
    }
    return result;
}

std::unique_ptr<NNS<float>>
make_rplsh_nns(uint32_t numDims, const DocVectorAccess<float> &dva)
{
    return std::make_unique<RpLshNns>(numDims, dva);
}
