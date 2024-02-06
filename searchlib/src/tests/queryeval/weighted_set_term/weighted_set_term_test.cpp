// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/weighted_set_term_search.h>

#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/weighted_set_term_blueprint.h>
#include <vespa/searchlib/queryeval/fake_result.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/weightedchildrenverifiers.h>
#include <vespa/vespalib/gtest/gtest.h>

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

        fake.addResult("field", token1, FakeResult().doc(docid));
        fake.addResult("multi-field", token1, FakeResult().doc(docid));
        fake.addResult("multi-field", token2, FakeResult().doc(docid));
        fake.addResult("multi-field", token3, FakeResult().doc(docid));
    }
}

struct WS {
    static const uint32_t fieldId = 42;
    MatchDataLayout layout;
    TermFieldHandle handle;
    std::vector<std::pair<std::string, uint32_t> > tokens;
    bool field_is_filter;
    bool term_is_not_needed;

    WS();
    ~WS();

    WS &add(const std::string &token, uint32_t weight) {
        tokens.emplace_back(token, weight);
        return *this;
    }
    WS& set_field_is_filter(bool value) { field_is_filter = value; return *this; }
    WS& set_term_is_not_needed(bool value) { term_is_not_needed = value; return *this; }

    [[nodiscard]] Node::UP createNode() const {
        auto node = std::make_unique<SimpleWeightedSetTerm>(tokens.size(), "view", 0, Weight(0));
        for (const auto & token : tokens) {
            node->addTerm(token.first,Weight(token.second));
        }
        return node;
    }

    bool isGenericSearch(Searchable &searchable, const std::string &field, bool strict) const {
        FakeRequestContext requestContext;
        MatchData::UP md = layout.createMatchData();
        Node::UP node = createNode();
        FieldSpecList fields;
        fields.add(FieldSpec(field, fieldId, handle));
        auto bp = searchable.createBlueprint(requestContext, fields, *node);
        bp->fetchPostings(ExecuteInfo::createForTest(strict));
        auto sb = bp->createSearch(*md, strict);
        return (dynamic_cast<WeightedSetTermSearch*>(sb.get()) != nullptr);
    }

    FakeResult search(Searchable &searchable, const std::string &field, bool strict) const {
        FakeRequestContext requestContext;
        MatchData::UP md = layout.createMatchData();
        if (term_is_not_needed) {
            md->resolveTermField(handle)->tagAsNotNeeded();
        }
        Node::UP node = createNode();
        FieldSpecList fields;
        fields.add(FieldSpec(field, fieldId, handle, field_is_filter));
        auto bp = searchable.createBlueprint(requestContext, fields, *node);
        bp->fetchPostings(ExecuteInfo::createForTest(strict));
        auto sb = bp->createSearch(*md, strict);
        sb->initFullRange();
        FakeResult result;
        for (uint32_t docId = 1; docId < 10; ++docId) {
            if (sb->seek(docId)) {
                sb->unpack(docId);
                result.doc(docId);
                TermFieldMatchData &data = *md->resolveTermField(handle);
                FieldPositionsIterator itr = data.getIterator();
                for (; itr.valid(); itr.next()) {
                    result.elem(itr.getElementId());
                    result.weight(itr.getElementWeight());
                    result.pos(itr.getPosition());
                }
            }
        }
        return result;
    }
};

WS::WS()
    : layout(),
      handle(layout.allocTermField(fieldId)),
      tokens(),
      field_is_filter(false),
      term_is_not_needed(false)
{
    MatchData::UP tmp = layout.createMatchData();
    assert(tmp->resolveTermField(handle)->getFieldId() == fieldId);
}

WS::~WS() = default;

struct MockSearch : public SearchIterator {
    int seekCnt;
    int _initial;
    explicit MockSearch(uint32_t initial) : SearchIterator(), seekCnt(0), _initial(initial) { }
    void initRange(uint32_t begin, uint32_t end) override {
        SearchIterator::initRange(begin, end);
        setDocId(_initial);
    }
    void doSeek(uint32_t) override {
        ++seekCnt;
        setAtEnd();
    }
    void doUnpack(uint32_t) override {}
};

