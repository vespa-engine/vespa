// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/fake_search.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_search.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "rise_wand.h"
#include "rise_wand.hpp"

using namespace search::fef;
using namespace search::queryeval;
using namespace vespalib;

typedef ParallelWeakAndSearch::MatchParams PWMatchParams;
typedef ParallelWeakAndSearch::RankParams PWRankParams;

namespace {

struct Stats {
    size_t hitCnt;
    size_t seekCnt;
    size_t unpackCnt;
    size_t skippedDocs;
    size_t skippedHits;
    Stats() : hitCnt(0), seekCnt(0), unpackCnt(0),
              skippedDocs(0), skippedHits(0) {}
    void hit() {
        ++hitCnt;
    }
    void seek(size_t docs, size_t hits) {
        ++seekCnt;
        skippedDocs += docs;
        skippedHits += hits;
    }
    void unpack() {
        ++unpackCnt;
    }
    void print() {
        fprintf(stderr, "Stats: hits=%zu, seeks=%zu, unpacks=%zu, skippedDocs=%zu, skippedHits=%zu\n",
                hitCnt, seekCnt, unpackCnt, skippedDocs, skippedHits);
    }
};

struct ModSearch : SearchIterator {
    Stats   &stats;
    uint32_t step;
    uint32_t limit;
    MinMaxPostingInfo info;
    TermFieldMatchData *tfmd;
    ModSearch(Stats &stats_in, uint32_t step_in, uint32_t limit_in, int32_t maxWeight, TermFieldMatchData *tfmd_in)
        : stats(stats_in), step(step_in), limit(limit_in), info(0, maxWeight), tfmd(tfmd_in) { }
    void initRange(uint32_t begin, uint32_t end) override {
        SearchIterator::initRange(begin, end);
        setDocId(step);
    }
    virtual void doSeek(uint32_t docid) override {
        assert(docid > getDocId());
        uint32_t skippedDocs = (docid - getDocId() - 1);
        uint32_t skippedHits = (skippedDocs / step);
        stats.seek(skippedDocs, skippedHits);
        uint32_t hit = (docid / step) * step;
        if (hit < docid) {
            hit += step;
        }
        if (hit < limit) {
            assert(hit >= docid);
            setDocId(hit);
        } else {
            setAtEnd();
        }
    }
    virtual void doUnpack(uint32_t docid) override {
        if (tfmd != NULL) {
            tfmd->reset(docid);
            search::fef::TermFieldMatchDataPosition pos;
            pos.setElementWeight(info.getMaxWeight());
            tfmd->appendPosition(pos);
        }
        stats.unpack();
    }
    virtual const PostingInfo *getPostingInfo() const override { return &info; }
};

struct WandFactory {
    virtual std::string name() const = 0;
    virtual SearchIterator::UP create(const wand::Terms &terms) = 0;
    virtual ~WandFactory() {}
};

struct VespaWandFactory : WandFactory {
    uint32_t n;
    VespaWandFactory(uint32_t n_in) : n(n_in) {}
    ~VespaWandFactory() override;
    virtual std::string name() const override { return make_string("VESPA WAND (n=%u)", n); }
    virtual SearchIterator::UP create(const wand::Terms &terms) override {
        return SearchIterator::UP(WeakAndSearch::create(terms, n, true));
    }
};

VespaWandFactory::~VespaWandFactory() = default;

struct VespaArrayWandFactory : WandFactory {
    uint32_t n;
    VespaArrayWandFactory(uint32_t n_in) : n(n_in) {}
    ~VespaArrayWandFactory() override;
    virtual std::string name() const override { return make_string("VESPA ARRAY WAND (n=%u)", n); }
    virtual SearchIterator::UP create(const wand::Terms &terms) override {
        return SearchIterator::UP(WeakAndSearch::createArrayWand(terms, n, true));
    }
};

VespaArrayWandFactory::~VespaArrayWandFactory() = default;

struct VespaHeapWandFactory : WandFactory {
    uint32_t n;
    VespaHeapWandFactory(uint32_t n_in) : n(n_in) {}
    ~VespaHeapWandFactory() override;
    virtual std::string name() const override { return make_string("VESPA HEAP WAND (n=%u)", n); }
    virtual SearchIterator::UP create(const wand::Terms &terms) override {
        return SearchIterator::UP(WeakAndSearch::createHeapWand(terms, n, true));
    }
};

VespaHeapWandFactory::~VespaHeapWandFactory() = default;

struct VespaParallelWandFactory : public WandFactory {
    SharedWeakAndPriorityQueue scores;
    TermFieldMatchData rootMatchData;
    VespaParallelWandFactory(uint32_t n) : scores(n), rootMatchData() {}
    ~VespaParallelWandFactory() override;
    virtual std::string name() const override { return make_string("VESPA PWAND (n=%u)", scores.getScoresToTrack()); }
    virtual SearchIterator::UP create(const wand::Terms &terms) override {
        return SearchIterator::UP(ParallelWeakAndSearch::create(terms,
                        PWMatchParams(scores, 0, 1, 1),
                        PWRankParams(rootMatchData, MatchData::UP()), true));
    }
};

VespaParallelWandFactory::~VespaParallelWandFactory() = default;

struct VespaParallelArrayWandFactory : public VespaParallelWandFactory {
    VespaParallelArrayWandFactory(uint32_t n) : VespaParallelWandFactory(n) {}
    ~VespaParallelArrayWandFactory() override;
    virtual std::string name() const override { return make_string("VESPA ARRAY PWAND (n=%u)", scores.getScoresToTrack()); }
    virtual SearchIterator::UP create(const wand::Terms &terms) override {
        return SearchIterator::UP(ParallelWeakAndSearch::createArrayWand(terms,
                        PWMatchParams(scores, 0, 1, 1),
                        PWRankParams(rootMatchData, MatchData::UP()), true));
    }
};

VespaParallelArrayWandFactory::~VespaParallelArrayWandFactory() = default;

struct VespaParallelHeapWandFactory : public VespaParallelWandFactory {
    VespaParallelHeapWandFactory(uint32_t n) : VespaParallelWandFactory(n) {}
    ~VespaParallelHeapWandFactory() override;
    virtual std::string name() const override { return make_string("VESPA HEAP PWAND (n=%u)", scores.getScoresToTrack()); }
    virtual SearchIterator::UP create(const wand::Terms &terms) override {
        return SearchIterator::UP(ParallelWeakAndSearch::createHeapWand(terms,
                        PWMatchParams(scores, 0, 1, 1),
                        PWRankParams(rootMatchData, MatchData::UP()), true));
    }
};

VespaParallelHeapWandFactory::~VespaParallelHeapWandFactory() = default;

struct TermFrequencyRiseWandFactory : WandFactory {
    uint32_t n;
    TermFrequencyRiseWandFactory(uint32_t n_in) : n(n_in) {}
    ~TermFrequencyRiseWandFactory() override;
    virtual std::string name() const override { return make_string("RISE WAND TF (n=%u)", n); }
    virtual SearchIterator::UP create(const wand::Terms &terms) override {
        return SearchIterator::UP(new rise::TermFrequencyRiseWand(terms, n));
    }
};

TermFrequencyRiseWandFactory::~TermFrequencyRiseWandFactory() = default;

struct DotProductRiseWandFactory : WandFactory {
    uint32_t n;
    DotProductRiseWandFactory(uint32_t n_in) : n(n_in) {}
    ~DotProductRiseWandFactory() override;
    virtual std::string name() const override { return make_string("RISE WAND DP (n=%u)", n); }
    virtual SearchIterator::UP create(const wand::Terms &terms) override {
        return SearchIterator::UP(new rise::DotProductRiseWand(terms, n));
    }
};

DotProductRiseWandFactory::~DotProductRiseWandFactory() = default;

struct FilterFactory : WandFactory {
    WandFactory &factory;
    Stats stats;
    uint32_t n;
    FilterFactory(WandFactory &f, uint32_t n_in) : factory(f), n(n_in) {}
    ~FilterFactory() override;
    virtual std::string name() const override { return make_string("Filter (mod=%u) [%s]", n, factory.name().c_str()); }
    virtual SearchIterator::UP create(const wand::Terms &terms) override {
        AndNotSearch::Children children;
        children.push_back(factory.create(terms));
        children.emplace_back(new ModSearch(stats, n, search::endDocId, n, NULL));
        return AndNotSearch::create(std::move(children), true);
    }
};

FilterFactory::~FilterFactory() = default;

struct Setup {
    Stats    stats;
    vespalib::duration  minTime;
    Setup() : stats(), minTime(10000s) {}
    virtual ~Setup() {}
    virtual std::string name() const = 0;
    virtual SearchIterator::UP create() = 0;
    void perform() {
        SearchIterator::UP search = create();
        SearchIterator &sb = *search;
        vespalib::Timer timer;
        for (sb.seek(1); !sb.isAtEnd(); sb.seek(sb.getDocId() + 1)) {
            stats.hit();
            sb.unpack(sb.getDocId());
        }
        if (timer.elapsed() < minTime) {
            minTime = timer.elapsed();
        }
    }
    void benchmark() {
        fprintf(stderr, "running benchmark for %s...\n", name().c_str());
        for (size_t i = 0; i < 5; ++i) {
            perform();
            if (i == 0) {
                stats.print();
            }
        }
        fprintf(stderr, "time (ms): %" PRId64 "\n", vespalib::count_ms(minTime));
    }
};

struct WandSetup : Setup {
    WandFactory &factory;
    uint32_t childCnt;
    uint32_t limit;
    uint32_t weight;
    MatchData::UP matchData;
    WandSetup(WandFactory &f, uint32_t c, uint32_t l) : Setup(), factory(f), childCnt(c), limit(l), weight(100), matchData() {}
    ~WandSetup() override;
    virtual std::string name() const override {
        return make_string("Wand Setup (terms=%u,docs=%u) [%s]", childCnt, limit, factory.name().c_str());
    }
    virtual SearchIterator::UP create() override {
        MatchDataLayout layout;
        std::vector<TermFieldHandle> handles;
        for (size_t i = 0; i < childCnt; ++i) {
            handles.push_back(layout.allocTermField(0));
        }
        matchData = layout.createMatchData();
        wand::Terms terms;
        for (size_t i = 1; i <= childCnt; ++i) {
            TermFieldMatchData *tfmd = matchData->resolveTermField(handles[i-1]);
            terms.push_back(wand::Term(new ModSearch(stats, i, limit, i, tfmd), weight, limit / i, tfmd));
        }
        return factory.create(terms);
    }
};

WandSetup::~WandSetup() = default;

} // namespace <unnamed>
