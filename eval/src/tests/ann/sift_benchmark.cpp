// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <chrono>
#include <cstdlib>

#define NUM_DIMS 128
#define NUM_DOCS 1000000
#define EFFECTIVE_DOCS NUM_DOCS
#define NUM_Q 1000
#define NUM_REACH 10000

#include "doc_vector_access.h"
#include "nns.h"
#include "for-sift-hit.h"
#include "for-sift-top-k.h"
#include "std-random.h"
#include "time-util.h"
#include "point-vector.h"
#include "read-vecs.h"
#include "bruteforce-nns.h"

TopK bruteforce_nns_filter(const PointVector &query, const BitVector &skipDocIds) {
    TopK result;
    BfHitHeap heap(result.K);
    for (uint32_t docid = 0; docid < NUM_DOCS; ++docid) {
        if (skipDocIds.isSet(docid)) continue;
        const PointVector &docvector = generatedDocs[docid];
        double d = l2distCalc.l2sq_dist(query, docvector);
        Hit h(docid, d);
        heap.maybe_use(h);
    }
    std::vector<Hit> best = heap.bestHits();
    EXPECT_EQUAL(best.size(), result.K);
    for (size_t i = 0; i < result.K; ++i) {
        result.hits[i] = best[i];
    }
    return result;
}

void timing_bf_filter(int percent)
{
    BitVector skipDocIds(NUM_DOCS);
    RndGen rnd;
    for (uint32_t idx = 0; idx < NUM_DOCS; ++idx) {
        if (rnd.nextUniform() < 0.01 * percent) {
            skipDocIds.setBit(idx);
        } else {
            skipDocIds.clearBit(idx);
        }
    }
    TimePoint bef = std::chrono::steady_clock::now();
    for (int cnt = 0; cnt < NUM_Q; ++cnt) {
        const PointVector &qv = generatedQueries[cnt];
        auto res = bruteforce_nns_filter(qv, skipDocIds);
        EXPECT_TRUE(res.hits[res.K - 1].distance > 0.0);
    }
    TimePoint aft = std::chrono::steady_clock::now();
    fprintf(stderr, "timing for bruteforce filter %d %%: %.3f ms = %.3f ms/q\n",
            percent, to_ms(aft - bef), to_ms(aft - bef)/NUM_Q);
}

TEST("require that brute force works") {
    TimePoint bef = std::chrono::steady_clock::now();
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
#if 1
    for (uint32_t filter_percent : { 0, 1, 10, 50, 90, 95, 99 }) {
        timing_bf_filter(filter_percent);
    }
#endif
}

using NNS_API = NNS<float>;

size_t search_with_filter(uint32_t sk, NNS_API &nns, uint32_t qid,
                          const BitVector &skipDocIds)
{
    const PointVector &qv = generatedQueries[qid];
    vespalib::ConstArrayRef<float> query(qv.v, NUM_DIMS);
    auto rv = nns.topKfilter(100, query, sk, skipDocIds);
    return rv.size();
}

#include "find-with-nns.h"
#include "verify-top-k.h"

void verify_with_filter(uint32_t sk, NNS_API &nns, uint32_t qid,
                        const BitVector &skipDocIds)
{
    const PointVector &qv = generatedQueries[qid];
    auto expected = bruteforce_nns_filter(qv, skipDocIds);
    vespalib::ConstArrayRef<float> query(qv.v, NUM_DIMS);
    auto rv = nns.topKfilter(expected.K, query, sk, skipDocIds);
    TopK actual;
    for (size_t i = 0; i < actual.K; ++i) {
        actual.hits[i] = Hit(rv[i].docid, rv[i].sq.distance);
    }
    verify_top_k(expected, actual, sk, qid);
}

