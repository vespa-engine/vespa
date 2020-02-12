// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <chrono>

#define NUM_DIMS 960
#define NUM_DOCS 200000
#define NUM_REACH 10000
#define NUM_Q 1000

#include "doc_vector_access.h"
#include "nns.h"
#include "for-sift-hit.h"
#include "for-sift-top-k.h"

std::vector<TopK> bruteforceResults;

struct PointVector {
    float v[NUM_DIMS];
    using ConstArr = vespalib::ConstArrayRef<float>;
    operator ConstArr() const { return ConstArr(v, NUM_DIMS); }
};

static PointVector *aligned_alloc(size_t num) {
    size_t num_bytes = num * sizeof(PointVector);
    double mega_bytes = num_bytes / (1024.0*1024.0);
    fprintf(stderr, "allocate %.2f MB of vectors\n", mega_bytes);
    char *mem = (char *)malloc(num_bytes + 512);
    mem += 512;
    size_t val = (size_t)mem;
    size_t unalign = val % 512;
    mem -= unalign;
    return reinterpret_cast<PointVector *>(mem);
}

static PointVector *generatedQueries = aligned_alloc(NUM_Q);
static PointVector *generatedDocs = aligned_alloc(NUM_DOCS);

struct DocVectorAdapter : public DocVectorAccess<float>
{
    vespalib::ConstArrayRef<float> get(uint32_t docid) const override {
        ASSERT_TRUE(docid < NUM_DOCS);
        return generatedDocs[docid];
    }
};

double computeDistance(const PointVector &query, uint32_t docid) {
    const PointVector &docvector = generatedDocs[docid];
    return l2distCalc.l2sq_dist(query, docvector);
}

void read_queries(std::string fn) {
    int fd = open(fn.c_str(), O_RDONLY);
    ASSERT_TRUE(fd > 0);
    int d;
    size_t rv;
    fprintf(stderr, "reading %u queries from %s\n", NUM_Q, fn.c_str());
    for (uint32_t qid = 0; qid < NUM_Q; ++qid) {
        rv = read(fd, &d, 4);
        ASSERT_EQUAL(rv, 4u);
        ASSERT_EQUAL(d, NUM_DIMS);
        rv = read(fd, &generatedQueries[qid].v, NUM_DIMS*sizeof(float));
        ASSERT_EQUAL(rv, sizeof(PointVector));
    }
    close(fd);
}

void read_docs(std::string fn) {
    int fd = open(fn.c_str(), O_RDONLY);
    ASSERT_TRUE(fd > 0);
    int d;
    size_t rv;
    fprintf(stderr, "reading %u doc vectors from %s\n", NUM_DOCS, fn.c_str());
    for (uint32_t docid = 0; docid < NUM_DOCS; ++docid) {
        rv = read(fd, &d, 4);
        ASSERT_EQUAL(rv, 4u);
        ASSERT_EQUAL(d, NUM_DIMS);
        rv = read(fd, &generatedDocs[docid].v, NUM_DIMS*sizeof(float));
        ASSERT_EQUAL(rv, sizeof(PointVector));
    }
    close(fd);
}

using TimePoint = std::chrono::steady_clock::time_point;
using Duration = std::chrono::steady_clock::duration;

double to_ms(Duration elapsed) {
    std::chrono::duration<double, std::milli> ms(elapsed);
    return ms.count();
}

