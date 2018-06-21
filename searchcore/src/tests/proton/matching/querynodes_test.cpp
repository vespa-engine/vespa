// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for querynodes.

#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/fakesearchcontext.h>
#include <vespa/searchcore/proton/matching/blueprintbuilder.h>
#include <vespa/searchcore/proton/matching/matchdatareservevisitor.h>
#include <vespa/searchcore/proton/matching/resolveviewvisitor.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/fieldtype.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/queryeval/nearsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/ranksearch.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/simple_phrase_search.h>
#include <vespa/searchlib/queryeval/same_element_search.h>
#include <vespa/searchlib/queryeval/sourceblendersearch.h>
#include <vespa/searchlib/queryeval/fake_search.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/singlenumericattribute.hpp>

#include <vespa/log/log.h>
LOG_SETUP("querynodes_test");

using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::fef::test::IndexEnvironment;
using search::query::Node;
using search::query::QueryBuilder;
using search::queryeval::AndNotSearch;
using search::queryeval::AndSearch;
using search::queryeval::Blueprint;
using search::queryeval::EmptySearch;
using search::queryeval::FakeRequestContext;
using search::queryeval::FakeResult;
using search::queryeval::FakeSearch;
using search::queryeval::FieldSpec;
using search::queryeval::ISourceSelector;
using search::queryeval::NearSearch;
using search::queryeval::ONearSearch;
using search::queryeval::OrSearch;
using search::queryeval::RankSearch;
using search::queryeval::SameElementSearch;
using search::queryeval::SearchIterator;
using search::queryeval::Searchable;
using search::queryeval::SimplePhraseSearch;
using search::queryeval::SourceBlenderSearch;
using std::string;
using std::vector;
using namespace proton::matching;
namespace fef_test = search::fef::test;
using CollectionType = FieldInfo::CollectionType;

namespace {

template <typename T> void checkTwoFieldsTwoAttributesTwoIndexes();
template <typename T> void checkTwoFieldsTwoAttributesOneIndex();
template <typename T> void checkOneFieldOneAttributeTwoIndexes();
template <typename T> void checkOneFieldNoAttributesTwoIndexes();
template <typename T> void checkTwoFieldsNoAttributesTwoIndexes();
template <typename T> void checkOneFieldNoAttributesOneIndex();

template <typename T> void checkProperBlending();
template <typename T> void checkProperBlendingWithParent();

const string term = "term";
const string phrase_term1 = "hello";
const string phrase_term2 = "world";
const string view = "view";
const uint32_t id = 3;
const search::query::Weight weight(7);
const string field[] = { "field1", "field2" };
const string attribute[] = { "attribute1", "attribute2" };
const string source_tag[] = { "Source 1", "Source 2" };
const string attribute_tag = "Attribute source";
const uint32_t distance = 13;

template <class SearchType>
class Create {
    bool _strict;
    typename SearchType::Children _children;

public:
    explicit Create(bool strict = true) : _strict(strict) {}

    Create &add(SearchIterator *s) {
        _children.push_back(s);
        return *this;
    }

    operator SearchIterator *() const {
        return SearchType::create(_children, _strict);
    }
};
typedef Create<OrSearch>     MyOr;

class ISourceSelectorDummy : public ISourceSelector
{
public:
    static SourceStore _sourceStoreDummy;
    using Iterator = search::queryeval::sourceselector::Iterator;

    static std::unique_ptr<Iterator>
    makeDummyIterator()
    {
        return std::make_unique<Iterator>(_sourceStoreDummy);
    }
};

ISourceSelector::SourceStore ISourceSelectorDummy::_sourceStoreDummy("foo");


typedef uint32_t SourceId;
class Blender {
    bool _strict;
    SourceBlenderSearch::Children _children;

public:
    explicit Blender(bool strict = true) : _strict(strict) {}

    Blender &add(SourceId source_id, SearchIterator *search) {
        _children.push_back(SourceBlenderSearch::Child(search, source_id));
        return *this;
    }

    operator SearchIterator *() const {
        return SourceBlenderSearch::create(
                ISourceSelectorDummy::makeDummyIterator(), _children, _strict);
    }
};

SearchIterator *getTerm(const string &trm, const string &fld, const string &tag) {
    static TermFieldMatchData tmd;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&tmd);
    return new FakeSearch(tag, fld, trm, FakeResult(), tfmda);
}

class IteratorStructureTest {
    int _field_count;
    int _attribute_count;
    int _index_count;

public:
    void setFieldCount(int count) { _field_count = count; }
    void setAttributeCount(int count) { _attribute_count = count; }
    void setIndexCount(int count) { _index_count = count; }

