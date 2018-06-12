// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/log/log.h>
LOG_SETUP("matching_test");

#include <vespa/document/base/globalid.h>
#include <initializer_list>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/matching/error_constant_value.h>
#include <vespa/searchcore/proton/matching/fakesearchcontext.h>
#include <vespa/searchcore/proton/matching/i_constant_value_repo.h>
#include <vespa/searchcore/proton/matching/isearchcontext.h>
#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchlib/aggregation/aggregation.h>
#include <vespa/searchlib/aggregation/grouping.h>
#include <vespa/searchlib/aggregation/perdocexpression.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/common/transport.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>
#include <vespa/searchcore/proton/matching/match_params.h>
#include <vespa/searchcore/proton/matching/match_tools.h>
#include <vespa/searchcore/proton/matching/match_context.h>

using namespace proton::matching;
using namespace proton;
using namespace search::aggregation;
using namespace search::attribute;
using namespace search::engine;
using namespace search::expression;
using namespace search::fef;
using namespace search::grouping;
using namespace search::index;
using namespace search::query;
using namespace search::queryeval;
using namespace search;

using search::index::schema::DataType;
using storage::spi::Timestamp;

void inject_match_phase_limiting(Properties &setup, const vespalib::string &attribute, size_t max_hits, bool descending)
{
    Properties cfg;
    cfg.add(indexproperties::matchphase::DegradationAttribute::NAME, attribute);
    cfg.add(indexproperties::matchphase::DegradationAscendingOrder::NAME, descending ? "false" : "true");
    cfg.add(indexproperties::matchphase::DegradationMaxHits::NAME, vespalib::make_string("%zu", max_hits));
    setup.import(cfg);
}

FakeResult make_elem_result(const std::vector<std::pair<uint32_t,std::vector<uint32_t> > > &match_data) {
    FakeResult result;
    uint32_t pos_should_be_ignored = 0;
    for (const auto &doc: match_data) {
        result.doc(doc.first);
        for (const auto &elem: doc.second) {
            result.elem(elem).pos(++pos_should_be_ignored);
        }
    }
    return result;
}

vespalib::string make_simple_stack_dump(const vespalib::string &field,
                                        const vespalib::string &term)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(term, field, 1, search::query::Weight(1));
    return StackDumpCreator::create(*builder.build());
}

vespalib::string make_same_element_stack_dump(const vespalib::string &a1_term,
                                              const vespalib::string &f1_term)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addSameElement(2, "");
    builder.addStringTerm(a1_term, "a1", 1, search::query::Weight(1));
    builder.addStringTerm(f1_term, "f1", 2, search::query::Weight(1));
    return StackDumpCreator::create(*builder.build());
}

//-----------------------------------------------------------------------------

const uint32_t NUM_DOCS = 1000;

//-----------------------------------------------------------------------------

class MyAttributeContext : public IAttributeContext
{
private:
    typedef std::map<string, IAttributeVector *> Map;
    Map _vectors;

public:
    const IAttributeVector *get(const string &name) const {
        if (_vectors.find(name) == _vectors.end()) {
            return 0;
        }
        return _vectors.find(name)->second;
    }
    virtual const IAttributeVector *
    getAttribute(const string &name) const override {
        return get(name);
    }
    virtual const IAttributeVector *
    getAttributeStableEnum(const string &name) const override {
        return get(name);
    }
    virtual void
    getAttributeList(std::vector<const IAttributeVector *> & list) const override {
        Map::const_iterator pos = _vectors.begin();
        Map::const_iterator end = _vectors.end();
        for (; pos != end; ++pos) {
            list.push_back(pos->second);
        }
    }
    ~MyAttributeContext() {
        Map::iterator pos = _vectors.begin();
        Map::iterator end = _vectors.end();
        for (; pos != end; ++pos) {
            delete pos->second;
        }
    }

    //-------------------------------------------------------------------------

    void add(IAttributeVector *attr) {
        _vectors[attr->getName()] = attr;
    }
};

struct EmptyConstantValueRepo : public proton::matching::IConstantValueRepo {
    virtual vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &) const override {
        return std::make_unique<proton::matching::ErrorConstantValue>();
    }
};

//-----------------------------------------------------------------------------

