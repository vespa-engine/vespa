// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/fake_result.h>
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/simple_phrase_blueprint.h>
#include <vespa/searchlib/queryeval/simple_phrase_search.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/vespalib/util/testclock.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("simple_phrase_test");

using namespace search::queryeval;

using search::fef::MatchData;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldHandle;
using search::query::SimpleStringTerm;
using search::query::Weight;
using std::unique_ptr;
using std::copy;
using std::string;
using std::vector;

namespace {

struct MyTerm : public search::queryeval::SimpleLeafBlueprint {
    MyTerm(const FieldSpec &field, uint32_t hits)
        : search::queryeval::SimpleLeafBlueprint(field)
    {
        setEstimate(HitEstimate(hits, (hits == 0)));
    }
    SearchIterator::UP createLeafSearch(const search::fef::TermFieldMatchDataArray &, bool) const override {
        return SearchIterator::UP();
    }
    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
        return create_default_filter(strict, constraint);
    }
};

class Test : public vespalib::TestApp {
    void requireThatIteratorFindsSimplePhrase(bool useBlueprint);
    void requireThatIteratorFindsLongPhrase(bool useBlueprint);
    void requireThatStrictIteratorFindsNextMatch(bool useBlueprint);
    void requireThatPhrasesAreUnpacked(bool useBlueprint, bool unpack_normal_features, bool unpack_interleaved_features);
    void requireThatTermsCanBeEvaluatedInPriorityOrder();
    void requireThatBlueprintExposesFieldWithEstimate();
    void requireThatBlueprintForcesPositionDataOnChildren();

public:
    int Main() override;
};

int
Test::Main()
{
    TEST_INIT("phrasesearch_test");

    TEST_DO(requireThatIteratorFindsSimplePhrase(false));
    TEST_DO(requireThatIteratorFindsLongPhrase(false));
    TEST_DO(requireThatStrictIteratorFindsNextMatch(false));
    TEST_DO(requireThatPhrasesAreUnpacked(false, true, false));
    TEST_DO(requireThatPhrasesAreUnpacked(false, true, true));
    TEST_DO(requireThatPhrasesAreUnpacked(false, false, false));
    TEST_DO(requireThatPhrasesAreUnpacked(false, false, true));
    TEST_DO(requireThatTermsCanBeEvaluatedInPriorityOrder());

    TEST_DO(requireThatIteratorFindsSimplePhrase(true));
    TEST_DO(requireThatIteratorFindsLongPhrase(true));
    TEST_DO(requireThatStrictIteratorFindsNextMatch(true));
    TEST_DO(requireThatPhrasesAreUnpacked(true, true, false));
    TEST_DO(requireThatPhrasesAreUnpacked(true, true, true));
    TEST_DO(requireThatPhrasesAreUnpacked(true, false, false));
    TEST_DO(requireThatPhrasesAreUnpacked(true, false, true));
    TEST_DO(requireThatBlueprintExposesFieldWithEstimate());
    TEST_DO(requireThatBlueprintForcesPositionDataOnChildren());

    TEST_DONE();
}

const string field = "field";
const uint32_t fieldId = 1;
const uint32_t doc_match = 42;
const uint32_t doc_no_match = 43;
const uint32_t phrase_handle = 1;

class PhraseSearchTest
{
private:
    FakeRequestContext      _requestContext;
    FakeSearchable          _index;
    FieldSpec               _phrase_fs;
    SimplePhraseBlueprint   _phrase;
    std::vector<Blueprint::UP> _children;
    MatchData::UP           _md;
    vector<uint32_t>        _order;
    uint32_t                _pos;
    bool                    _strict;

public:
    PhraseSearchTest(bool expiredDoom=false);
    ~PhraseSearchTest();

    TermFieldHandle childHandle(uint32_t idx) const { return (10 * idx + 11); }

    void setStrict(bool strict) { _strict = strict; }
    void setOrder(const vector<uint32_t> &order) { _order = order; }
    const TermFieldMatchData &tmd() const { return *_md->resolveTermField(phrase_handle); }
    TermFieldMatchData &writable_term_field_match_data() { return *_md->resolveTermField(phrase_handle); }

    PhraseSearchTest &addTerm(const string &term, bool last) {
        return addTerm(term, FakeResult()
                       .doc(doc_match).pos(_pos)
                       .doc(doc_no_match).pos(_pos + last));
    }