    string getIteratorAsString(Node &node) {
        ViewResolver resolver;
        for (int i = 0; i < _field_count; ++i) {
            resolver.add(view, field[i]);
        }
        for (int i = 0; i < _attribute_count; ++i) {
            resolver.add(view, attribute[i]);
        }

        fef_test::IndexEnvironment index_environment;
        uint32_t fieldId = 0;
        for (int i = 0; i < _field_count; ++i) {
            FieldInfo field_info(FieldType::INDEX, CollectionType::SINGLE, field[i], fieldId++);
            index_environment.getFields().push_back(field_info);
        }
        for (int i = 0; i < _attribute_count; ++i) {
            FieldInfo field_info(FieldType::ATTRIBUTE, CollectionType::SINGLE, attribute[i], fieldId++);
            index_environment.getFields().push_back(field_info);
        }

        ResolveViewVisitor resolve_visitor(resolver, index_environment);
        node.accept(resolve_visitor);

        FakeSearchContext context;
        context.attr().tag(attribute_tag);

        for (int i = 0; i < _index_count; ++i) {
            context.addIdx(i).idx(i).getFake().tag(source_tag[i]);
        }

        MatchDataLayout mdl;
        FakeRequestContext requestContext;
        MatchDataReserveVisitor reserve_visitor(mdl);
        node.accept(reserve_visitor);
        MatchData::UP match_data = mdl.createMatchData();

        Blueprint::UP blueprint = BlueprintBuilder::build(requestContext, node, context);
        blueprint->fetchPostings(true);
        return blueprint->createSearch(*match_data, true)->asString();
    }

    template <typename Tag> string getIteratorAsString();
};

typedef QueryBuilder<ProtonNodeTypes> QB;
struct Phrase { void addToBuilder(QB& b) { b.addPhrase(2, view, id, weight); }};
struct SameElement { void addToBuilder(QB& b) { b.addSameElement(2, view); }};
struct Near   { void addToBuilder(QB& b) { b.addNear(2, distance); } };
struct ONear  { void addToBuilder(QB& b) { b.addONear(2, distance); } };
struct Or     { void addToBuilder(QB& b) { b.addOr(2); } };
struct And    { void addToBuilder(QB& b) { b.addAnd(2); } };
struct AndNot { void addToBuilder(QB& b) { b.addAndNot(2); } };
struct Rank   { void addToBuilder(QB& b) { b.addRank(2); } };
struct Term {};

template <typename Tag>
string IteratorStructureTest::getIteratorAsString() {
    QueryBuilder<ProtonNodeTypes> query_builder;
    Tag().addToBuilder(query_builder);
    query_builder.addStringTerm(phrase_term1, view, id, weight);
    query_builder.addStringTerm(phrase_term2, view, id, weight);
    Node::UP node = query_builder.build();
    return getIteratorAsString(*node);
}

template <>
string IteratorStructureTest::getIteratorAsString<Term>() {
    ProtonStringTerm node(term, view, id, weight);
    return getIteratorAsString(node);
}

template <typename T>
SearchIterator *getLeaf(const string &fld, const string &tag) {
    return getTerm(term, fld, tag);
}

template <>
SearchIterator *getLeaf<Phrase>(const string &fld, const string &tag) {
    SimplePhraseSearch::Children children;
    children.push_back(getTerm(phrase_term1, fld, tag));
    children.push_back(getTerm(phrase_term2, fld, tag));
    static TermFieldMatchData tmd;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&tmd).add(&tmd);
    vector<uint32_t> eval_order(2);
    return new SimplePhraseSearch(children, MatchData::UP(), tfmda, eval_order, tmd, true);
}

template <typename NearType>
SearchIterator *getNearParent(SearchIterator *a, SearchIterator *b) {
    typename NearType::Children children;
    children.push_back(a);
    children.push_back(b);
    TermFieldMatchDataArray data;
    static TermFieldMatchData tmd;
    // we only check how many term/field combinations
    // are below the NearType parent:
    // two terms searching in (two index fields + two attribute fields)
    data.add(&tmd).add(&tmd).add(&tmd).add(&tmd)
        .add(&tmd).add(&tmd).add(&tmd).add(&tmd);
    return new NearType(children, data, distance, true);
}

template <typename SearchType>
SearchIterator *getSimpleParent(SearchIterator *a, SearchIterator *b) {
    typename SearchType::Children children;
    children.push_back(a);
    children.push_back(b);
    return SearchType::create(children, true);
}

template <typename T>
SearchIterator *getParent(SearchIterator *a, SearchIterator *b);

template <>
SearchIterator *getParent<Near>(SearchIterator *a, SearchIterator *b) {
    return getNearParent<NearSearch>(a, b);
}

template <>
SearchIterator *getParent<ONear>(SearchIterator *a, SearchIterator *b) {
    return getNearParent<ONearSearch>(a, b);
}