struct MyWorld {
    Schema                  schema;
    Properties              config;
    FakeSearchContext       searchContext;
    MyAttributeContext      attributeContext;
    SessionManager::SP      sessionManager;
    DocumentMetaStore       metaStore;
    MatchingStats           matchingStats;
    vespalib::Clock         clock;
    QueryLimiter            queryLimiter;
    EmptyConstantValueRepo  constantValueRepo;

    MyWorld();
    ~MyWorld();

    void basicSetup(size_t heapSize=10, size_t arraySize=100) {
        // schema
        schema.addIndexField(Schema::IndexField("f1", DataType::STRING));
        schema.addIndexField(Schema::IndexField("f2", DataType::STRING));
        schema.addIndexField(Schema::IndexField("tensor_field", DataType::TENSOR));
        schema.addAttributeField(Schema::AttributeField("a1", DataType::INT32));
        schema.addAttributeField(Schema::AttributeField("a2", DataType::INT32));
        schema.addAttributeField(Schema::AttributeField("predicate_field", DataType::BOOLEANTREE));

        // config
        config.add(indexproperties::rank::FirstPhase::NAME, "attribute(a1)");
        config.add(indexproperties::hitcollector::HeapSize::NAME, (vespalib::asciistream() << heapSize).str());
        config.add(indexproperties::hitcollector::ArraySize::NAME, (vespalib::asciistream() << arraySize).str());
        config.add(indexproperties::summary::Feature::NAME, "attribute(a1)");
        config.add(indexproperties::summary::Feature::NAME, "value(100)");
        config.add(indexproperties::dump::IgnoreDefaultFeatures::NAME, "true");
        config.add(indexproperties::dump::Feature::NAME, "attribute(a2)");

        // search context
        searchContext.setLimit(NUM_DOCS);
        searchContext.addIdx(0).addIdx(1);
        for (uint32_t i = 0; i < NUM_DOCS; ++i) {
            searchContext.selector().setSource(i, i % 2); // even -> 0
                                                    // odd  -> 1
        }

        // attribute context
        {
            SingleInt32ExtAttribute *attr = new SingleInt32ExtAttribute("a1");
            AttributeVector::DocId docid;
            for (uint32_t i = 0; i < NUM_DOCS; ++i) {
                attr->addDoc(docid);
                attr->add(i, docid); // value = docid
            }
            assert(docid + 1 == NUM_DOCS);
            attributeContext.add(attr);
        }
        {
            SingleInt32ExtAttribute *attr = new SingleInt32ExtAttribute("a2");
            AttributeVector::DocId docid;
            for (uint32_t i = 0; i < NUM_DOCS; ++i) {
                attr->addDoc(docid);
                attr->add(i * 2, docid); // value = docid * 2
            }
            assert(docid + 1 == NUM_DOCS);
            attributeContext.add(attr);
        }

        // grouping
        sessionManager = SessionManager::SP(new SessionManager(100));

        // metaStore
        for (uint32_t i = 0; i < NUM_DOCS; ++i) {
            document::DocumentId docId(vespalib::make_string("doc::%u", i));
            const document::GlobalId &gid = docId.getGlobalId();
            typedef DocumentMetaStore::Result PutRes;
            document::BucketId bucketId(BucketFactory::getBucketId(docId));
            uint32_t docSize = 1;
            PutRes putRes(metaStore.put(gid,
                                        bucketId,
                                        Timestamp(0u),
                                        docSize,
                                        i));
            metaStore.setBucketState(bucketId, true);
        }
    }

    void set_property(const vespalib::string &name, const vespalib::string &value) {
        Properties cfg;
        cfg.add(name, value);
        config.import(cfg);
    }

    void setup_match_phase_limiting(const vespalib::string &attribute, size_t max_hits, bool descending)
    {
        inject_match_phase_limiting(config, attribute, max_hits, descending);
    }

    void add_match_phase_limiting_result(const vespalib::string &attribute, size_t want_docs,
                                         bool descending, std::initializer_list<uint32_t> docs)
    {
        vespalib::string term = vespalib::make_string("[;;%s%zu]", descending ? "-" : "", want_docs);
        FakeResult result;
        for (uint32_t doc: docs) {
            result.doc(doc);
        }
        searchContext.attr().addResult(attribute, term, result);
    }

    void setupSecondPhaseRanking() {
        Properties cfg;
        cfg.add(indexproperties::rank::SecondPhase::NAME, "attribute(a2)");
        cfg.add(indexproperties::hitcollector::HeapSize::NAME, "3");
        config.import(cfg);
    }

