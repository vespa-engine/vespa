// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/queryeval/unpackinfo.h>
#include <vespa/searchlib/queryeval/multibitvectoriterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>
#include <random>

using namespace search;
using namespace vespalib;
using search::queryeval::SearchIterator;
using search::queryeval::OrSearch;
using search::queryeval::UnpackInfo;
using TMD = search::fef::TermFieldMatchData;
using vespalib::make_string_short::fmt;
using Impl = OrSearch::StrictImpl;

double budget = 5.0;
size_t bench_docs = 10'000'000;
bool bench_mode = false;
constexpr uint32_t default_seed = 5489u;
std::mt19937 gen(default_seed);

const char *impl_str(Impl impl) {
    if (impl == Impl::PLAIN) { return "plain"; }
    if (impl == Impl::HEAP)  { return " heap"; }
    return "unknown";
}
const char *bool_str(bool bit) { return bit ? "true" : "false"; }
const char *leaf_str(bool array) { return array ? "A" : "B"; }
const char *opt_str(bool optimize) { return optimize ? "OPT" : "std"; }

BitVector::UP make_bitvector(size_t size, size_t num_bits) {
    EXPECT_GT(size, num_bits);
    auto bv = BitVector::create(size);
    size_t bits_left = num_bits;
    // bit 0 is never set since it is reserved
    // all other bits have equal probability to be set
    for (size_t i = 1; i < size; ++i) {
        std::uniform_int_distribution<size_t> space(0,size-i-1);
        if (space(gen) < bits_left) {
            bv->setBit(i);
            --bits_left;
        }
    }
    bv->invalidateCachedCount();
    EXPECT_EQ(bv->countTrueBits(), num_bits);
    return bv;
}

// simple strict array-based iterator
// This class has 2 uses:
// 1: better performance for few hits compared to bitvector
// 2: not a bitvector, useful when testing multi-bitvector interactions
struct ArrayIterator : SearchIterator {
    uint32_t               my_offset = 0;
    uint32_t               my_limit;
    std::vector<uint32_t>  my_hits;
    TMD                   &my_match_data;
    ArrayIterator(const BitVector &bv, TMD &tmd)
      : my_limit(bv.size()), my_match_data(tmd)
    {
        uint32_t next = bv.getStartIndex();
        for (;;) {
            next = bv.getNextTrueBit(next);
            if (next >= my_limit) {
                break;
            }
            my_hits.push_back(next++);
        }
        my_match_data.reset(0);
    }
    void initRange(uint32_t begin, uint32_t end) final {
        SearchIterator::initRange(begin, end);
        my_offset = 0;
    }
    void doSeek(uint32_t docid) final {
        while (my_offset < my_hits.size() && my_hits[my_offset] < docid) {
            ++my_offset;
        }
        if (my_offset < my_hits.size()) {
            setDocId(my_hits[my_offset]);
        } else {
            setAtEnd();
        }
    }
    Trinary is_strict() const final { return Trinary::True; }
    void doUnpack(uint32_t docId) final { my_match_data.resetOnlyDocId(docId); }
};

