// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#define EFFECTIVE_DOCS NUM_DOCS
#define NUM_REACH 10000
#define NUM_Q 1000

#include "doc_vector_access.h"
#include "nns.h"
#include "for-sift-hit.h"
#include "for-sift-top-k.h"
#include "time-util.h"
#include "point-vector.h"
#include "read-vecs.h"
#include "bruteforce-nns.h"

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
    std::string data_set = "gist";
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