    void verbose_a1_result(const vespalib::string &term) {
        FakeResult result;
        for (uint32_t i = 15; i < NUM_DOCS; ++i) {
            result.doc(i);
        }
        searchContext.attr().addResult("a1", term, result);
    }

    void add_same_element_results(const vespalib::string &a1_term, const vespalib::string &f1_0_term) {
        auto a1_result   = make_elem_result({{10, {1}}, {20, {2}}, {21, {2}}});
        auto f1_0_result = make_elem_result({{10, {2}}, {20, {2}}, {21, {2}}});
        searchContext.attr().addResult("a1", a1_term, a1_result);
        searchContext.idx(0).getFake().addResult("f1", f1_0_term, f1_0_result);
    }

    void basicResults() {
        searchContext.idx(0).getFake().addResult("f1", "foo",
                                                 FakeResult()
                                                 .doc(10).doc(20).doc(30));
        searchContext.idx(0).getFake().addResult(
                "f1", "spread",
                FakeResult()
                .doc(100).doc(200).doc(300).doc(400).doc(500)
                .doc(600).doc(700).doc(800).doc(900));
    }

    void setStackDump(Request &request, const vespalib::string &stack_dump) {
        request.stackDump.assign(stack_dump.data(),
                                 stack_dump.data() + stack_dump.size());
    }

    SearchRequest::SP createRequest(const vespalib::string &stack_dump)
    {
        SearchRequest::SP request(new SearchRequest);
        request->setTimeout(60 * fastos::TimeStamp::SEC);
        setStackDump(*request, stack_dump);
        request->maxhits = 10;
        return request;
    }

    SearchRequest::SP createSimpleRequest(const vespalib::string &field,
                                          const vespalib::string &term)
    {
        return createRequest(make_simple_stack_dump(field, term));
    }

    SearchRequest::SP createSameElementRequest(const vespalib::string &a1_term,
                                               const vespalib::string &f1_term)
    {
        return createRequest(make_same_element_stack_dump(a1_term, f1_term));
    }

    Matcher::SP createMatcher() {
        return std::make_shared<Matcher>(schema, config, clock, queryLimiter, constantValueRepo, 0);
    }

    struct MySearchHandler : ISearchHandler {
        Matcher::SP _matcher;

        MySearchHandler(Matcher::SP matcher) : _matcher(matcher) {}

        virtual DocsumReply::UP getDocsums(const DocsumRequest &) override
        { return DocsumReply::UP(); }
        virtual SearchReply::UP match(const ISearchHandler::SP &,
                                      const SearchRequest &,
                                      vespalib::ThreadBundle &) const override
        { return SearchReply::UP(); }
    };

    double get_first_phase_termwise_limit() {
        Matcher::SP matcher = createMatcher();
        SearchRequest::SP request = createSimpleRequest("f1", "spread");
        search::fef::Properties overrides;
        MatchToolsFactory::UP match_tools_factory = matcher->create_match_tools_factory(
                *request, searchContext, attributeContext, metaStore, overrides);
        MatchTools::UP match_tools = match_tools_factory->createMatchTools();
        match_tools->setup_first_phase();
        return match_tools->match_data().get_termwise_limit();
    }

    SearchReply::UP performSearch(SearchRequest::SP req, size_t threads) {
        Matcher::SP matcher = createMatcher();
        SearchSession::OwnershipBundle owned_objects;
        owned_objects.search_handler.reset(new MySearchHandler(matcher));
        owned_objects.context.reset(new MatchContext(
                        IAttributeContext::UP(new MyAttributeContext),
                        matching::ISearchContext::UP(new FakeSearchContext)));
        vespalib::SimpleThreadBundle threadBundle(threads);
        SearchReply::UP reply =
            matcher->match(*req, threadBundle, searchContext, attributeContext,
                           *sessionManager, metaStore,
                           std::move(owned_objects));
        matchingStats.add(matcher->getStats());
        return reply;
    }

    DocsumRequest::SP createSimpleDocsumRequest(const vespalib::string & field,
                                                const vespalib::string & term)
    {
        DocsumRequest::SP request(new DocsumRequest);
        setStackDump(*request, make_simple_stack_dump(field, term));

        // match a subset of basic result + request for a non-hit (not
        // sorted on docid)
        request->hits.push_back(DocsumRequest::Hit());
        request->hits.back().docid = 30;
        request->hits.push_back(DocsumRequest::Hit());
        request->hits.back().docid = 10;
        request->hits.push_back(DocsumRequest::Hit());
        request->hits.back().docid = 15;
        return request;
    }