    PhraseSearchTest &addTerm(const string &term, const FakeResult &r) {
        _index.addResult(field, term, r);
        ++_pos;
        SimpleStringTerm term_node(term, field, 0, Weight(0));
        {
            // make one child blueprint for explicit use
            FieldSpecList fields;
            fields.add(FieldSpec(field, fieldId,
                                 childHandle(_children.size())));
            _children.push_back(_index.createBlueprint(_requestContext, fields, term_node));
        }
        {
            // and one to be used by the phrase blueprint
            FieldSpecList fields;
            fields.add(_phrase.getNextChildField(_phrase_fs));
            _phrase.addTerm(_index.createBlueprint(_requestContext, fields, term_node));
        }
        _order.push_back(_order.size());
        return *this;
    }

    void
    fetchPostings(bool useBlueprint)
    {
        ExecuteInfo execInfo = ExecuteInfo::create(_strict);
        if (useBlueprint) {
            _phrase.fetchPostings(execInfo);
            return;
        }
        for (size_t i = 0; i < _children.size(); ++i) {
            _children[i]->fetchPostings(execInfo);
        }
    }

    // NB: using blueprint will ignore eval order override
    SearchIterator *createSearch(bool useBlueprint) {
        SearchIterator::UP search;
        if (useBlueprint) {
            search = _phrase.createSearch(*_md, _strict);
        } else {
            search::fef::TermFieldMatchDataArray childMatch;
            for (size_t i = 0; i < _children.size(); ++i) {
                auto *child_term_field_match_data = _md->resolveTermField(childHandle(i));
                child_term_field_match_data->setNeedInterleavedFeatures(tmd().needs_interleaved_features());
                child_term_field_match_data->setNeedNormalFeatures(true);
                childMatch.add(child_term_field_match_data);
            }
            SimplePhraseSearch::Children children;
            for (size_t i = 0; i < _children.size(); ++i) {
                children.push_back(_children[i]->createSearch(*_md, _strict));
            }
            search = std::make_unique<SimplePhraseSearch>(std::move(children),
                                                          MatchData::UP(), childMatch, _order,
                                                          *_md->resolveTermField(phrase_handle),
                                                          _strict);
        }
        search->initFullRange();
        return search.release();
    }
};

PhraseSearchTest::PhraseSearchTest(bool expiredDoom)
    : _requestContext(nullptr, expiredDoom ? vespalib::steady_time(): vespalib::steady_time::max()),
      _index(),
      _phrase_fs(field, fieldId, phrase_handle),
      _phrase(_phrase_fs, false),
      _children(),
      _md(MatchData::makeTestInstance(100, 10)),
      _order(),
      _pos(1),
      _strict(false)
{}
PhraseSearchTest::~PhraseSearchTest() = default;

void Test::requireThatIteratorFindsSimplePhrase(bool useBlueprint) {
    PhraseSearchTest test;
    test.addTerm("foo", 0).addTerm("bar", 1);

    test.fetchPostings(useBlueprint);
    unique_ptr<SearchIterator> search(test.createSearch(useBlueprint));
    EXPECT_TRUE(!search->seek(1u));
    EXPECT_TRUE(search->seek(doc_match));
    EXPECT_TRUE(!search->seek(doc_no_match));
}

void Test::requireThatIteratorFindsLongPhrase(bool useBlueprint) {
    PhraseSearchTest test;
    test.addTerm("foo", 0).addTerm("bar", 0).addTerm("baz", 0)
        .addTerm("qux", 1);

    test.fetchPostings(useBlueprint);
    unique_ptr<SearchIterator> search(test.createSearch(useBlueprint));
    EXPECT_TRUE(!search->seek(1u));
    EXPECT_TRUE(search->seek(doc_match));
    EXPECT_TRUE(!search->seek(doc_no_match));
}

void Test::requireThatStrictIteratorFindsNextMatch(bool useBlueprint) {
    PhraseSearchTest test;
    test.setStrict(true);
    test.addTerm("foo", 0).addTerm("bar", 1);

    test.fetchPostings(useBlueprint);
    unique_ptr<SearchIterator> search(test.createSearch(useBlueprint));
    EXPECT_TRUE(!search->seek(1u));
    EXPECT_EQUAL(doc_match, search->getDocId());
    EXPECT_TRUE(!search->seek(doc_no_match));
    EXPECT_TRUE(search->isAtEnd());
}