struct OrSetup {
    uint32_t                          docid_limit;
    bool                              unpack_all = true;
    bool                              unpack_none = true;
    std::vector<std::unique_ptr<TMD>> match_data;
    std::vector<BitVector::UP>        child_hits;
    std::vector<bool>                 use_array;
    OrSetup(uint32_t docid_limit_in) noexcept : docid_limit(docid_limit_in) {}
    size_t per_child(double target, size_t child_cnt) {
        size_t result = (docid_limit * target) / child_cnt;
        return (result >= docid_limit) ? (docid_limit - 1) : result;
    }
    bool should_use_array(size_t hits) {
        return (docid_limit / hits) >= 32;
    }
    OrSetup &add(size_t num_hits, bool use_array_in, bool need_unpack) {
        match_data.push_back(std::make_unique<TMD>());
        child_hits.push_back(make_bitvector(docid_limit, num_hits));
        use_array.push_back(use_array_in);
        if (need_unpack) {
            match_data.back()->setNeedNormalFeatures(true);
            match_data.back()->setNeedInterleavedFeatures(true);
            unpack_none = false;
        } else {
            match_data.back()->tagAsNotNeeded();
            unpack_all = false;
        }
        return *this;
    }
    SearchIterator::UP make_leaf(size_t i) {
        if (use_array[i]) {
            return std::make_unique<ArrayIterator>(*child_hits[i], *match_data[i]);
        } else {
            return BitVectorIterator::create(child_hits[i].get(), *match_data[i], true);
        }
    }
    SearchIterator::UP make_or(Impl impl, bool optimize) {
        assert(!child_hits.empty());
        if (child_hits.size() == 1) {
            // use child directly if there is only one
            return make_leaf(0);
        }
        std::vector<SearchIterator::UP> children;
        for (size_t i = 0; i < child_hits.size(); ++i) {
            children.push_back(make_leaf(i));
        }
        UnpackInfo unpack;
        if (unpack_all) {
            unpack.forceAll();
        } else if (!unpack_none) {
            for (size_t i = 0; i < match_data.size(); ++i) {
                if (!match_data[i]->isNotNeeded()) {
                    unpack.add(i);
                }
            }
        }
        auto result = OrSearch::create(std::move(children), true, unpack, impl);
        if (optimize) {
            result = queryeval::MultiBitVectorIteratorBase::optimize(std::move(result));
        }
        return result;
    }
    OrSetup &prepare_bm(size_t child_cnt, size_t hits_per_child) {
        for (size_t i = 0; i < child_cnt; ++i) {
            add(hits_per_child, should_use_array(hits_per_child), false);
        }
        return *this;
    }
    std::pair<size_t,double> bm_search_ms(Impl impl, bool optimized) {
        auto search_up = make_or(impl, optimized);
        SearchIterator &search = *search_up;
        size_t hits = 0;
        BenchmarkTimer timer(budget);
        while (timer.has_budget()) {
            timer.before();
            hits = 0;
            search.initRange(1, docid_limit);
            uint32_t docid = search.seekFirst(1);
            while (docid < docid_limit) {
                ++hits;
                docid = search.seekNext(docid + 1);
                // no unpack
            }
            timer.after();
        }
        return std::make_pair(hits, timer.min_time() * 1000.0);
    }
    void verify_not_match(uint32_t docid) {
        for (size_t i = 0; i < match_data.size(); ++i) {
            EXPECT_FALSE(child_hits[i]->testBit(docid));
        }
    }
    void verify_match(uint32_t docid, bool unpacked, bool check_skipped_unpack) {
        bool match = false;
        for (size_t i = 0; i < match_data.size(); ++i) {
            if (child_hits[i]->testBit(docid)) {
                match = true;
                if (unpacked) {
                    if (!match_data[i]->isNotNeeded()) {
                        EXPECT_EQ(match_data[i]->getDocId(), docid) << "unpack was needed";
                    } else if (check_skipped_unpack) {
                        EXPECT_NE(match_data[i]->getDocId(), docid) << "unpack was not needed";
                    }
                } else {
                    EXPECT_NE(match_data[i]->getDocId(), docid) << "document was not unpacked";
                }
            } else {
                EXPECT_NE(match_data[i]->getDocId(), docid) << "document was not a match";
            }
        }
        EXPECT_TRUE(match);
    }
    void reset_match_data() {
        // this is needed since we re-search the same docid space
        // multiple times and may end up finding a result we are not
        // unpacking that was unpacked in the last iteration thus
        // breaking the "document was not unpacked" test condition.
        for (auto &tmd: match_data) {
            tmd->resetOnlyDocId(0);
        }
    }
    void verify_seek_unpack(Impl impl, bool check_skipped_unpack, bool optimized) {
        auto search_up = make_or(impl, optimized);
        SearchIterator &search = *search_up;
        for (size_t unpack_nth: {1, 3}) {
            for (size_t skip: {1, 31}) {
                uint32_t hits = 0;
                uint32_t check_at = 1;
                search.initRange(1, docid_limit);
                uint32_t docid = search.seekFirst(1);
                while (docid < docid_limit) {
                    for (; check_at < docid; ++check_at) {
                        verify_not_match(check_at);
                    }
                    if (++hits % unpack_nth == 0) {
                        search.unpack(docid);
                        verify_match(check_at, true, check_skipped_unpack);
                    } else {
                        verify_match(check_at, false, check_skipped_unpack);
                    }
                    check_at = docid + skip;
                    docid = search.seekNext(docid + skip);
                }
                for (; check_at < docid_limit; ++check_at) {
                    verify_not_match(check_at);
                }
                reset_match_data();
            }
        }
    }
    ~OrSetup();
};
OrSetup::~OrSetup() = default;