    std::unique_ptr<FieldInfo> get_field_info(const vespalib::string &field_name) {
        Matcher::SP matcher = createMatcher();
        const FieldInfo *field = matcher->get_index_env().getFieldByName(field_name);
        if (field == nullptr) {
            return std::unique_ptr<FieldInfo>(nullptr);
        }
        return std::make_unique<FieldInfo>(*field);
    }

    FeatureSet::SP getSummaryFeatures(DocsumRequest::SP req) {
        Matcher::SP matcher = createMatcher();
        return matcher->getSummaryFeatures(*req, searchContext,
                                          attributeContext, *sessionManager);
    }

    FeatureSet::SP getRankFeatures(DocsumRequest::SP req) {
        Matcher::SP matcher = createMatcher();
        return matcher->getRankFeatures(*req, searchContext, attributeContext,
                                       *sessionManager);
    }

};

MyWorld::MyWorld()
    : schema(),
      config(),
      searchContext(),
      attributeContext(),
      sessionManager(),
      metaStore(std::make_shared<BucketDBOwner>()),
      matchingStats(),
      clock(),
      queryLimiter()
{}
MyWorld::~MyWorld() {}
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------

void verifyViewResolver(const ViewResolver &resolver) {
    {
        std::vector<vespalib::string> fields;
        EXPECT_TRUE(resolver.resolve("foo", fields));
        ASSERT_TRUE(fields.size() == 2u);
        EXPECT_EQUAL("x", fields[0]);
        EXPECT_EQUAL("y", fields[1]);
    }
    {
        std::vector<vespalib::string> fields;
        EXPECT_TRUE(resolver.resolve("bar", fields));
        ASSERT_TRUE(fields.size() == 1u);
        EXPECT_EQUAL("z", fields[0]);
    }
    {
        std::vector<vespalib::string> fields;
        EXPECT_TRUE(!resolver.resolve("baz", fields));
        ASSERT_TRUE(fields.size() == 1u);
        EXPECT_EQUAL("baz", fields[0]);
    }
}

TEST("require that view resolver can be set up directly") {
    ViewResolver resolver;
    resolver.add("foo", "x").add("foo", "y").add("bar", "z");
    TEST_DO(verifyViewResolver(resolver));
}

TEST("require that view resolver can be set up from schema") {
    Schema schema;
    Schema::FieldSet foo("foo");
    foo.addField("x").addField("y");
    Schema::FieldSet bar("bar");
    bar.addField("z");
    schema.addFieldSet(foo);
    schema.addFieldSet(bar);
    ViewResolver resolver = ViewResolver::createFromSchema(schema);
    TEST_DO(verifyViewResolver(resolver));
}

//-----------------------------------------------------------------------------

TEST("require that matching is performed (multi-threaded)") {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world;
        world.basicSetup();
        world.basicResults();
        SearchRequest::SP request = world.createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(request, threads);
        EXPECT_EQUAL(9u, world.matchingStats.docsMatched());
        EXPECT_EQUAL(9u, reply->hits.size());
        EXPECT_GREATER(world.matchingStats.matchTimeAvg(), 0.0000001);
    }
}

TEST("require that matching also returns hits when only bitvector is used (multi-threaded)") {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world;
        world.basicSetup(0, 0);
        world.verbose_a1_result("all");
        SearchRequest::SP request = world.createSimpleRequest("a1", "all");
        SearchReply::UP reply = world.performSearch(request, threads);
        EXPECT_EQUAL(985u, world.matchingStats.docsMatched());
        EXPECT_EQUAL(10u, reply->hits.size());
        EXPECT_GREATER(world.matchingStats.matchTimeAvg(), 0.0000001);
    }
}