template <>
SearchIterator *getParent<SameElement>(SearchIterator *a, SearchIterator *b) {
    std::vector<SearchIterator::UP> children;
    children.emplace_back(a);
    children.emplace_back(b);
    TermFieldMatchDataArray data;
    static TermFieldMatchData tmd;
    // we only check how many term/field combinations
    // are below the SameElement parent:
    // two terms searching in one index field
    data.add(&tmd).add(&tmd);
    return new SameElementSearch(nullptr, std::move(children), data, true);
}

template <>
SearchIterator *getParent<Or>(SearchIterator *a, SearchIterator *b) {
    return getSimpleParent<OrSearch>(a, b);
}

template <>
SearchIterator *getParent<And>(SearchIterator *a, SearchIterator *b) {
    return getSimpleParent<AndSearch>(a, b);
}

template <>
SearchIterator *getParent<AndNot>(SearchIterator *a, SearchIterator *b) {
    return getSimpleParent<AndNotSearch>(a, b);
}

template <>
SearchIterator *getParent<Rank>(SearchIterator *a, SearchIterator *b) {
    return getSimpleParent<RankSearch>(a, b);
}

template <typename T> bool bothStrict() { return false; }

template <> bool bothStrict<Or>() { return true; }

template <typename T>
void checkTwoFieldsTwoAttributesTwoIndexes() {
    IteratorStructureTest structure_test;
    structure_test.setFieldCount(2);
    structure_test.setAttributeCount(2);
    structure_test.setIndexCount(2);

    SearchIterator::UP expected(
            MyOr()
            .add(getLeaf<T>(attribute[0], attribute_tag))
            .add(getLeaf<T>(attribute[1], attribute_tag))
            .add(Blender()
                 .add(SourceId(0), MyOr()
                      .add(getLeaf<T>(field[0], source_tag[0]))
                      .add(getLeaf<T>(field[1], source_tag[0])))
                 .add(SourceId(1), MyOr()
                      .add(getLeaf<T>(field[0], source_tag[1]))
                      .add(getLeaf<T>(field[1], source_tag[1])))));
    EXPECT_EQUAL(expected->asString(), structure_test.getIteratorAsString<T>());
}

template <typename T>
void checkTwoFieldsTwoAttributesOneIndex() {
    IteratorStructureTest structure_test;
    structure_test.setFieldCount(2);
    structure_test.setAttributeCount(2);
    structure_test.setIndexCount(1);

    SearchIterator::UP expected(
            MyOr()
            .add(getLeaf<T>(attribute[0], attribute_tag))
            .add(getLeaf<T>(attribute[1], attribute_tag))
            .add(Blender()
                 .add(SourceId(0), MyOr()
                      .add(getLeaf<T>(field[0], source_tag[0]))
                      .add(getLeaf<T>(field[1], source_tag[0])))));
    EXPECT_EQUAL(expected->asString(), structure_test.getIteratorAsString<T>());
}

template <typename T>
void checkOneFieldOneAttributeTwoIndexes() {
    IteratorStructureTest structure_test;
    structure_test.setFieldCount(1);
    structure_test.setAttributeCount(1);
    structure_test.setIndexCount(2);

    SearchIterator::UP expected(
            MyOr()
            .add(getLeaf<T>(attribute[0], attribute_tag))
            .add(Blender()
                 .add(SourceId(0),
                      getLeaf<T>(field[0], source_tag[0]))
                 .add(SourceId(1),
                      getLeaf<T>(field[0], source_tag[1]))));
    EXPECT_EQUAL(expected->asString(), structure_test.getIteratorAsString<T>());
}

template <typename T>
void checkOneFieldNoAttributesTwoIndexes() {
    IteratorStructureTest structure_test;
    structure_test.setFieldCount(1);
    structure_test.setAttributeCount(0);
    structure_test.setIndexCount(2);

    SearchIterator::UP expected(
            Blender()
            .add(SourceId(0), getLeaf<T>(field[0], source_tag[0]))
            .add(SourceId(1), getLeaf<T>(field[0], source_tag[1])));
    EXPECT_EQUAL(expected->asString(), structure_test.getIteratorAsString<T>());
}

template <typename T>
void checkTwoFieldsNoAttributesTwoIndexes() {
    IteratorStructureTest structure_test;
    structure_test.setFieldCount(2);
    structure_test.setAttributeCount(0);
    structure_test.setIndexCount(2);

    SearchIterator::UP expected(
            Blender()
            .add(SourceId(0), MyOr()
                 .add(getLeaf<T>(field[0], source_tag[0]))
                 .add(getLeaf<T>(field[1], source_tag[0])))
            .add(SourceId(1), MyOr()
                 .add(getLeaf<T>(field[0], source_tag[1]))
                 .add(getLeaf<T>(field[1], source_tag[1]))));
    EXPECT_EQUAL(expected->asString(), structure_test.getIteratorAsString<T>());
}

