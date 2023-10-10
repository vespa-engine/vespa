// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/queryeval/fake_search.h>
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/simplesearch.h>
#include <vespa/searchlib/queryeval/test/eagerchild.h>
#include <vespa/searchlib/queryeval/test/leafspec.h>
#include <vespa/searchlib/queryeval/test/wandspec.h>
#include <vespa/searchlib/test/weightedchildrenverifiers.h>

using namespace search::fef;
using namespace search::queryeval;
using namespace search::queryeval::test;

using History = SearchHistory;

namespace {

struct MyWandSpec : public WandSpec
{
    uint32_t n;

    MyWandSpec(uint32_t n_) : WandSpec(), n(n_) {}
    SearchIterator *create() {
        return new TrackedSearch("WAND", getHistory(), WeakAndSearch::create(getTerms(), n, true));
    }
};

struct SimpleWandFixture {
    MyWandSpec   spec;
    SimpleResult hits;
    SimpleWandFixture() : spec(2), hits() {
        spec.leaf(LeafSpec("foo").doc(1).doc(2).doc(3).doc(4).doc(5).doc(6));
        spec.leaf(LeafSpec("bar").doc(1).doc(3).doc(5));
        SearchIterator::UP search(spec.create());
        hits.search(*search);
    }
};

struct AdvancedWandFixture {
    MyWandSpec   spec;
    SimpleResult hits;
    AdvancedWandFixture() : spec(100), hits() {
        spec.leaf(LeafSpec("1").doc(1).doc(11).doc(111));
        spec.leaf(LeafSpec("2").doc(2).doc(12).doc(112));
        spec.leaf(LeafSpec("3").doc(3).doc(13).doc(113));
        spec.leaf(LeafSpec("4").doc(4).doc(14).doc(114));
        spec.leaf(LeafSpec("5").doc(5).doc(15).doc(115));
        SearchIterator::UP search(spec.create());
        hits.search(*search);
    }
};

struct WeightOrder {
    bool operator()(const wand::Term &t1, const wand::Term &t2) const {
        return (t1.weight < t2.weight);
    }
};

} // namespace <unnamed>

TEST_F("require that wand prunes bad hits after enough good ones are obtained", SimpleWandFixture) {
    EXPECT_EQUAL(SimpleResult().addHit(1).addHit(2).addHit(3).addHit(5), f.hits);
}

TEST_F("require that wand uses subsearches as expected", SimpleWandFixture) {
    EXPECT_EQUAL(History()
                 .seek("WAND", 1).seek("bar", 1).step("bar", 1).step("WAND", 1)
                 .unpack("WAND", 1).seek("foo", 1).step("foo", 1).unpack("bar", 1).unpack("foo", 1)
                 .seek("WAND", 2).seek("bar", 2).step("bar", 3).seek("foo", 2).step("foo", 2).step("WAND", 2)
                 .unpack("WAND", 2).unpack("foo", 2)
                 .seek("WAND", 3).step("WAND", 3)
                 .unpack("WAND", 3).seek("foo", 3).step("foo", 3).unpack("bar", 3).unpack("foo", 3)
                 .seek("WAND", 4).seek("bar", 4).step("bar", 5).seek("foo", 5).step("foo", 5).step("WAND", 5)
                 .unpack("WAND", 5).unpack("bar", 5).unpack("foo", 5)
                 .seek("WAND", 6).seek("bar", 6).step("bar", search::endDocId).step("WAND", search::endDocId),
                 f.spec.getHistory());
}

TEST_F("require that documents are considered in the right order", AdvancedWandFixture) {
    EXPECT_EQUAL(SimpleResult()
                 .addHit(1).addHit(2).addHit(3).addHit(4).addHit(5)
                 .addHit(11).addHit(12).addHit(13).addHit(14).addHit(15)
                 .addHit(111).addHit(112).addHit(113).addHit(114).addHit(115), f.hits);
}

TEST("require that initial docid for subsearches are taken into account") {
    History history;
    wand::Terms terms;
    terms.push_back(wand::Term(new TrackedSearch("foo", history, new EagerChild(search::endDocId)), 100, 1));
    terms.push_back(wand::Term(new TrackedSearch("bar", history, new EagerChild(10)), 100, 2));
    SearchIterator::UP search(new TrackedSearch("WAND", history, WeakAndSearch::create(terms, 2, true)));
    SimpleResult hits;
    hits.search(*search);
    EXPECT_EQUAL(SimpleResult().addHit(10), hits);
    EXPECT_EQUAL(History().seek("WAND", 1).step("WAND", 10).unpack("WAND", 10).unpack("bar", 10)
                 .seek("WAND", 11).seek("bar", 11).step("bar", search::endDocId).step("WAND", search::endDocId),
                 history);
}

class IteratorChildrenVerifier : public search::test::IteratorChildrenVerifier {
private:
    SearchIterator::UP create(bool strict) const override {
        wand::Terms terms;
        for (size_t i = 0; i < _num_children; ++i) {
            terms.emplace_back(createIterator(_split_lists[i], strict).release(),
                               100, _split_lists[i].size());
        }
        return SearchIterator::UP(WeakAndSearch::create(terms, -1, strict));
    }
};

TEST("verify search iterator conformance") {
    IteratorChildrenVerifier verifier;
    verifier.verify();
}

TEST_MAIN() { TEST_RUN_ALL(); }