TEST("require that ranking is performed (multi-threaded)") {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world;
        world.basicSetup();
        world.basicResults();
        SearchRequest::SP request = world.createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(request, threads);
        EXPECT_EQUAL(9u, world.matchingStats.docsMatched());
        EXPECT_EQUAL(9u, world.matchingStats.docsRanked());
        EXPECT_EQUAL(0u, world.matchingStats.docsReRanked());
        ASSERT_TRUE(reply->hits.size() == 9u);
        EXPECT_EQUAL(document::DocumentId("doc::900").getGlobalId(),  reply->hits[0].gid);
        EXPECT_EQUAL(900.0, reply->hits[0].metric);
        EXPECT_EQUAL(document::DocumentId("doc::800").getGlobalId(),  reply->hits[1].gid);
        EXPECT_EQUAL(800.0, reply->hits[1].metric);
        EXPECT_EQUAL(document::DocumentId("doc::700").getGlobalId(),  reply->hits[2].gid);
        EXPECT_EQUAL(700.0, reply->hits[2].metric);
        EXPECT_GREATER(world.matchingStats.matchTimeAvg(), 0.0000001);
        EXPECT_EQUAL(0.0, world.matchingStats.rerankTimeAvg());
    }
}

TEST("require that re-ranking is performed (multi-threaded)") {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world;
        world.basicSetup();
        world.setupSecondPhaseRanking();
        world.basicResults();
        SearchRequest::SP request = world.createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(request, threads);
        EXPECT_EQUAL(9u, world.matchingStats.docsMatched());
        EXPECT_EQUAL(9u, world.matchingStats.docsRanked());
        EXPECT_EQUAL(3u, world.matchingStats.docsReRanked());
        ASSERT_TRUE(reply->hits.size() == 9u);
        EXPECT_EQUAL(document::DocumentId("doc::900").getGlobalId(),  reply->hits[0].gid);
        EXPECT_EQUAL(1800.0, reply->hits[0].metric);
        EXPECT_EQUAL(document::DocumentId("doc::800").getGlobalId(),  reply->hits[1].gid);
        EXPECT_EQUAL(1600.0, reply->hits[1].metric);
        EXPECT_EQUAL(document::DocumentId("doc::700").getGlobalId(),  reply->hits[2].gid);
        EXPECT_EQUAL(1400.0, reply->hits[2].metric);
        EXPECT_EQUAL(document::DocumentId("doc::600").getGlobalId(),  reply->hits[3].gid);
        EXPECT_EQUAL(600.0, reply->hits[3].metric);
        EXPECT_EQUAL(document::DocumentId("doc::500").getGlobalId(),  reply->hits[4].gid);
        EXPECT_EQUAL(500.0, reply->hits[4].metric);
        EXPECT_GREATER(world.matchingStats.matchTimeAvg(), 0.0000001);
        EXPECT_GREATER(world.matchingStats.rerankTimeAvg(), 0.0000001);
    }
}

TEST("require that sortspec can be used (multi-threaded)") {
    for (bool drop_sort_data: {false, true}) {
        for (size_t threads = 1; threads <= 16; ++threads) {
            MyWorld world;
            world.basicSetup();
            world.basicResults();
            SearchRequest::SP request = world.createSimpleRequest("f1", "spread");
            request->sortSpec = "+a1";
            if (drop_sort_data) {
                request->queryFlags |= fs4transport::QFLAG_DROP_SORTDATA;
            }
            SearchReply::UP reply = world.performSearch(request, threads);
            ASSERT_EQUAL(9u, reply->hits.size());
            EXPECT_EQUAL(document::DocumentId("doc::100").getGlobalId(),  reply->hits[0].gid);
            EXPECT_EQUAL(zero_rank_value, reply->hits[0].metric);
            EXPECT_EQUAL(document::DocumentId("doc::200").getGlobalId(),  reply->hits[1].gid);
            EXPECT_EQUAL(zero_rank_value, reply->hits[1].metric);
            EXPECT_EQUAL(document::DocumentId("doc::300").getGlobalId(),  reply->hits[2].gid);
            EXPECT_EQUAL(zero_rank_value, reply->hits[2].metric);
            EXPECT_EQUAL(drop_sort_data, reply->sortIndex.empty());
            EXPECT_EQUAL(drop_sort_data, reply->sortData.empty());
        }
    }
}