template <typename T>
void checkOneFieldNoAttributesOneIndex() {
    IteratorStructureTest structure_test;
    structure_test.setFieldCount(1);
    structure_test.setAttributeCount(0);
    structure_test.setIndexCount(1);

    SearchIterator::UP expected(
            Blender()
            .add(SourceId(0), getLeaf<T>(field[0], source_tag[0])));
    EXPECT_EQUAL(expected->asString(), structure_test.getIteratorAsString<T>());
}

template <typename T>
void checkProperBlending() {
    TEST_DO(checkTwoFieldsTwoAttributesTwoIndexes<T>());
    TEST_DO(checkTwoFieldsTwoAttributesOneIndex<T>());
    TEST_DO(checkOneFieldOneAttributeTwoIndexes<T>());
    TEST_DO(checkOneFieldNoAttributesTwoIndexes<T>());
    TEST_DO(checkTwoFieldsNoAttributesTwoIndexes<T>());
    TEST_DO(checkOneFieldNoAttributesOneIndex<T>());
}


template <typename T>
void checkProperBlendingWithParent() {
    IteratorStructureTest structure_test;
    structure_test.setFieldCount(2);
    structure_test.setAttributeCount(2);
    structure_test.setIndexCount(2);

    SearchIterator::UP expected(
            getParent<T>(
                    MyOr()
                    .add(getTerm(phrase_term1, attribute[0], attribute_tag))
                    .add(getTerm(phrase_term1, attribute[1], attribute_tag))
                    .add(Blender()
                         .add(SourceId(0), MyOr()
                              .add(getTerm(phrase_term1, field[0], source_tag[0]))
                              .add(getTerm(phrase_term1, field[1], source_tag[0])))
                         .add(SourceId(1), MyOr()
                              .add(getTerm(phrase_term1, field[0], source_tag[1]))
                              .add(getTerm(phrase_term1, field[1], source_tag[1])))),
                    MyOr(bothStrict<T>())
                    .add(getTerm(phrase_term2, attribute[0], attribute_tag))
                    .add(getTerm(phrase_term2, attribute[1], attribute_tag))
                    .add(Blender(bothStrict<T>())
                         .add(SourceId(0), MyOr(bothStrict<T>())
                              .add(getTerm(phrase_term2, field[0], source_tag[0]))
                              .add(getTerm(phrase_term2, field[1], source_tag[0])))
                         .add(SourceId(1), MyOr(bothStrict<T>())
                              .add(getTerm(phrase_term2, field[0], source_tag[1]))
                              .add(getTerm(phrase_term2, field[1], source_tag[1]))))));
    EXPECT_EQUAL(expected->asString(), structure_test.getIteratorAsString<T>());
}

template <>
void checkProperBlendingWithParent<SameElement>() {
    using T = SameElement;
    IteratorStructureTest structure_test;
    structure_test.setFieldCount(1);
    structure_test.setAttributeCount(0);
    structure_test.setIndexCount(2);

    SearchIterator::UP expected(
            getParent<T>(Blender()
                         .add(SourceId(0), getTerm(phrase_term1, field[0], source_tag[0]))
                         .add(SourceId(1), getTerm(phrase_term1, field[0], source_tag[1])),
                         Blender(bothStrict<T>())
                         .add(SourceId(0), getTerm(phrase_term2, field[0], source_tag[0]))
                         .add(SourceId(1), getTerm(phrase_term2, field[0], source_tag[1]))));
    EXPECT_EQUAL(expected->asString(), structure_test.getIteratorAsString<T>());
}

TEST("requireThatTermNodeSearchIteratorsGetProperBlending") {
    TEST_DO(checkProperBlending<Term>());
}

TEST("requireThatPhrasesGetProperBlending") {
    TEST_DO(checkProperBlending<Phrase>());
}

TEST("requireThatSameElementGetProperBlending") {
    TEST_DO(checkProperBlendingWithParent<SameElement>());
}

TEST("requireThatNearGetProperBlending") {
    TEST_DO(checkProperBlendingWithParent<Near>());
}

TEST("requireThatONearGetProperBlending") {
    TEST_DO(checkProperBlendingWithParent<ONear>());
}

TEST("requireThatSimpleIntermediatesGetProperBlending") {
    TEST_DO(checkProperBlendingWithParent<And>());
    TEST_DO(checkProperBlendingWithParent<AndNot>());
    TEST_DO(checkProperBlendingWithParent<Or>());
    TEST_DO(checkProperBlendingWithParent<Rank>());
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
