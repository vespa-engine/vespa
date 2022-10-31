// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/queryeval/weighted_set_term_search.h>

#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/weighted_set_term_blueprint.h>
#include <vespa/searchlib/queryeval/fake_result.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
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

    WS()
        : layout(),
          handle(layout.allocTermField(fieldId)),
          tokens(),
          field_is_filter(false),
          term_is_not_needed(false)
    {
        MatchData::UP tmp = layout.createMatchData();
        ASSERT_TRUE(tmp->resolveTermField(handle)->getFieldId() == fieldId);
    }

    WS &add(const std::string &token, uint32_t weight) {
        tokens.push_back(std::make_pair(token, weight));
        return *this;
    }
    WS& set_field_is_filter(bool value) { field_is_filter = value; return *this; }
    WS& set_term_is_not_needed(bool value) { term_is_not_needed = value; return *this; }

    Node::UP createNode() const {
        SimpleWeightedSetTerm *node = new SimpleWeightedSetTerm(tokens.size(), "view", 0, Weight(0));
        for (size_t i = 0; i < tokens.size(); ++i) {
            node->addTerm(tokens[i].first,Weight(tokens[i].second));
        }
        return Node::UP(node);
    }

    bool isGenericSearch(Searchable &searchable, const std::string &field, bool strict) const {
        FakeRequestContext requestContext;
        MatchData::UP md = layout.createMatchData();
        Node::UP node = createNode();
        FieldSpecList fields = FieldSpecList().add(FieldSpec(field, fieldId, handle));
        queryeval::Blueprint::UP bp = searchable.createBlueprint(requestContext, fields, *node);
        bp->fetchPostings(ExecuteInfo::create(strict));
        SearchIterator::UP sb = bp->createSearch(*md, strict);
        return (dynamic_cast<WeightedSetTermSearch*>(sb.get()) != 0);
    }

    FakeResult search(Searchable &searchable, const std::string &field, bool strict) const {
        FakeRequestContext requestContext;
        MatchData::UP md = layout.createMatchData();
        if (term_is_not_needed) {
            md->resolveTermField(handle)->tagAsNotNeeded();
        }
        Node::UP node = createNode();
        FieldSpecList fields = FieldSpecList().add(FieldSpec(field, fieldId, handle, field_is_filter));
        queryeval::Blueprint::UP bp = searchable.createBlueprint(requestContext, fields, *node);
        bp->fetchPostings(ExecuteInfo::create(strict));
        SearchIterator::UP sb = bp->createSearch(*md, strict);
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

struct MockSearch : public SearchIterator {
    int seekCnt;
    int _initial;
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
    MockFixture(uint32_t initial) : mock(0), tfmd(), search() {
        std::vector<SearchIterator*> children;
        std::vector<int32_t> weights;
        mock = new MockSearch(initial);
        children.push_back(mock);
        weights.push_back(1);
        search = WeightedSetTermSearch::create(children, tfmd, false, weights, MatchData::UP(nullptr));
    }
};

} // namespace <unnamed>

void run_simple(bool field_is_filter, bool term_is_not_needed)
{
    FakeSearchable index;
    setupFakeSearchable(index);
    FakeResult expect;
    if (field_is_filter || term_is_not_needed) {
        expect.doc(3)
            .doc(5)
            .doc(7);
    } else {
        expect.doc(3).elem(0).weight(30).pos(0)
            .doc(5).elem(0).weight(50).pos(0)
            .doc(7).elem(0).weight(70).pos(0);
    }
    WS ws = WS().add("7", 70).add("5", 50).add("3", 30).add("100", 1000)
            .set_field_is_filter(field_is_filter)
            .set_term_is_not_needed(term_is_not_needed);
;
    EXPECT_TRUE(ws.isGenericSearch(index, "field", true));
    EXPECT_TRUE(ws.isGenericSearch(index, "field", false));
    EXPECT_TRUE(ws.isGenericSearch(index, "multi-field", true));
    EXPECT_TRUE(ws.isGenericSearch(index, "multi-field", false));

    EXPECT_EQUAL(expect, ws.search(index, "field", true));
    EXPECT_EQUAL(expect, ws.search(index, "field", false));
    EXPECT_EQUAL(expect, ws.search(index, "multi-field", true));
    EXPECT_EQUAL(expect, ws.search(index, "multi-field", false));
}

TEST("testSimple") {
    TEST_DO(run_simple(false, false));
}