void timing_nns_filter(const char *name, NNS_API &nns,
                       std::vector<uint32_t> sk_list, int percent)
{
    BitVector skipDocIds(NUM_DOCS);
    RndGen rnd;
    for (uint32_t idx = 0; idx < NUM_DOCS; ++idx) {
        if (rnd.nextUniform() < 0.01 * percent) {
            skipDocIds.setBit(idx);
        } else {
            skipDocIds.clearBit(idx);
        }
    }
    for (uint32_t search_k : sk_list) {
        TimePoint bef = std::chrono::steady_clock::now();
        for (int cnt = 0; cnt < NUM_Q; ++cnt) {
            uint32_t nh = search_with_filter(search_k, nns, cnt, skipDocIds);
            EXPECT_EQUAL(nh, 100u);
        }
        TimePoint aft = std::chrono::steady_clock::now();
        fprintf(stderr, "timing for %s filter %d %% search_k=%u: %.3f ms = %.3f ms/q\n",
                name, percent, search_k, to_ms(aft - bef), to_ms(aft - bef)/NUM_Q);
#if 0
        fprintf(stderr, "Quality check for %s filter %d %%:\n", name, percent);
        for (int cnt = 0; cnt < NUM_Q; ++cnt) {
            verify_with_filter(search_k, nns, cnt, skipDocIds);
        }
#endif
    }
}

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
void benchmark_nns(const char *name, FUNC creator, std::vector<uint32_t> sk_list) {
    fprintf(stderr, "trying %s indexing...\n", name);
    std::unique_ptr<NNS_API> nnsp = creator();
    NNS_API &nns = *nnsp;
    TimePoint bef = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < NUM_DOCS; ++i) {
        nns.addDoc(i);
    }
    fprintf(stderr, "added %u documents...\n", NUM_DOCS);
    find_with_nns(1, nns, 0);
    TimePoint aft = std::chrono::steady_clock::now();
    fprintf(stderr, "build %s index: %.3f ms\n", name, to_ms(aft - bef));

    fprintf(stderr, "Timings for %s :\n", name);
    timing_nns(name, nns, sk_list);
    for (uint32_t filter_percent : { 0, 1, 10, 50, 90, 95, 99 }) {
        timing_nns_filter(name, nns, sk_list, filter_percent);
    }
    fprintf(stderr, "Quality for %s :\n", name);
    quality_nns(nns, sk_list);
}

#if 0
TEST("require that Locality Sensitive Hashing mostly works") {
    DocVectorAdapter adapter;
    auto creator = [&adapter]() { return make_rplsh_nns(NUM_DIMS, adapter); };
    benchmark_nns("RPLSH", creator, { 200, 1000 });
}
#endif

#if 1
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
 * Before running the benchmark the ANN_SIFT1M data set must be downloaded and extracted:
 *   wget ftp://ftp.irisa.fr/local/texmex/corpus/sift.tar.gz
 *   tar -xf sift.tar.gz
 *
 * To run the program:
 *   ./eval_sift_benchmark_app <data_dir>
 *
 * The benchmark program will load the data set from $HOME/sift if no directory is specified.
 *
 *
 * The ANN_GIST1M data set can also be used (as it has the same file format):
 *   wget ftp://ftp.irisa.fr/local/texmex/corpus/gist.tar.gz
 *   tar -xf gist.tar.gz
 *
 * Note that #define NUM_DIMS must be changed to 960 before recompiling and running the program:
 *   ./eval_sift_benchmark_app gist <data_dir>
 *
 *
 * More information about the datasets is found here: http://corpus-texmex.irisa.fr/.
 */
int main(int argc, char **argv) {
    TEST_MASTER.init(__FILE__);
    std::string data_set = "sift";
    std::string data_dir = ".";
    if (argc > 2) {
        data_set = argv[1];
        data_dir = argv[2];
    } else if (argc > 1) {
        data_dir = argv[1];
    } else {
        char *home = getenv("HOME");
        if (home) {
            data_dir = home;
            data_dir += "/" + data_set;
        }
    }
    read_data(data_dir, data_set);
    TEST_RUN_ALL();
    return (TEST_MASTER.fini() ? 0 : 1);
}