struct MockFixture {
    MockSearch *mock;
    TermFieldMatchData tfmd;
    std::unique_ptr<SearchIterator> search;
    explicit MockFixture(uint32_t initial) : mock(nullptr), tfmd(), search() {
        std::vector<SearchIterator*> children;
        std::vector<int32_t> weights;
        mock = new MockSearch(initial);
        children.push_back(mock);
        weights.push_back(1);
        search = WeightedSetTermSearch::create(children, tfmd, false, weights, {});
    }
    ~MockFixture();
};

MockFixture::~MockFixture() = default;

} // namespace <unnamed>

void run_simple(bool field_is_filter, bool term_is_not_needed, bool singleTerm)
{
    FakeSearchable index;
    setupFakeSearchable(index);
    FakeResult expect;
    if (field_is_filter || term_is_not_needed) {
        expect.doc(3);
        if ( ! singleTerm) {
            expect.doc(5)
                  .doc(7);
        }
    } else {
        expect.doc(3).elem(0).weight(30).pos(0);
        if (!singleTerm) {
            expect.doc(5).elem(0).weight(50).pos(0)
                  .doc(7).elem(0).weight(70).pos(0);
        }
    }
    WS ws;
    if (singleTerm) {
        ws.add("3", 30);
    } else {
        ws.add("7", 70).add("5", 50).add("3", 30).add("100", 1000);
    }
    ws.set_field_is_filter(field_is_filter)
      .set_term_is_not_needed(term_is_not_needed);

    EXPECT_TRUE(ws.isGenericSearch(index, "field", true));
    EXPECT_TRUE(ws.isGenericSearch(index, "field", false));
    EXPECT_TRUE(ws.isGenericSearch(index, "multi-field", true));
    EXPECT_TRUE(ws.isGenericSearch(index, "multi-field", false));

    EXPECT_EQ(expect, ws.search(index, "field", true));
    EXPECT_EQ(expect, ws.search(index, "field", false));
    EXPECT_EQ(expect, ws.search(index, "multi-field", true));
    EXPECT_EQ(expect, ws.search(index, "multi-field", false));
}

TEST(WeightedSetTermTest, test_simple)
{
    run_simple(false, false, false);
}

TEST(WeightedSetTermTest, test_simple_filter_field)
{
    run_simple(true, false, false);
}

TEST(WeightedSetTermTest, test_simple_unranked)
{
    run_simple(false, true, false);
}

TEST(WeightedSetTermTest, test_simple_unranked_filter_field)
{
    run_simple(true, true, false);
}

TEST(WeightedSetTermTest, test_simple_single)
{
    run_simple(false, false, true);
}

TEST(WeightedSetTermTest, test_simple_single_filter_field)
{
    run_simple(true, false, true);
}

TEST(WeightedSetTermTest, test_simple_single_unranked)
{
    run_simple(false, true, true);
}

TEST(WeightedSetTermTest, test_simple_single_unranked_filter_field)
{
    run_simple(true, true, true);
}

void run_multi(bool field_is_filter, bool term_is_not_needed)
{
    FakeSearchable index;
    setupFakeSearchable(index);
    FakeResult expect;
    if (field_is_filter || term_is_not_needed) {
        expect.doc(3)
            .doc(5)
            .doc(7);
    } else {
        expect.doc(3).elem(0).weight(230).pos(0).elem(0).weight(130).pos(0).elem(0).weight(30).pos(0)
            .doc(5).elem(0).weight(150).pos(0).elem(0).weight(50).pos(0)
            .doc(7).elem(0).weight(70).pos(0);
    }
    WS ws;
    ws.add("7", 70).add("5", 50).add("3", 30)
      .add("15", 150).add("13", 130)
      .add("23", 230).add("100", 1000)
      .set_field_is_filter(field_is_filter)
      .set_term_is_not_needed(term_is_not_needed);
    EXPECT_TRUE(ws.isGenericSearch(index, "multi-field", true));
    EXPECT_TRUE(ws.isGenericSearch(index, "multi-field", false));

    EXPECT_EQ(expect, ws.search(index, "multi-field", true));
    EXPECT_EQ(expect, ws.search(index, "multi-field", false));
}

TEST(WeightedSetTermTest, test_multi)
{
    run_multi(false, false);
}

TEST(WeightedSetTermTest, test_multi_filter_field)
{
    run_multi(true, false);
}

TEST(WeightedSetTermTest, test_multi_unranked)
{
    run_multi(false, true);
}