ExpressionNode::UP createAttr() { return std::make_unique<AttributeNode>("a1"); }
TEST("require that grouping is performed (multi-threaded)") {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world;
        world.basicSetup();
        world.basicResults();
        SearchRequest::SP request = world.createSimpleRequest("f1", "spread");
        {
            vespalib::nbostream buf;
            vespalib::NBOSerializer os(buf);
            uint32_t n = 1;
            os << n;
            Grouping grequest;
            grequest.setRoot(Group().addResult(SumAggregationResult().setExpression(createAttr())));
            grequest.serialize(os);
            request->groupSpec.assign(buf.c_str(), buf.c_str() + buf.size());
        }
        SearchReply::UP reply = world.performSearch(request, threads);
        {
            vespalib::nbostream buf(&reply->groupResult[0], reply->groupResult.size());
            vespalib::NBOSerializer is(buf);
            uint32_t n;
            is >> n;
            EXPECT_EQUAL(1u, n);
            Grouping gresult;
            gresult.deserialize(is);
            Grouping gexpect;
            gexpect.setRoot(Group().addResult(SumAggregationResult()
                                                      .setExpression(createAttr())
                                                      .setResult(Int64ResultNode(4500))));
            EXPECT_EQUAL(gexpect.root().asString(), gresult.root().asString());
        }
        EXPECT_GREATER(world.matchingStats.groupingTimeAvg(), 0.0000001);
    }
}

TEST("require that summary features are filled") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    DocsumRequest::SP req = world.createSimpleDocsumRequest("f1", "foo");
    FeatureSet::SP fs = world.getSummaryFeatures(req);
    const feature_t * f = NULL;
    EXPECT_EQUAL(2u, fs->numFeatures());
    EXPECT_EQUAL("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQUAL("value(100)", fs->getNames()[1]);
    EXPECT_EQUAL(2u, fs->numDocs());
    f = fs->getFeaturesByDocId(10);
    EXPECT_TRUE(f != NULL);
    EXPECT_EQUAL(10, f[0]);
    EXPECT_EQUAL(100, f[1]);
    f = fs->getFeaturesByDocId(15);
    EXPECT_TRUE(f == NULL);
    f = fs->getFeaturesByDocId(30);
    EXPECT_TRUE(f != NULL);
    EXPECT_EQUAL(30, f[0]);
    EXPECT_EQUAL(100, f[1]);
}

TEST("require that rank features are filled") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    DocsumRequest::SP req = world.createSimpleDocsumRequest("f1", "foo");
    FeatureSet::SP fs = world.getRankFeatures(req);
    const feature_t * f = NULL;
    EXPECT_EQUAL(1u, fs->numFeatures());
    EXPECT_EQUAL("attribute(a2)", fs->getNames()[0]);
    EXPECT_EQUAL(2u, fs->numDocs());
    f = fs->getFeaturesByDocId(10);
    EXPECT_TRUE(f != NULL);
    EXPECT_EQUAL(20, f[0]);
    f = fs->getFeaturesByDocId(15);
    EXPECT_TRUE(f == NULL);
    f = fs->getFeaturesByDocId(30);
    EXPECT_TRUE(f != NULL);
    EXPECT_EQUAL(60, f[0]);
}

TEST("require that search session can be cached") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    SearchRequest::SP request = world.createSimpleRequest("f1", "foo");
    request->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    request->sessionId.push_back('a');
    EXPECT_EQUAL(0u, world.sessionManager->getSearchStats().numInsert);
    SearchReply::UP reply = world.performSearch(request, 1);
    EXPECT_EQUAL(1u, world.sessionManager->getSearchStats().numInsert);
    SearchSession::SP session = world.sessionManager->pickSearch("a");
    ASSERT_TRUE(session.get());
    EXPECT_EQUAL(request->getTimeOfDoom(), session->getTimeOfDoom());
    EXPECT_EQUAL("a", session->getSessionId());
}