void read_data(std::string dir) {
    TimePoint bef = std::chrono::steady_clock::now();
    read_queries(dir + "/gist_query.fvecs");
    TimePoint aft = std::chrono::steady_clock::now();
    fprintf(stderr, "read queries: %.3f ms\n", to_ms(aft - bef));
    bef = std::chrono::steady_clock::now();
    read_docs(dir + "/gist_base.fvecs");
    aft = std::chrono::steady_clock::now();
    fprintf(stderr, "read docs: %.3f ms\n", to_ms(aft - bef));
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
    for (uint32_t docid = 0; docid < NUM_DOCS; ++docid) {
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
    std::vector<double> all_c2;
    for (uint32_t i = 0; i < NUM_DOCS; ++i) {
        double dist = computeDistance(query, i);
        if (dist < min_distance) {
            fprintf(stderr, "WARN dist %.9g < mindist %.9g\n", dist, min_distance);
        }
        EXPECT_FALSE(dist+0.000001 < min_distance);
        if (min_distance > 0.0) all_c2.push_back(dist / min_distance);
    }
    if (all_c2.size() != NUM_DOCS) return;
    std::sort(all_c2.begin(), all_c2.end());
    for (uint32_t idx : { 1, 3, 10, 30, 100, 300, 1000, 3000, NUM_DOCS/2, NUM_DOCS-1}) {
        fprintf(stderr, "c2-factor[%u] = %.3f\n", idx, all_c2[idx]);
    }
}

using NNS_API = NNS<float>;

TEST("require that brute force works") {
    TimePoint bef = std::chrono::steady_clock::now();
    fprintf(stderr, "generating %u brute force results\n", NUM_Q);
    bruteforceResults.reserve(NUM_Q);
    for (uint32_t cnt = 0; cnt < NUM_Q; ++cnt) {
        const PointVector &query = generatedQueries[cnt];
        bruteforceResults.emplace_back(bruteforce_nns(query));
    }
    TimePoint aft = std::chrono::steady_clock::now();
    fprintf(stderr, "timing for brute force: %.3f ms = %.3f ms per query\n",
            to_ms(aft - bef), to_ms(aft - bef)/NUM_Q);
    for (int cnt = 0; cnt < NUM_Q; cnt = (cnt+1)*2) {
        verifyBF(cnt);
    }
}

#include "find-with-nns.h"
#include "verify-top-k.h"

void timing_nns(const char *name, NNS_API &nns, std::vector<uint32_t> sk_list) {
    for (uint32_t search_k : sk_list) {
        TimePoint bef = std::chrono::steady_clock::now();
        for (int cnt = 0; cnt < NUM_Q; ++cnt) {
            find_with_nns(search_k, nns, cnt);
        }
        TimePoint aft = std::chrono::steady_clock::now();
        fprintf(stderr, "timing for %s search_k=%u: %.3f ms = %.3f ms/q\n",
                name, search_k, to_ms(aft - bef), to_ms(aft - bef)/NUM_Q);
    }
}

#include "quality-nns.h"

template <typename FUNC>
void bm_nns_simple(const char *name, FUNC creator, std::vector<uint32_t> sk_list) {
    std::unique_ptr<NNS_API> nnsp = creator();
    NNS_API &nns = *nnsp;
    fprintf(stderr, "trying %s indexing...\n", name);
    TimePoint bef = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < NUM_DOCS; ++i) {
        nns.addDoc(i);
    }
    TimePoint aft = std::chrono::steady_clock::now();
    fprintf(stderr, "build %s index with %u docs: %.3f ms\n", name, NUM_DOCS, to_ms(aft - bef));
    timing_nns(name, nns, sk_list);
    fprintf(stderr, "Quality for %s [A] clean build with %u documents:\n", name, NUM_DOCS);
    quality_nns(nns, sk_list);
}

template <typename FUNC>
void benchmark_nns(const char *name, FUNC creator, std::vector<uint32_t> sk_list) {
    bm_nns_simple(name, creator, sk_list);
}

#if 0
TEST("require that Locality Sensitive Hashing mostly works") {
    DocVectorAdapter adapter;
    auto creator = [&adapter]() { return make_rplsh_nns(NUM_DIMS, adapter); };
    benchmark_nns("RPLSH", creator, { 200, 1000 });
}
#endif

#if 0
TEST("require that Annoy via NNS api mostly works") {
    DocVectorAdapter adapter;
    auto creator = [&adapter]() { return make_annoy_nns(NUM_DIMS, adapter); };
    benchmark_nns("Annoy", creator, { 8000, 10000 });
}
#endif

#if 1
TEST("require that HNSW via NNS api mostly works") {
    DocVectorAdapter adapter;
    auto creator = [&adapter]() { return make_hnsw_nns(NUM_DIMS, adapter); };
    benchmark_nns("HNSW-like", creator, { 100, 150, 200 });
}
#endif

#if 0
TEST("require that HNSW wrapped api mostly works") {
    DocVectorAdapter adapter;
    auto creator = [&adapter]() { return make_hnsw_wrap(NUM_DIMS, adapter); };
    benchmark_nns("HNSW-wrap", creator, { 100, 150, 200 });
}
#endif

/**
 * Before running the benchmark the ANN_GIST1M data set must be downloaded and extracted:
 *   wget ftp://ftp.irisa.fr/local/texmex/corpus/gist.tar.gz
 *   tar -xf gist.tar.gz
 *
 * The benchmark program will load the data set from $HOME/gist if no directory is specified.
 *
 * More information about the dataset is found here: http://corpus-texmex.irisa.fr/.
 */
int main(int argc, char **argv) {
    TEST_MASTER.init(__FILE__);
    std::string gist_dir = ".";
    if (argc > 1) {
        gist_dir = argv[1];
    } else {
        char *home = getenv("HOME");
        if (home) {
            gist_dir = home;
            gist_dir += "/gist";
        }
    }
    read_data(gist_dir);
    TEST_RUN_ALL();
    return (TEST_MASTER.fini() ? 0 : 1);
}