void Test::requireThatPhrasesAreUnpacked(bool useBlueprint, bool unpack_normal_features, bool unpack_interleaved_features) {
    PhraseSearchTest test;
    test.addTerm("foo", FakeResult()
                 .doc(doc_match).pos(1).pos(11).pos(21).field_length(30).num_occs(3));
    test.addTerm("bar", FakeResult()
                 .doc(doc_match).pos(2).pos(16).pos(22).field_length(30).num_occs(3));
    test.writable_term_field_match_data().setNeedNormalFeatures(unpack_normal_features);
    test.writable_term_field_match_data().setNeedInterleavedFeatures(unpack_interleaved_features);
    test.fetchPostings(useBlueprint);
    unique_ptr<SearchIterator> search(test.createSearch(useBlueprint));
    EXPECT_TRUE(search->seek(doc_match));
    search->unpack(doc_match);

    EXPECT_EQUAL(doc_match, test.tmd().getDocId());
    if (unpack_normal_features) {
        EXPECT_EQUAL(2, std::distance(test.tmd().begin(), test.tmd().end()));
        EXPECT_EQUAL(1u, test.tmd().begin()->getPosition());
        EXPECT_EQUAL(21u, (test.tmd().begin() + 1)->getPosition());
    } else {
        EXPECT_EQUAL(0, std::distance(test.tmd().begin(), test.tmd().end()));
    }
    if (unpack_interleaved_features) {
        EXPECT_EQUAL(2u, test.tmd().getNumOccs());
        EXPECT_EQUAL(30u, test.tmd().getFieldLength());
    } else {
        EXPECT_EQUAL(0u, test.tmd().getNumOccs());
        EXPECT_EQUAL(0u, test.tmd().getFieldLength());
    }
}

void Test::requireThatTermsCanBeEvaluatedInPriorityOrder() {
    vector<uint32_t> order;
    order.push_back(2);
    order.push_back(0);
    order.push_back(1);
    PhraseSearchTest test;
    test.addTerm("foo", 0).addTerm("bar", 1).addTerm("baz", 1);
    test.setOrder(order);

    test.fetchPostings(false);
    unique_ptr<SearchIterator> search(test.createSearch(false));
    EXPECT_TRUE(!search->seek(1u));
    EXPECT_TRUE(search->seek(doc_match));
    EXPECT_TRUE(!search->seek(doc_no_match));
}

void
Test::requireThatBlueprintExposesFieldWithEstimate()
{
    FieldSpec f("foo", 1, 1);
    SimplePhraseBlueprint phrase(f, false);
    ASSERT_TRUE(phrase.getState().numFields() == 1);
    EXPECT_EQUAL(f.getFieldId(), phrase.getState().field(0).getFieldId());
    EXPECT_EQUAL(f.getHandle(), phrase.getState().field(0).getHandle());

    EXPECT_EQUAL(true, phrase.getState().estimate().empty);
    EXPECT_EQUAL(0u, phrase.getState().estimate().estHits);

    phrase.addTerm(Blueprint::UP(new MyTerm(phrase.getNextChildField(f), 10)));
    EXPECT_EQUAL(false, phrase.getState().estimate().empty);
    EXPECT_EQUAL(10u, phrase.getState().estimate().estHits);

    phrase.addTerm(Blueprint::UP(new MyTerm(phrase.getNextChildField(f), 5)));
    EXPECT_EQUAL(false, phrase.getState().estimate().empty);
    EXPECT_EQUAL(5u, phrase.getState().estimate().estHits);

    phrase.addTerm(Blueprint::UP(new MyTerm(phrase.getNextChildField(f), 20)));
    EXPECT_EQUAL(false, phrase.getState().estimate().empty);
    EXPECT_EQUAL(5u, phrase.getState().estimate().estHits);
}

void
Test::requireThatBlueprintForcesPositionDataOnChildren()
{
    FieldSpec f("foo", 1, 1, true);
    SimplePhraseBlueprint phrase(f, false);
    EXPECT_TRUE(f.isFilter());
    EXPECT_TRUE(!phrase.getNextChildField(f).isFilter());
}

} // namespace

TEST_APPHOOK(Test);