TEST("require that getSummaryFeatures can use cached query setup") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    SearchRequest::SP request = world.createSimpleRequest("f1", "foo");
    request->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    request->sessionId.push_back('a');
    world.performSearch(request, 1);

    DocsumRequest::SP docsum_request(new DocsumRequest);  // no stack dump
    docsum_request->sessionId = request->sessionId;
    docsum_request->
        propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    docsum_request->hits.push_back(DocsumRequest::Hit());
    docsum_request->hits.back().docid = 30;

    FeatureSet::SP fs = world.getSummaryFeatures(docsum_request);
    ASSERT_EQUAL(2u, fs->numFeatures());
    EXPECT_EQUAL("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQUAL("value(100)", fs->getNames()[1]);
    ASSERT_EQUAL(1u, fs->numDocs());
    const feature_t *f = fs->getFeaturesByDocId(30);
    ASSERT_TRUE(f);
    EXPECT_EQUAL(30, f[0]);
    EXPECT_EQUAL(100, f[1]);

    // getSummaryFeatures can be called multiple times.
    fs = world.getSummaryFeatures(docsum_request);
    ASSERT_EQUAL(2u, fs->numFeatures());
    EXPECT_EQUAL("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQUAL("value(100)", fs->getNames()[1]);
    ASSERT_EQUAL(1u, fs->numDocs());
    f = fs->getFeaturesByDocId(30);
    ASSERT_TRUE(f);
    EXPECT_EQUAL(30, f[0]);
    EXPECT_EQUAL(100, f[1]);
}

TEST("require that getSummaryFeatures prefers cached query setup") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    SearchRequest::SP request = world.createSimpleRequest("f1", "spread");
    request->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    request->sessionId.push_back('a');
    world.performSearch(request, 1);

    DocsumRequest::SP req = world.createSimpleDocsumRequest("f1", "foo");
    req->sessionId = request->sessionId;
    req->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    FeatureSet::SP fs = world.getSummaryFeatures(req);
    EXPECT_EQUAL(2u, fs->numFeatures());
    ASSERT_EQUAL(0u, fs->numDocs());  // "spread" has no hits

    // Empty cache
    auto pruneTime = fastos::ClockSystem::now() +
                     fastos::TimeStamp::MINUTE * 10;
    world.sessionManager->pruneTimedOutSessions(pruneTime);

    fs = world.getSummaryFeatures(req);
    EXPECT_EQUAL(2u, fs->numFeatures());
    ASSERT_EQUAL(2u, fs->numDocs());  // "foo" has two hits
}

TEST("require that match params are set up straight with ranking on") {
    MatchParams p(1, 2, 4, 0.7, 0, 1, true, true);
    ASSERT_EQUAL(1u, p.numDocs);
    ASSERT_EQUAL(2u, p.heapSize);
    ASSERT_EQUAL(4u, p.arraySize);
    ASSERT_EQUAL(0.7, p.rankDropLimit);
    ASSERT_EQUAL(0u, p.offset);
    ASSERT_EQUAL(1u, p.hits);
}

TEST("require that match params are set up straight with ranking on arraySize is atleast the size of heapSize") {
    MatchParams p(1, 6, 4, 0.7, 1, 1, true, true);
    ASSERT_EQUAL(1u, p.numDocs);
    ASSERT_EQUAL(6u, p.heapSize);
    ASSERT_EQUAL(6u, p.arraySize);
    ASSERT_EQUAL(0.7, p.rankDropLimit);
    ASSERT_EQUAL(1u, p.offset);
    ASSERT_EQUAL(1u, p.hits);
}

TEST("require that match params are set up straight with ranking on arraySize is atleast the size of hits+offset") {
    MatchParams p(1, 6, 4, 0.7, 4, 4, true, true);
    ASSERT_EQUAL(1u, p.numDocs);
    ASSERT_EQUAL(6u, p.heapSize);
    ASSERT_EQUAL(8u, p.arraySize);
    ASSERT_EQUAL(0.7, p.rankDropLimit);
    ASSERT_EQUAL(4u, p.offset);
    ASSERT_EQUAL(4u, p.hits);
}

TEST("require that match params are set up straight with ranking off array and heap size is 0") {
    MatchParams p(1, 6, 4, 0.7, 4, 4, true, false);
    ASSERT_EQUAL(1u, p.numDocs);
    ASSERT_EQUAL(0u, p.heapSize);
    ASSERT_EQUAL(0u, p.arraySize);
    ASSERT_EQUAL(0.7, p.rankDropLimit);
    ASSERT_EQUAL(4u, p.offset);
    ASSERT_EQUAL(4u, p.hits);
}

