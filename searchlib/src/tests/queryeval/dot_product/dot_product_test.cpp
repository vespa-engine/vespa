// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/queryeval/dot_product_search.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/fake_result.h>
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/test/weightedchildrenverifiers.h>

using namespace search;
using namespace search::query;
using namespace search::fef;
using namespace search::queryeval;
using search::test::SearchIteratorVerifier;
using search::test::DocumentWeightAttributeHelper;

namespace {

void setupFakeSearchable(FakeSearchable &fake) {
    for (size_t docid = 1; docid < 10; ++docid) {
        std::string token1 = vespalib::make_string("%zu", docid);
        std::string token2 = vespalib::make_string("1%zu", docid);
        std::string token3 = vespalib::make_string("2%zu", docid);

        fake.addResult("field", token1, FakeResult().doc(docid).weight(docid).pos(0));
        fake.addResult("multi-field", token1, FakeResult().doc(docid).weight(docid).pos(0));
        fake.addResult("multi-field", token2, FakeResult().doc(docid).weight(2 * docid).pos(0));
        fake.addResult("multi-field", token3, FakeResult().doc(docid).weight(3 * docid).pos(0));
    }
}

struct DP {
    static const uint32_t fieldId = 0;
    static const TermFieldHandle handle = 0;
    std::vector<std::pair<std::string, uint32_t> > tokens;
    bool field_is_filter;
    bool term_is_not_needed;

    DP()
        : tokens(),
          field_is_filter(false),
          term_is_not_needed(false)
    {
    }

    DP &add(const std::string &token, uint32_t weight) {
        tokens.push_back(std::make_pair(token, weight));
        return *this;
    }
    DP& set_field_is_filter(bool value) { field_is_filter = value; return *this; }
    DP& set_term_is_not_needed(bool value) { term_is_not_needed = value; return *this; }

    Node::UP createNode() const {
        SimpleDotProduct *node = new SimpleDotProduct(tokens.size(), "view", 0, Weight(0));
        for (size_t i = 0; i < tokens.size(); ++i) {
            node->addTerm(tokens[i].first, Weight(tokens[i].second));
        }
        return Node::UP(node);
    }

    FakeResult search(Searchable &searchable, const std::string &field, bool strict) const {
        MatchData::UP md(MatchData::makeTestInstance(1, 1));
        if (term_is_not_needed) {
            md->resolveTermField(handle)->tagAsNotNeeded();
        }
        FakeRequestContext requestContext;
        Node::UP node = createNode();
        FieldSpecList fields;
        fields.add(FieldSpec(field, fieldId, handle, field_is_filter));
        queryeval::Blueprint::UP bp = searchable.createBlueprint(requestContext, fields, *node);
        bp->fetchPostings(ExecuteInfo::create(strict));
        SearchIterator::UP sb = bp->createSearch(*md, strict);
        EXPECT_TRUE(dynamic_cast<DotProductSearch*>(sb.get()) != 0);
        sb->initFullRange();
        FakeResult result;
        for (uint32_t docId = 1; docId < 10; ++docId) {
            if (sb->seek(docId)) {
                sb->unpack(docId);
                result.doc(docId);
                double score = md->resolveTermField(handle)->getRawScore();
                EXPECT_EQUAL((int)score, score);
                result.score(score);
            }
        }
        return result;
    }
};

struct MockSearch : public SearchIterator {
    int seekCnt;
    uint32_t _initial;
    MockSearch(uint32_t initial) : SearchIterator(), seekCnt(0), _initial(initial) { }
    void initRange(uint32_t begin, uint32_t end) override {
        SearchIterator::initRange(begin, end);
        setDocId(_initial);
    }
    virtual void doSeek(uint32_t) override {
        ++seekCnt;
        setAtEnd();
    }
    virtual void doUnpack(uint32_t) override {}
};

struct MockFixture {
    MockSearch *mock;
    TermFieldMatchData tfmd;
    std::unique_ptr<SearchIterator> search;
    MockFixture(uint32_t initial) :
            MockFixture(initial, {new EmptySearch()})
    { }
    MockFixture(uint32_t initial, std::vector<SearchIterator *> children) : mock(0), tfmd(), search() {
        std::vector<TermFieldMatchData*> childMatch;
        std::vector<int32_t> weights;
        const size_t numChildren(children.size()+1);
        MatchData::UP md(MatchData::makeTestInstance(numChildren, numChildren));
        for (size_t i(0); i < children.size(); i++) {
            childMatch.push_back(md->resolveTermField(i));
            weights.push_back(1);
        }
        mock = new MockSearch(initial);
        childMatch.push_back(md->resolveTermField(children.size()));
        children.push_back(mock);
        weights.push_back(1);
        search = DotProductSearch::create(children, tfmd, false, childMatch, weights, std::move(md));
    }
};

    void verifySimple(const FakeResult & expect, DP & ws) {
        FakeSearchable index;
        setupFakeSearchable(index);
        EXPECT_EQUAL(expect, ws.search(index, "field", true));
        EXPECT_EQUAL(expect, ws.search(index, "field", false));
        EXPECT_EQUAL(expect, ws.search(index, "multi-field", true));
        EXPECT_EQUAL(expect, ws.search(index, "multi-field", false));
    }

} // namespace <unnamed>

std::function<int(int)>
make_score_filter(bool field_is_filter, bool term_is_not_needed)
{
    if (field_is_filter || term_is_not_needed) {
        return [](int) noexcept { return 0; };
    } else {
        return [](int value) noexcept { return value; };
    }
}