TEST(OrSpeed, array_iterator_seek_unpack) {
    OrSetup setup(100);
    setup.add(10, true, true);
    setup.verify_seek_unpack(Impl::PLAIN, true, false);
}

TEST(OrSpeed, or_seek_unpack) {
    for (bool optimize: {false, true}) {
        for (double target: {0.1, 0.5, 1.0, 10.0}) {
            for (int unpack: {0,1,2}) {
                OrSetup setup(1000);
                size_t part = setup.per_child(target, 13);
                for (size_t i = 0; i < 13; ++i) {
                    bool use_array = (i/2)%2 == 0;
                    bool need_unpack = unpack > 0;
                    if (unpack == 2 && i % 2 == 0) {
                        need_unpack = false;
                    }
                    setup.add(part, use_array, need_unpack);
                }
                for (auto impl: {Impl::PLAIN, Impl::HEAP}) {
                    SCOPED_TRACE(fmt("impl: %s, optimize: %s, part: %zu, unpack: %d",
                                     impl_str(impl), bool_str(optimize), part, unpack));
                    setup.verify_seek_unpack(impl, true, optimize);
                }
            }
        }
    }
}

TEST(OrSpeed, bm_array_vs_bitvector) {
    if (!bench_mode) {
        fprintf(stdout, "[ SKIPPING ] run with 'bench' parameter to activate\n");
        return;
    }
    for (size_t one_of: {16, 32, 64}) {
        double target = 1.0 / one_of;
        size_t hits = target * bench_docs;
        OrSetup setup(bench_docs);
        setup.add(hits, false, false);
        for (bool use_array: {false, true}) {
            setup.use_array[0] = use_array;
            auto result = setup.bm_search_ms(Impl::PLAIN, false);
            fprintf(stderr, "LEAF(%s): (one of %4zu) hits: %8zu, time: %10.3f ms, time per hits: %10.3f ns\n",
                    leaf_str(use_array), one_of, result.first, result.second, (result.second * 1000.0 * 1000.0) / result.first);
        }
    }
}

TEST(OrSpeed, bm_strict_or) {
    if (!bench_mode) {
        fprintf(stdout, "[ SKIPPING ] run with 'bench' parameter to activate\n");
        return;
    }
    for (double target: {0.001, 0.01, 0.1, 0.5, 1.0, 10.0}) {
        for (size_t child_cnt: {2, 3, 4, 5, 10, 100, 250, 500, 1000}) {
            for (bool optimize: {false, true}) {
                OrSetup setup(bench_docs);
                size_t part = setup.per_child(target, child_cnt);
                bool use_array = setup.should_use_array(part);
                if (part > 0 && (!use_array || !optimize)) {
                    setup.prepare_bm(child_cnt, part);
                    for (auto impl: {Impl::PLAIN, Impl::HEAP}) {
                        auto result = setup.bm_search_ms(impl, optimize);
                        fprintf(stderr, "OR bench(%s, %s, children: %4zu, hits_per_child: %8zu %s): "
                                "total_hits: %8zu, time: %10.3f ms, time per hits: %10.3f ns\n",
                                impl_str(impl), opt_str(optimize), child_cnt, part, leaf_str(use_array),
                                result.first, result.second, (result.second * 1000.0 * 1000.0) / result.first);
                    }
                }
            }
        }
    }
}

int main(int argc, char **argv) {
    if (argc > 1 && (argv[1] == std::string("bench"))) {
        fprintf(stderr, "running in benchmarking mode\n");
        bench_mode = true;
        ++argv;
        --argc;
    }
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