TEST("require that match phase limiting works") {
    for (int s = 0; s <= 1; ++s) {
        for (int i = 0; i <= 6; ++i) {
            bool enable = (i != 0);
            bool index_time = (i == 1) || (i == 2) || (i == 5) || (i == 6);
            bool query_time = (i == 3) || (i == 4) || (i == 5) || (i == 6);
            bool descending = (i == 2) || (i == 4) || (i == 6);
            bool use_sorting = (s == 1);
            size_t want_threads = 75;
            MyWorld world;
            world.basicSetup();
            world.verbose_a1_result("all");
            if (enable) {
                if (index_time) {
                    if (query_time) {
                        // inject bogus setup to be overridden by query
                        world.setup_match_phase_limiting("limiter", 10, true);
                    } else {
                        world.setup_match_phase_limiting("limiter", 150, descending);
                    }
                }
                world.add_match_phase_limiting_result("limiter", 152, descending, {948, 951, 963, 987, 991, 994, 997});
            }
            SearchRequest::SP request = world.createSimpleRequest("a1", "all");
            if (query_time) {
                inject_match_phase_limiting(request->propertiesMap.lookupCreate(search::MapNames::RANK), "limiter", 150, descending);
            }
            if (use_sorting) {
                request->sortSpec = "-a1";
            }
            SearchReply::UP reply = world.performSearch(request, want_threads);
            ASSERT_EQUAL(10u, reply->hits.size());
            if (enable) {
                EXPECT_EQUAL(79u, reply->totalHitCount);
                if (!use_sorting) {
                    EXPECT_EQUAL(997.0, reply->hits[0].metric);
                    EXPECT_EQUAL(994.0, reply->hits[1].metric);
                    EXPECT_EQUAL(991.0, reply->hits[2].metric);
                    EXPECT_EQUAL(987.0, reply->hits[3].metric);
                    EXPECT_EQUAL(974.0, reply->hits[4].metric);
                    EXPECT_EQUAL(963.0, reply->hits[5].metric);
                    EXPECT_EQUAL(961.0, reply->hits[6].metric);
                    EXPECT_EQUAL(951.0, reply->hits[7].metric);
                    EXPECT_EQUAL(948.0, reply->hits[8].metric);
                    EXPECT_EQUAL(935.0, reply->hits[9].metric);
                }
            } else {
                EXPECT_EQUAL(985u, reply->totalHitCount);
                if (!use_sorting) {
                    EXPECT_EQUAL(999.0, reply->hits[0].metric);
                    EXPECT_EQUAL(998.0, reply->hits[1].metric);
                    EXPECT_EQUAL(997.0, reply->hits[2].metric);
                    EXPECT_EQUAL(996.0, reply->hits[3].metric);
                }
            }
        }
    }
}

TEST("require that arithmetic used for rank drop limit works") {
    double small = -HUGE_VAL;
    double limit = -std::numeric_limits<feature_t>::quiet_NaN();
    EXPECT_TRUE(!(small <= limit));
}

TEST("require that termwise limit is set correctly for first phase ranking program") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    EXPECT_EQUAL(1.0, world.get_first_phase_termwise_limit());
    world.set_property(indexproperties::matching::TermwiseLimit::NAME, "0.02");
    EXPECT_EQUAL(0.02, world.get_first_phase_termwise_limit());
}

TEST("require that fields are tagged with data type") {
    MyWorld world;
    world.basicSetup();
    auto int32_field = world.get_field_info("a1");
    auto string_field = world.get_field_info("f1");
    auto tensor_field = world.get_field_info("tensor_field");
    auto predicate_field = world.get_field_info("predicate_field");
    ASSERT_TRUE(bool(int32_field));
    ASSERT_TRUE(bool(string_field));
    ASSERT_TRUE(bool(tensor_field));
    ASSERT_TRUE(bool(predicate_field));
    EXPECT_EQUAL(int32_field->get_data_type(), FieldInfo::DataType::INT32);
    EXPECT_EQUAL(string_field->get_data_type(), FieldInfo::DataType::STRING);
    EXPECT_EQUAL(tensor_field->get_data_type(), FieldInfo::DataType::TENSOR);
    EXPECT_EQUAL(predicate_field->get_data_type(), FieldInfo::DataType::BOOLEANTREE);
}

TEST("require that same element search works (note that this does not test/use the attribute element iterator wrapper)") {
    MyWorld world;
    world.basicSetup();
    world.add_same_element_results("foo", "bar");
    SearchRequest::SP request = world.createSameElementRequest("foo", "bar");
    SearchReply::UP reply = world.performSearch(request, 1);
    ASSERT_EQUAL(1u, reply->hits.size());
    EXPECT_EQUAL(document::DocumentId("doc::20").getGlobalId(), reply->hits[0].gid);
}

TEST_MAIN() { TEST_RUN_ALL(); }