TEST("testSimple filter field") {
    TEST_DO(run_simple(true, false));
}

TEST("testSimple unranked") {
    TEST_DO(run_simple(false, true));
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
    WS ws = WS().add("7", 70).add("5", 50).add("3", 30)
            .add("15", 150).add("13", 130)
            .add("23", 230).add("100", 1000)
            .set_field_is_filter(field_is_filter)
            .set_term_is_not_needed(term_is_not_needed);
    EXPECT_TRUE(ws.isGenericSearch(index, "multi-field", true));
    EXPECT_TRUE(ws.isGenericSearch(index, "multi-field", false));

    EXPECT_EQUAL(expect, ws.search(index, "multi-field", true));
    EXPECT_EQUAL(expect, ws.search(index, "multi-field", false));
}

TEST("testMulti") {
    TEST_DO(run_multi(false, false));
}

TEST("testMulti filter field") {
    TEST_DO(run_multi(true, false));
}

TEST("testMulti unranked") {
    TEST_DO(run_multi(false, true));
}

TEST_F("test Eager Empty Child", MockFixture(search::endDocId)) {
    MockSearch *mock = f1.mock;
    SearchIterator &search = *f1.search;
    search.initFullRange();
    EXPECT_EQUAL(search.beginId(), search.getDocId());
    EXPECT_TRUE(!search.seek(1));
    EXPECT_TRUE(search.isAtEnd());
    EXPECT_EQUAL(0, mock->seekCnt);
}

TEST_F("test Eager Matching Child", MockFixture(5)) {
    MockSearch *mock = f1.mock;
    SearchIterator &search = *f1.search;
    search.initFullRange();
    EXPECT_EQUAL(search.beginId(), search.getDocId());
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

class IteratorChildrenVerifier : public search::test::IteratorChildrenVerifier {
private:
    SearchIterator::UP create(const std::vector<SearchIterator*> &children) const override {
        return SearchIterator::UP(WeightedSetTermSearch::create(children, _tfmd, false, _weights, MatchData::UP(nullptr)));
    }
};

class WeightIteratorChildrenVerifier : public search::test::DwaIteratorChildrenVerifier {
private:
    SearchIterator::UP create(std::vector<DocumentWeightIterator> && children) const override {
        return SearchIterator::UP(WeightedSetTermSearch::create(_tfmd, false, _weights, std::move(children)));
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

struct VerifyMatchData {
    struct MyBlueprint : search::queryeval::SimpleLeafBlueprint {
        VerifyMatchData &vmd;
        MyBlueprint(VerifyMatchData &vmd_in, FieldSpec spec_in)
            : SimpleLeafBlueprint(spec_in), vmd(vmd_in) {}
        SearchIterator::UP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool) const override {
            EXPECT_EQUAL(tfmda.size(), 1u);
            EXPECT_TRUE(tfmda[0] != nullptr);
            if (vmd.child_tfmd == nullptr) {
                vmd.child_tfmd = tfmda[0];
            } else {
                EXPECT_EQUAL(vmd.child_tfmd, tfmda[0]);
            }
            ++vmd.child_cnt;
            return std::make_unique<EmptySearch>();
        }
        SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
            return create_default_filter(strict, constraint);
        }
    };
    size_t child_cnt = 0;
    TermFieldMatchData *child_tfmd = nullptr;
    search::queryeval::Blueprint::UP create(const FieldSpec &spec) {
        return std::make_unique<MyBlueprint>(*this, spec);
    }
};

TEST("require that children get a common (yet separate) term field match data") {
    VerifyMatchData vmd;
    MatchDataLayout layout;
    auto top_handle = layout.allocTermField(42);
    FieldSpec top_spec("foo", 42, top_handle);
    WeightedSetTermBlueprint blueprint(top_spec);
    for (size_t i = 0; i < 5; ++i) {
        blueprint.addTerm(vmd.create(blueprint.getNextChildField(top_spec)), 1);
    }
    auto match_data = layout.createMatchData();
    auto search = blueprint.createSearch(*match_data, true);
    auto top_tfmd = match_data->resolveTermField(top_handle);
    EXPECT_EQUAL(vmd.child_cnt, 5u);
    EXPECT_TRUE(vmd.child_tfmd != nullptr);
    EXPECT_NOT_EQUAL(top_tfmd, vmd.child_tfmd);
}

TEST_MAIN() { TEST_RUN_ALL(); }