TEST(WeightedSetTermTest, test_eager_empty_child)
{
    MockFixture f1(search::endDocId);
    MockSearch *mock = f1.mock;
    SearchIterator &search = *f1.search;
    search.initFullRange();
    EXPECT_EQ(search.beginId(), search.getDocId());
    EXPECT_TRUE(!search.seek(1));
    EXPECT_TRUE(search.isAtEnd());
    EXPECT_EQ(0, mock->seekCnt);
}

TEST(WeightedSetTermTest, test_eager_matching_child)
{
    MockFixture f1(5);
    MockSearch *mock = f1.mock;
    SearchIterator &search = *f1.search;
    search.initFullRange();
    EXPECT_EQ(search.beginId(), search.getDocId());
    EXPECT_TRUE(!search.seek(3));
    EXPECT_EQ(5u, search.getDocId());
    EXPECT_EQ(0, mock->seekCnt);
    EXPECT_TRUE(search.seek(5));
    EXPECT_EQ(5u, search.getDocId());
    EXPECT_EQ(0, mock->seekCnt);
    EXPECT_TRUE(!search.seek(7));
    EXPECT_TRUE(search.isAtEnd());
    EXPECT_EQ(1, mock->seekCnt);
}

class IteratorChildrenVerifier : public search::test::IteratorChildrenVerifier {
private:
    SearchIterator::UP create(const std::vector<SearchIterator*> &children) const override {
        return WeightedSetTermSearch::create(children, _tfmd, false, _weights, {});
    }
};

class WeightIteratorChildrenVerifier : public search::test::DwwIteratorChildrenVerifier {
private:
    SearchIterator::UP create(std::vector<DocidWithWeightIterator> && children) const override {
        return WeightedSetTermSearch::create(_tfmd, false, std::cref(_weights), std::move(children));
    }
};

TEST(WeightedSetTermTest, verify_search_iterator_conformance_with_search_iterator_children)
{
    IteratorChildrenVerifier verifier;
    verifier.verify();
}

TEST(WeightedSetTermTest, verify_search_iterator_conformance_with_document_weight_iterator_children)
{
    WeightIteratorChildrenVerifier verifier;
    verifier.verify();
}

struct VerifyMatchData {
    struct MyBlueprint : search::queryeval::SimpleLeafBlueprint {
        VerifyMatchData &vmd;
        MyBlueprint(VerifyMatchData &vmd_in, FieldSpecBase spec_in)
            : SimpleLeafBlueprint(spec_in), vmd(vmd_in) {}
        [[nodiscard]] SearchIterator::UP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool) const override {
            EXPECT_EQ(tfmda.size(), 1u);
            EXPECT_TRUE(tfmda[0] != nullptr);
            if (vmd.child_tfmd == nullptr) {
                vmd.child_tfmd = tfmda[0];
            } else {
                EXPECT_EQ(vmd.child_tfmd, tfmda[0]);
            }
            ++vmd.child_cnt;
            return std::make_unique<EmptySearch>();
        }
        FlowStats calculate_flow_stats(uint32_t docid_limit) const override {
            return default_flow_stats(docid_limit, 0, 0);
        }
        [[nodiscard]] SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
            return create_default_filter(strict, constraint);
        }
    };
    size_t child_cnt = 0;
    TermFieldMatchData *child_tfmd = nullptr;
    search::queryeval::Blueprint::UP create(FieldSpecBase spec) {
        return std::make_unique<MyBlueprint>(*this, spec);
    }
};

TEST(WeightedSetTermTest, require_that_children_get_a_common_yet_separate_term_field_match_data)
{
    VerifyMatchData vmd;
    MatchDataLayout layout;
    auto top_handle = layout.allocTermField(42);
    FieldSpec top_spec("foo", 42, top_handle);
    WeightedSetTermBlueprint blueprint(top_spec);
    queryeval::Blueprint::HitEstimate estimate;
    for (size_t i = 0; i < 5; ++i) {
        blueprint.addTerm(vmd.create(blueprint.getNextChildField(top_spec)), 1, estimate);
    }
    blueprint.complete(estimate);
    auto match_data = layout.createMatchData();
    auto search = blueprint.createSearch(*match_data, true);
    auto top_tfmd = match_data->resolveTermField(top_handle);
    EXPECT_EQ(vmd.child_cnt, 5u);
    EXPECT_TRUE(vmd.child_tfmd != nullptr);
    EXPECT_NE(top_tfmd, vmd.child_tfmd);
}

GTEST_MAIN_RUN_ALL_TESTS()