void run_simple(bool field_is_filter, bool term_is_not_needed)
{
    auto score_filter = make_score_filter(field_is_filter, term_is_not_needed);
    FakeResult expect = FakeResult()
                        .doc(3).score(score_filter(30 * 3))
                        .doc(5).score(score_filter(50 * 5))
                        .doc(7).score(score_filter(70 * 7));
    DP ws = DP().add("7", 70).add("5", 50).add("3", 30).add("100", 1000)
            .set_field_is_filter(field_is_filter)
            .set_term_is_not_needed(term_is_not_needed);

    TEST_DO(verifySimple(expect, ws));
}

TEST("test Simple") {
    TEST_DO(run_simple(false, false));
}

TEST("test Simple filter field") {
    TEST_DO(run_simple(true, false));
}

TEST("test Simple unranked") {
    TEST_DO(run_simple(false, true));
}

TEST("test Simple Single") {
    FakeResult expect = FakeResult()
            .doc(7).score(70 * 7);
    DP ws = DP().add("7", 70);

    TEST_DO(verifySimple(expect, ws));
}

void run_multi(bool field_is_filter, bool term_is_not_needed)
{
    auto score_filter = make_score_filter(field_is_filter, term_is_not_needed);
    FakeSearchable index;
    setupFakeSearchable(index);
    FakeResult expect = FakeResult()
                        .doc(3).score(score_filter(30 * 3 + 130 * 2 * 3 + 230 * 3 * 3))
                        .doc(5).score(score_filter(50 * 5 + 150 * 2 * 5))
                        .doc(7).score(score_filter(70 * 7));
    DP ws = DP().add("7", 70).add("5", 50).add("3", 30)
            .add("15", 150).add("13", 130)
            .add("23", 230).add("100", 1000)
            .set_field_is_filter(field_is_filter)
            .set_term_is_not_needed(term_is_not_needed);

    EXPECT_EQUAL(expect, ws.search(index, "multi-field", true));
    EXPECT_EQUAL(expect, ws.search(index, "multi-field", false));
}


TEST("test Multi") {
    TEST_DO(run_multi(false, false));
}

TEST("test Multi filter field") {
    TEST_DO(run_multi(true, false));
}
TEST("test Multi unranked") {
    TEST_DO(run_multi(false, true));
}

TEST_F("test Eager Empty Child", MockFixture(search::endDocId, {})) {
    MockSearch *mock = f1.mock;
    SearchIterator &search = *f1.search;
    search.initFullRange();
    EXPECT_TRUE(search.isAtEnd());
    EXPECT_EQUAL(0, mock->seekCnt);
}

TEST_F("test Eager Empty Children", MockFixture(search::endDocId)) {
    MockSearch *mock = f1.mock;
    SearchIterator &search = *f1.search;
    search.initFullRange();
    EXPECT_EQUAL(search.beginId(), search.getDocId());
    EXPECT_TRUE(!search.seek(1));
    EXPECT_TRUE(search.isAtEnd());
    EXPECT_EQUAL(0, mock->seekCnt);
}

void verifyEagerMatching(SearchIterator & search, MockSearch * mock) {
    EXPECT_TRUE(!search.seek(3));
    EXPECT_EQUAL(5u, search.getDocId());
    EXPECT_EQUAL(0, mock->seekCnt);
    EXPECT_TRUE(search.seek(5));
    EXPECT_EQUAL(5u, search.getDocId());
    EXPECT_EQUAL(0, mock->seekCnt);
    EXPECT_TRUE(!search.seek(7));
    EXPECT_TRUE(search.isAtEnd());
    EXPECT_EQUAL(1, mock->seekCnt);
}

TEST_F("test Eager Matching Child", MockFixture(5, {})) {
    MockSearch *mock = f1.mock;
    SearchIterator &search = *f1.search;
    search.initFullRange();
    EXPECT_EQUAL(5u, search.getDocId());
    verifyEagerMatching(search, mock);
}

TEST_F("test Eager Matching Children", MockFixture(5)) {
    MockSearch *mock = f1.mock;
    SearchIterator &search = *f1.search;
    search.initFullRange();
    EXPECT_EQUAL(search.beginId(), search.getDocId());
    verifyEagerMatching(search, mock);
}

class IteratorChildrenVerifier : public search::test::IteratorChildrenVerifier {
private:
    SearchIterator::UP
    create(const std::vector<SearchIterator*> &children) const override {
        // This is a pragmatic and dirty workaround to make IteratorVerifier test
        // not fail on unpack when accessing child match weights
        std::vector<fef::TermFieldMatchData*> no_child_match(children.size(), &_tfmd);
        MatchData::UP no_match_data;
        return DotProductSearch::create(children, _tfmd, false, no_child_match, _weights, std::move(no_match_data));
    }
};

class WeightIteratorChildrenVerifier : public search::test::DwaIteratorChildrenVerifier {
private:
    SearchIterator::UP
    create(std::vector<DocumentWeightIterator> && children) const override {
        return DotProductSearch::create(_tfmd, false, _weights, std::move(children));
    }
};

TEST("verify search iterator conformance with search iterator children") {
    IteratorChildrenVerifier verifier;
    verifier.verify();
}

TEST("verify search iterator conformance with document weight iterator children") {
    WeightIteratorChildrenVerifier verifier;
    verifier.verify();
}

TEST_MAIN() { TEST_RUN_ALL(); }
