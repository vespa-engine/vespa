// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/matching/fakesearchcontext.h>
#include <vespa/searchcore/proton/matching/i_ranking_assets_repo.h>
#include <vespa/searchcore/proton/matching/match_context.h>
#include <vespa/searchcore/proton/matching/match_params.h>
#include <vespa/searchcore/proton/matching/match_tools.h>
#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchlib/aggregation/aggregation.h>
#include <vespa/searchlib/aggregation/grouping.h>
#include <vespa/searchlib/aggregation/perdocexpression.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/ranksetup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/test/mock_attribute_context.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/document/base/globalid.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>
#include <vespa/vespalib/util/testclock.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <initializer_list>

#include <vespa/log/log.h>
LOG_SETUP("matching_test");

using namespace proton::matching;
using namespace proton;
using namespace search::aggregation;
using namespace search::attribute;
using namespace search::engine;
using namespace search::expression;
using namespace search::fef::indexproperties::matching;
using namespace search::fef;
using namespace search::grouping;
using namespace search::index;
using namespace search::query;
using namespace search::queryeval;
using namespace search;

using search::attribute::test::MockAttributeContext;
using search::fef::indexproperties::hitcollector::HeapSize;
using search::index::schema::DataType;
using storage::spi::Timestamp;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::nbostream;

vespalib::ThreadBundle &ttb() { return vespalib::ThreadBundle::trivial(); }

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

vespalib::string make_simple_stack_dump(const vespalib::string &field, const vespalib::string &term)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addStringTerm(term, field, 1, search::query::Weight(1));
    return StackDumpCreator::create(*builder.build());
}

vespalib::string make_same_element_stack_dump(const vespalib::string &a1_term, const vespalib::string &f1_term)
{
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addSameElement(2, "my");
    builder.addStringTerm(a1_term, "a1", 1, search::query::Weight(1));
    builder.addStringTerm(f1_term, "f1", 2, search::query::Weight(1));
    return StackDumpCreator::create(*builder.build());
}

//-----------------------------------------------------------------------------

const uint32_t NUM_DOCS = 1000;

struct EmptyRankingAssetsRepo : public proton::matching::IRankingAssetsRepo {
    vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &) const override {
        return {};
    }

    vespalib::string getExpression(const vespalib::string &) const override {
        return {};
    }

    const OnnxModel *getOnnxModel(const vespalib::string &) const override {
        return nullptr;
    }
};

//-----------------------------------------------------------------------------

struct MyWorld {
    Schema                  schema;
    Properties              config;
    FakeSearchContext       searchContext;
    MockAttributeContext    attributeContext;
    SessionManager::SP      sessionManager;
    DocumentMetaStore       metaStore;
    MatchingStats           matchingStats;
    vespalib::TestClock     clock;
    QueryLimiter            queryLimiter;
    EmptyRankingAssetsRepo  constantValueRepo;

    MyWorld();
    ~MyWorld();

    void basicSetup(size_t heapSize=10, size_t arraySize=100) {
        // schema
        schema.addIndexField(Schema::IndexField("f1", DataType::STRING));
        schema.addIndexField(Schema::IndexField("f2", DataType::STRING));
        schema.addIndexField(Schema::IndexField("tensor_field", DataType::TENSOR));
        schema.addIndexField(Schema::IndexField("my.f1", DataType::STRING));
        schema.addAttributeField(Schema::AttributeField("a1", DataType::INT32));
        schema.addAttributeField(Schema::AttributeField("a2", DataType::INT32));
        schema.addAttributeField(Schema::AttributeField("a3", DataType::INT32));
        schema.addAttributeField(Schema::AttributeField("predicate_field", DataType::BOOLEANTREE));
        schema.addAttributeField(Schema::AttributeField("my.a1", DataType::STRING));

        // config
        config.add(indexproperties::rank::FirstPhase::NAME, "attribute(a1)");
        config.add(indexproperties::hitcollector::HeapSize::NAME, (vespalib::asciistream() << heapSize).str());
        config.add(indexproperties::hitcollector::ArraySize::NAME, (vespalib::asciistream() << arraySize).str());
        config.add(indexproperties::summary::Feature::NAME, "matches(f1)");
        config.add(indexproperties::summary::Feature::NAME, "rankingExpression(\"reduce(tensor(x[3])(x),sum)\")");
        config.add(indexproperties::summary::Feature::NAME, "rankingExpression(\"tensor(x[3])(x)\")");
        config.add(indexproperties::summary::Feature::NAME, "value(100)");
        config.add(indexproperties::summary::Feature::NAME, " attribute ( a1 ) "); // will be sorted and normalized

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
            AttributeVector::DocId docid(0);
            for (uint32_t i = 0; i < NUM_DOCS; ++i) {
                attr->addDoc(docid);
                attr->add(i, docid); // value = docid
            }
            assert(docid + 1 == NUM_DOCS);
            attributeContext.add(attr);
        }
        {
            auto *attr = new SingleInt32ExtAttribute("a2");
            AttributeVector::DocId docid(0);
            for (uint32_t i = 0; i < NUM_DOCS; ++i) {
                attr->addDoc(docid);
                attr->add(i * 2, docid); // value = docid * 2
            }
            assert(docid + 1 == NUM_DOCS);
            attributeContext.add(attr);
        }
        {
            auto *attr = new SingleInt32ExtAttribute("a3");
            AttributeVector::DocId docid(0);
            for (uint32_t i = 0; i < NUM_DOCS; ++i) {
                attr->addDoc(docid);
                attr->add(i%10, docid);
            }
            assert(docid + 1 == NUM_DOCS);
            attributeContext.add(attr);
        }

        // grouping
        sessionManager = std::make_shared<SessionManager>(100);

        // metaStore
        for (uint32_t i = 0; i < NUM_DOCS; ++i) {
            document::DocumentId docId(vespalib::make_string("id:ns:searchdocument::%u", i));
            const document::GlobalId &gid = docId.getGlobalId();
            document::BucketId bucketId(BucketFactory::getBucketId(docId));
            uint32_t docSize = 1;
            metaStore.put(gid, bucketId, Timestamp(0u), docSize, i, 0u);
            metaStore.setBucketState(bucketId, true);
        }
    }

    void set_property(const vespalib::string &name, const vespalib::string &value) {
        Properties cfg;
        cfg.add(name, value);
        config.import(cfg);
    }

    void setup_match_features() {
        config.add(indexproperties::match::Feature::NAME, "attribute(a1)");
        config.add(indexproperties::match::Feature::NAME, "attribute(a2)");
        config.add(indexproperties::match::Feature::NAME, "matches(a1)");
        config.add(indexproperties::match::Feature::NAME, "matches(f1)");
        config.add(indexproperties::match::Feature::NAME, "rankingExpression(\"tensor(x[3])(x)\")");
    }

    void setup_feature_renames() {
        config.add(indexproperties::feature_rename::Rename::NAME, "matches(f1)");
        config.add(indexproperties::feature_rename::Rename::NAME, "foobar");
        config.add(indexproperties::feature_rename::Rename::NAME, "rankingExpression(\"tensor(x[3])(x)\")");
        config.add(indexproperties::feature_rename::Rename::NAME, "tensor(x[3])(x)");
    }

    static void verify_match_features(SearchReply &reply, const vespalib::string &matched_field) {
        if (reply.hits.empty()) {
            EXPECT_EQUAL(reply.match_features.names.size(), 0u);
            EXPECT_EQUAL(reply.match_features.values.size(), 0u);
        } else {
            ASSERT_EQUAL(reply.match_features.names.size(), 5u);
            EXPECT_EQUAL(reply.match_features.names[0], "attribute(a1)");
            EXPECT_EQUAL(reply.match_features.names[1], "attribute(a2)");
            EXPECT_EQUAL(reply.match_features.names[2], "matches(a1)");
            EXPECT_EQUAL(reply.match_features.names[3], "matches(f1)");
            EXPECT_EQUAL(reply.match_features.names[4], "rankingExpression(\"tensor(x[3])(x)\")");
            ASSERT_EQUAL(reply.match_features.values.size(), 5 * reply.hits.size());
            for (size_t i = 0; i < reply.hits.size(); ++i) {
                const auto *f = &reply.match_features.values[i * 5];
                EXPECT_GREATER(f[0].as_double(), 0.0);
                EXPECT_GREATER(f[1].as_double(), 0.0);
                EXPECT_EQUAL(f[0].as_double(), reply.hits[i].metric);
                EXPECT_EQUAL(f[0].as_double() * 2, f[1].as_double());
                EXPECT_EQUAL(f[2].as_double(), double(matched_field == "a1"));
                EXPECT_EQUAL(f[3].as_double(), double(matched_field == "f1"));
                EXPECT_TRUE(f[4].is_data());
                {
                    nbostream buf(f[4].as_data().data, f[4].as_data().size);
                    auto actual = spec_from_value(*SimpleValue::from_stream(buf));
                    auto expect = TensorSpec("tensor(x[3])").add({{"x", 0}}, 0).add({{"x", 1}}, 1).add({{"x", 2}}, 2);
                    EXPECT_EQUAL(actual, expect);
                }
            }
        }
    }

    static void verify_match_feature_renames(SearchReply &reply, const vespalib::string &matched_field) {
        if (reply.hits.empty()) {
            EXPECT_EQUAL(reply.match_features.names.size(), 0u);
            EXPECT_EQUAL(reply.match_features.values.size(), 0u);
        } else {
            ASSERT_EQUAL(reply.match_features.names.size(), 5u);
            EXPECT_EQUAL(reply.match_features.names[3], "foobar");
            EXPECT_EQUAL(reply.match_features.names[4], "tensor(x[3])(x)");
            ASSERT_EQUAL(reply.match_features.values.size(), 5 * reply.hits.size());
            for (size_t i = 0; i < reply.hits.size(); ++i) {
                const auto *f = &reply.match_features.values[i * 5];
                EXPECT_EQUAL(f[3].as_double(), double(matched_field == "f1"));
                EXPECT_TRUE(f[4].is_data());
            }
        }
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

    void add_same_element_results(const vespalib::string &my_a1_term, const vespalib::string &my_f1_0_term) {
        auto my_a1_result   = make_elem_result({{10, {1}}, {20, {2, 3}}, {21, {2}}});
        auto my_f1_0_result = make_elem_result({{10, {2}}, {20, {1, 2}}, {21, {2}}});
        searchContext.attr().addResult("my.a1", my_a1_term, my_a1_result);
        searchContext.idx(0).getFake().addResult("my.f1", my_f1_0_term, my_f1_0_result);
    }

    void basicResults() {
        searchContext.idx(0).getFake().addResult("f1", "foo", FakeResult().doc(10).doc(20).doc(30));
        searchContext.idx(0).getFake().addResult("f1", "spread", FakeResult()
                                                                 .doc(100).doc(200).doc(300).doc(400).doc(500)
                                                                 .doc(600).doc(700).doc(800).doc(900));
    }

    static void setStackDump(Request &request, const vespalib::string &stack_dump) {
        request.stackDump.assign(stack_dump.data(), stack_dump.data() + stack_dump.size());
    }

    static SearchRequest::SP createRequest(const vespalib::string &stack_dump)
    {
        SearchRequest::SP request(new SearchRequest);
        request->setTimeout(60s);
        setStackDump(*request, stack_dump);
        request->maxhits = 10;
        return request;
    }

    static SearchRequest::SP createSimpleRequest(const vespalib::string &field, const vespalib::string &term)
    {
        return createRequest(make_simple_stack_dump(field, term));
    }

    static SearchRequest::SP createSameElementRequest(const vespalib::string &a1_term, const vespalib::string &f1_term)
    {
        return createRequest(make_same_element_stack_dump(a1_term, f1_term));
    }

    Matcher::SP createMatcher() {
        return std::make_shared<Matcher>(schema, config, clock.clock(), queryLimiter, constantValueRepo, 0);
    }

    struct MySearchHandler : ISearchHandler {
        Matcher::SP _matcher;

        explicit MySearchHandler(Matcher::SP matcher) noexcept : _matcher(std::move(matcher)) {}

        DocsumReply::UP getDocsums(const DocsumRequest &) override {
            return {};
        }
        SearchReply::UP match(const SearchRequest &, vespalib::ThreadBundle &) const override {
            return {};
        }
    };

    void verify_diversity_filter(const SearchRequest & req, bool expectDiverse) {
        Matcher::SP matcher = createMatcher();
        search::fef::Properties overrides;
        auto mtf = matcher->create_match_tools_factory(req, searchContext, attributeContext, metaStore, overrides, ttb(), true);
        auto diversity = mtf->createDiversifier(HeapSize::lookup(config));
        EXPECT_EQUAL(expectDiverse, static_cast<bool>(diversity));
    }

    double get_first_phase_termwise_limit() {
        Matcher::SP matcher = createMatcher();
        SearchRequest::SP request = createSimpleRequest("f1", "spread");
        search::fef::Properties overrides;
        MatchToolsFactory::UP match_tools_factory = matcher->create_match_tools_factory(
            *request, searchContext, attributeContext, metaStore, overrides, ttb(), true);
        MatchTools::UP match_tools = match_tools_factory->createMatchTools();
        match_tools->setup_first_phase(nullptr);
        return match_tools->match_data().get_termwise_limit();
    }

    SearchReply::UP performSearch(const SearchRequest & req, size_t threads) {
        Matcher::SP matcher = createMatcher();
        SearchSession::OwnershipBundle owned_objects;
        owned_objects.search_handler = std::make_shared<MySearchHandler>(matcher);
        owned_objects.context = std::make_unique<MatchContext>(std::make_unique<MockAttributeContext>(),
                                                               std::make_unique<FakeSearchContext>());
        vespalib::SimpleThreadBundle threadBundle(threads);
        SearchReply::UP reply = matcher->match(req, threadBundle, searchContext, attributeContext,
                                               *sessionManager, metaStore, metaStore.getBucketDB(),
                                               std::move(owned_objects));
        matchingStats.add(matcher->getStats());
        return reply;
    }

    static DocsumRequest::UP create_docsum_request(const vespalib::string &stack_dump, const std::initializer_list<uint32_t> docs) {
        auto req = std::make_unique<DocsumRequest>();
        setStackDump(*req, stack_dump);
        for (uint32_t docid: docs) {
            req->hits.emplace_back();
            req->hits.back().docid = docid;
        }
        return req;
    }

    static DocsumRequest::SP createSimpleDocsumRequest(const vespalib::string & field, const vespalib::string & term) {
        // match a subset of basic result + request for a non-hit (not
        // sorted on docid)
        return create_docsum_request(make_simple_stack_dump(field, term), {30, 10, 15});
    }

    std::unique_ptr<FieldInfo> get_field_info(const vespalib::string &field_name) {
        Matcher::SP matcher = createMatcher();
        const FieldInfo *field = matcher->get_index_env().getFieldByName(field_name);
        if (field == nullptr) {
            return {};
        }
        return std::make_unique<FieldInfo>(*field);
    }

    FeatureSet::SP getSummaryFeatures(const DocsumRequest & req) {
        Matcher::SP matcher = createMatcher();
        auto docsum_matcher = matcher->create_docsum_matcher(req, searchContext, attributeContext, *sessionManager);
        return docsum_matcher->get_summary_features();
    }

    FeatureSet::SP getRankFeatures(const DocsumRequest & req) {
        Matcher::SP matcher = createMatcher();
        auto docsum_matcher = matcher->create_docsum_matcher(req, searchContext, attributeContext, *sessionManager);
        return docsum_matcher->get_rank_features();
    }

    MatchingElements::UP get_matching_elements(const DocsumRequest &req, const MatchingElementsFields &fields) {
        Matcher::SP matcher = createMatcher();
        auto docsum_matcher = matcher->create_docsum_matcher(req, searchContext, attributeContext, *sessionManager);
        return docsum_matcher->get_matching_elements(fields);
    }
};

MyWorld::MyWorld()
    : schema(),
      config(),
      searchContext(),
      attributeContext(),
      sessionManager(),
      metaStore(std::make_shared<bucketdb::BucketDBOwner>()),
      matchingStats(),
      clock(),
      queryLimiter()
{}
MyWorld::~MyWorld() = default;
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
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(*request, threads);
        EXPECT_EQUAL(9u, world.matchingStats.docsMatched());
        EXPECT_EQUAL(9u, reply->hits.size());
        EXPECT_GREATER(world.matchingStats.matchTimeAvg(), 0.0000001);
    }
}

TEST("require that match features are calculated (multi-threaded)") {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world;
        world.basicSetup();
        world.basicResults();
        world.setup_match_features();
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(*request, threads);
        EXPECT_GREATER(reply->hits.size(), 0u);
        MyWorld::verify_match_features(*reply, "f1");
    }
}

TEST("require that match features can be renamed") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    world.setup_match_features();
    world.setup_feature_renames();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_GREATER(reply->hits.size(), 0u);
    MyWorld::verify_match_feature_renames(*reply, "f1");
}

TEST("require that no hits gives no match feature names") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    world.setup_match_features();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "not_found");
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_EQUAL(reply->hits.size(), 0u);
    MyWorld::verify_match_features(*reply, "f1");
}

TEST("require that matching also returns hits when only bitvector is used (multi-threaded)") {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world;
        world.basicSetup(0, 0);
        world.verbose_a1_result("all");
        SearchRequest::SP request = MyWorld::createSimpleRequest("a1", "all");
        SearchReply::UP reply = world.performSearch(*request, threads);
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
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(*request, threads);
        EXPECT_EQUAL(9u, world.matchingStats.docsMatched());
        EXPECT_EQUAL(9u, world.matchingStats.docsRanked());
        EXPECT_EQUAL(0u, world.matchingStats.docsReRanked());
        ASSERT_TRUE(reply->hits.size() == 9u);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::900").getGlobalId(),  reply->hits[0].gid);
        EXPECT_EQUAL(900.0, reply->hits[0].metric);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::800").getGlobalId(),  reply->hits[1].gid);
        EXPECT_EQUAL(800.0, reply->hits[1].metric);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::700").getGlobalId(),  reply->hits[2].gid);
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
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(*request, threads);
        EXPECT_EQUAL(9u, world.matchingStats.docsMatched());
        EXPECT_EQUAL(9u, world.matchingStats.docsRanked());
        EXPECT_EQUAL(3u, world.matchingStats.docsReRanked());
        ASSERT_TRUE(reply->hits.size() == 9u);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::900").getGlobalId(),  reply->hits[0].gid);
        EXPECT_EQUAL(1800.0, reply->hits[0].metric);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::800").getGlobalId(),  reply->hits[1].gid);
        EXPECT_EQUAL(1600.0, reply->hits[1].metric);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::700").getGlobalId(),  reply->hits[2].gid);
        EXPECT_EQUAL(1400.0, reply->hits[2].metric);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::600").getGlobalId(),  reply->hits[3].gid);
        EXPECT_EQUAL(600.0, reply->hits[3].metric);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::500").getGlobalId(),  reply->hits[4].gid);
        EXPECT_EQUAL(500.0, reply->hits[4].metric);
        EXPECT_GREATER(world.matchingStats.matchTimeAvg(), 0.0000001);
        EXPECT_GREATER(world.matchingStats.rerankTimeAvg(), 0.0000001);
    }
}

TEST("require that re-ranking is not diverse when not requested to be.") {
    MyWorld world;
    world.basicSetup();
    world.setupSecondPhaseRanking();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    world.verify_diversity_filter(*request, false);
}

using namespace search::fef::indexproperties::matchphase;

TEST("require that re-ranking is diverse when requested to be") {
    MyWorld world;
    world.basicSetup();
    world.setupSecondPhaseRanking();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    auto & rankProperies = request->propertiesMap.lookupCreate(MapNames::RANK);
    rankProperies.add(DiversityAttribute::NAME, "a2")
            .add(DiversityMinGroups::NAME, "3")
            .add(DiversityCutoffStrategy::NAME, "strict");
    world.verify_diversity_filter(*request, true);
}

TEST("require that re-ranking is diverse with diversity = 1/1") {
    MyWorld world;
    world.basicSetup();
    world.setupSecondPhaseRanking();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    auto & rankProperies = request->propertiesMap.lookupCreate(MapNames::RANK);
    rankProperies.add(DiversityAttribute::NAME, "a2")
                 .add(DiversityMinGroups::NAME, "3")
                 .add(DiversityCutoffStrategy::NAME, "strict");
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_EQUAL(9u, world.matchingStats.docsMatched());
    EXPECT_EQUAL(9u, world.matchingStats.docsRanked());
    EXPECT_EQUAL(3u, world.matchingStats.docsReRanked());
    ASSERT_TRUE(reply->hits.size() == 9u);
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::900").getGlobalId(),  reply->hits[0].gid);
    EXPECT_EQUAL(1800.0, reply->hits[0].metric);
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::800").getGlobalId(),  reply->hits[1].gid);
    EXPECT_EQUAL(1600.0, reply->hits[1].metric);
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::700").getGlobalId(),  reply->hits[2].gid);
    EXPECT_EQUAL(1400.0, reply->hits[2].metric);
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::600").getGlobalId(),  reply->hits[3].gid);
    EXPECT_EQUAL(600.0, reply->hits[3].metric);
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::500").getGlobalId(),  reply->hits[4].gid);
    EXPECT_EQUAL(500.0, reply->hits[4].metric);
}

TEST("require that re-ranking is diverse with diversity = 1/10") {
    MyWorld world;
    world.basicSetup();
    world.setupSecondPhaseRanking();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    auto & rankProperies = request->propertiesMap.lookupCreate(MapNames::RANK);
    rankProperies.add(DiversityAttribute::NAME, "a3")
                 .add(DiversityMinGroups::NAME, "3")
                 .add(DiversityCutoffStrategy::NAME, "strict");
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_EQUAL(9u, world.matchingStats.docsMatched());
    EXPECT_EQUAL(9u, world.matchingStats.docsRanked());
    EXPECT_EQUAL(1u, world.matchingStats.docsReRanked());
    ASSERT_TRUE(reply->hits.size() == 9u);
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::900").getGlobalId(),  reply->hits[0].gid);
    EXPECT_EQUAL(1800.0, reply->hits[0].metric);
    //TODO This is of course incorrect until the selectBest method sees everything.
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::800").getGlobalId(),  reply->hits[1].gid);
    EXPECT_EQUAL(800.0, reply->hits[1].metric);
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::700").getGlobalId(),  reply->hits[2].gid);
    EXPECT_EQUAL(700.0, reply->hits[2].metric);
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::600").getGlobalId(),  reply->hits[3].gid);
    EXPECT_EQUAL(600.0, reply->hits[3].metric);
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::500").getGlobalId(),  reply->hits[4].gid);
    EXPECT_EQUAL(500.0, reply->hits[4].metric);
}

TEST("require that sortspec can be used (multi-threaded)") {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world;
        world.basicSetup();
        world.basicResults();
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        request->sortSpec = "+a1";
        SearchReply::UP reply = world.performSearch(*request, threads);
        ASSERT_EQUAL(9u, reply->hits.size());
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::100").getGlobalId(),  reply->hits[0].gid);
        EXPECT_EQUAL(zero_rank_value, reply->hits[0].metric);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::200").getGlobalId(),  reply->hits[1].gid);
        EXPECT_EQUAL(zero_rank_value, reply->hits[1].metric);
        EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::300").getGlobalId(),  reply->hits[2].gid);
        EXPECT_EQUAL(zero_rank_value, reply->hits[2].metric);
        EXPECT_FALSE(reply->sortIndex.empty());
        EXPECT_FALSE(reply->sortData.empty());
    }
}

ExpressionNode::UP createAttr() { return std::make_unique<AttributeNode>("a1"); }
TEST("require that grouping is performed (multi-threaded)") {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world;
        world.basicSetup();
        world.basicResults();
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        {
            vespalib::nbostream buf;
            vespalib::NBOSerializer os(buf);
            uint32_t n = 1;
            os << n;
            Grouping grequest;
            grequest.setRoot(Group().addResult(SumAggregationResult().setExpression(createAttr())));
            grequest.serialize(os);
            request->groupSpec.assign(buf.data(), buf.data() + buf.size());
        }
        SearchReply::UP reply = world.performSearch(*request, threads);
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
    DocsumRequest::SP req = MyWorld::createSimpleDocsumRequest("f1", "foo");
    FeatureSet::SP fs = world.getSummaryFeatures(*req);
    const FeatureSet::Value * f = nullptr;
    EXPECT_EQUAL(5u, fs->numFeatures());
    EXPECT_EQUAL("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQUAL("matches(f1)", fs->getNames()[1]);
    EXPECT_EQUAL("rankingExpression(\"reduce(tensor(x[3])(x),sum)\")", fs->getNames()[2]);
    EXPECT_EQUAL("rankingExpression(\"tensor(x[3])(x)\")", fs->getNames()[3]);
    EXPECT_EQUAL("value(100)", fs->getNames()[4]);
    EXPECT_EQUAL(3u, fs->numDocs());
    f = fs->getFeaturesByDocId(10);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQUAL(10, f[0].as_double());
    EXPECT_EQUAL(1, f[1].as_double());
    EXPECT_EQUAL(100, f[4].as_double());
    f = fs->getFeaturesByDocId(15);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQUAL(15, f[0].as_double());
    EXPECT_EQUAL(0, f[1].as_double());
    EXPECT_EQUAL(100, f[4].as_double());
    f = fs->getFeaturesByDocId(30);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQUAL(30, f[0].as_double());
    EXPECT_EQUAL(1, f[1].as_double());
    EXPECT_TRUE(f[2].is_double());
    EXPECT_TRUE(!f[2].is_data());
    EXPECT_EQUAL(f[2].as_double(), 3.0); // 0 + 1 + 2
    EXPECT_TRUE(!f[3].is_double());
    EXPECT_TRUE(f[3].is_data());
    EXPECT_EQUAL(100, f[4].as_double());
    {
        nbostream buf(f[3].as_data().data, f[3].as_data().size);
        auto actual = spec_from_value(*SimpleValue::from_stream(buf));
        auto expect = TensorSpec("tensor(x[3])").add({{"x", 0}}, 0).add({{"x", 1}}, 1).add({{"x", 2}}, 2);
        EXPECT_EQUAL(actual, expect);
    }
}

TEST("require that rank features are filled") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    DocsumRequest::SP req = MyWorld::createSimpleDocsumRequest("f1", "foo");
    FeatureSet::SP fs = world.getRankFeatures(*req);
    const FeatureSet::Value * f = nullptr;
    EXPECT_EQUAL(1u, fs->numFeatures());
    EXPECT_EQUAL("attribute(a2)", fs->getNames()[0]);
    EXPECT_EQUAL(3u, fs->numDocs());
    f = fs->getFeaturesByDocId(10);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQUAL(20, f[0].as_double());
    f = fs->getFeaturesByDocId(15);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQUAL(30, f[0].as_double());
    f = fs->getFeaturesByDocId(30);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQUAL(60, f[0].as_double());
}

TEST("require that search session can be cached") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "foo");
    request->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    request->sessionId.push_back('a');
    EXPECT_EQUAL(0u, world.sessionManager->getSearchStats().numInsert);
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_EQUAL(1u, world.sessionManager->getSearchStats().numInsert);
    SearchSession::SP session = world.sessionManager->pickSearch("a");
    ASSERT_TRUE(session.get());
    EXPECT_EQUAL(request->getTimeOfDoom(), session->getTimeOfDoom());
    EXPECT_EQUAL("a", session->getSessionId());
}

TEST("require that summary features can be renamed") {
    MyWorld world;
    world.basicSetup();
    world.setup_feature_renames();
    world.basicResults();
    DocsumRequest::SP req = MyWorld::createSimpleDocsumRequest("f1", "foo");
    FeatureSet::SP fs = world.getSummaryFeatures(*req);
    const FeatureSet::Value * f = nullptr;
    EXPECT_EQUAL(5u, fs->numFeatures());
    EXPECT_EQUAL("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQUAL("foobar", fs->getNames()[1]);
    EXPECT_EQUAL("rankingExpression(\"reduce(tensor(x[3])(x),sum)\")", fs->getNames()[2]);
    EXPECT_EQUAL("tensor(x[3])(x)", fs->getNames()[3]);
    EXPECT_EQUAL(3u, fs->numDocs());
    f = fs->getFeaturesByDocId(30);
    EXPECT_TRUE(f != nullptr);
    EXPECT_TRUE(f[2].is_double());
    EXPECT_TRUE(f[3].is_data());
}

TEST("require that getSummaryFeatures can use cached query setup") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "foo");
    request->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    request->sessionId.push_back('a');
    world.performSearch(*request, 1);

    DocsumRequest::SP docsum_request(new DocsumRequest);  // no stack dump
    docsum_request->sessionId = request->sessionId;
    docsum_request->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    docsum_request->hits.emplace_back();
    docsum_request->hits.back().docid = 30;

    FeatureSet::SP fs = world.getSummaryFeatures(*docsum_request);
    ASSERT_EQUAL(5u, fs->numFeatures());
    EXPECT_EQUAL("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQUAL("matches(f1)", fs->getNames()[1]);
    EXPECT_EQUAL("rankingExpression(\"reduce(tensor(x[3])(x),sum)\")", fs->getNames()[2]);
    EXPECT_EQUAL("rankingExpression(\"tensor(x[3])(x)\")", fs->getNames()[3]);
    EXPECT_EQUAL("value(100)", fs->getNames()[4]);
    ASSERT_EQUAL(1u, fs->numDocs());
    const auto *f = fs->getFeaturesByDocId(30);
    ASSERT_TRUE(f);
    EXPECT_EQUAL(30, f[0].as_double());
    EXPECT_EQUAL(100, f[4].as_double());

    // getSummaryFeatures can be called multiple times.
    fs = world.getSummaryFeatures(*docsum_request);
    ASSERT_EQUAL(5u, fs->numFeatures());
    EXPECT_EQUAL("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQUAL("matches(f1)", fs->getNames()[1]);
    EXPECT_EQUAL("rankingExpression(\"reduce(tensor(x[3])(x),sum)\")", fs->getNames()[2]);
    EXPECT_EQUAL("rankingExpression(\"tensor(x[3])(x)\")", fs->getNames()[3]);
    EXPECT_EQUAL("value(100)", fs->getNames()[4]);
    ASSERT_EQUAL(1u, fs->numDocs());
    f = fs->getFeaturesByDocId(30);
    ASSERT_TRUE(f);
    EXPECT_EQUAL(30, f[0].as_double());
    EXPECT_EQUAL(100, f[4].as_double());
}

double count_f1_matches(FeatureSet &fs) {
    ASSERT_TRUE(fs.getNames().size() > 1);
    ASSERT_EQUAL(fs.getNames()[1], "matches(f1)");
    double sum = 0.0;
    for (size_t i = 0; i < fs.numDocs(); ++i) {
        auto *f = fs.getFeaturesByIndex(i);
        sum += f[1].as_double();
    }
    return sum;
}

TEST("require that getSummaryFeatures prefers cached query setup") {
    MyWorld world;
    world.basicSetup();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    request->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    request->sessionId.push_back('a');
    world.performSearch(*request, 1);

    DocsumRequest::SP req = MyWorld::createSimpleDocsumRequest("f1", "foo");
    req->sessionId = request->sessionId;
    req->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    FeatureSet::SP fs = world.getSummaryFeatures(*req);
    EXPECT_EQUAL(5u, fs->numFeatures());
    EXPECT_EQUAL(3u, fs->numDocs());
    EXPECT_EQUAL(0.0, count_f1_matches(*fs)); // "spread" has no hits

    // Empty cache
    auto pruneTime = vespalib::steady_clock::now() + 600s;
    world.sessionManager->pruneTimedOutSessions(pruneTime);

    fs = world.getSummaryFeatures(*req);
    EXPECT_EQUAL(5u, fs->numFeatures());
    EXPECT_EQUAL(3u, fs->numDocs());
    EXPECT_EQUAL(2.0, count_f1_matches(*fs)); // "foo" has two hits
}

TEST("require that match params are set up straight with ranking on") {
    MatchParams p(10, 2, 4, 0.7, 0, 1, true, true);
    ASSERT_EQUAL(10u, p.numDocs);
    ASSERT_EQUAL(2u, p.heapSize);
    ASSERT_EQUAL(4u, p.arraySize);
    ASSERT_EQUAL(0.7, p.rankDropLimit);
    ASSERT_EQUAL(0u, p.offset);
    ASSERT_EQUAL(1u, p.hits);
    ASSERT_TRUE(p.has_rank_drop_limit());
}

TEST("require that match params can turn off rank-drop-limit") {
    MatchParams p(10, 2, 4, -std::numeric_limits<feature_t>::quiet_NaN(), 0, 1, true, true);
    ASSERT_EQUAL(10u, p.numDocs);
    ASSERT_EQUAL(2u, p.heapSize);
    ASSERT_EQUAL(4u, p.arraySize);
    ASSERT_TRUE(std::isnan(p.rankDropLimit));
    ASSERT_EQUAL(0u, p.offset);
    ASSERT_EQUAL(1u, p.hits);
    ASSERT_FALSE(p.has_rank_drop_limit());
}


TEST("require that match params are set up straight with ranking on arraySize is atleast the size of heapSize") {
    MatchParams p(10, 6, 4, 0.7, 1, 1, true, true);
    ASSERT_EQUAL(10u, p.numDocs);
    ASSERT_EQUAL(6u, p.heapSize);
    ASSERT_EQUAL(6u, p.arraySize);
    ASSERT_EQUAL(0.7, p.rankDropLimit);
    ASSERT_EQUAL(1u, p.offset);
    ASSERT_EQUAL(1u, p.hits);
}

TEST("require that match params are set up straight with ranking on arraySize is atleast the size of hits+offset") {
    MatchParams p(10, 6, 4, 0.7, 4, 4, true, true);
    ASSERT_EQUAL(10u, p.numDocs);
    ASSERT_EQUAL(6u, p.heapSize);
    ASSERT_EQUAL(8u, p.arraySize);
    ASSERT_EQUAL(0.7, p.rankDropLimit);
    ASSERT_EQUAL(4u, p.offset);
    ASSERT_EQUAL(4u, p.hits);
}

TEST("require that match params are capped by numDocs") {
    MatchParams p(1, 6, 4, 0.7, 4, 4, true, true);
    ASSERT_EQUAL(1u, p.numDocs);
    ASSERT_EQUAL(1u, p.heapSize);
    ASSERT_EQUAL(1u, p.arraySize);
    ASSERT_EQUAL(0.7, p.rankDropLimit);
    ASSERT_EQUAL(1u, p.offset);
    ASSERT_EQUAL(0u, p.hits);
}

TEST("require that match params are capped by numDocs and hits adjusted down") {
    MatchParams p(5, 6, 4, 0.7, 4, 4, true, true);
    ASSERT_EQUAL(5u, p.numDocs);
    ASSERT_EQUAL(5u, p.heapSize);
    ASSERT_EQUAL(5u, p.arraySize);
    ASSERT_EQUAL(0.7, p.rankDropLimit);
    ASSERT_EQUAL(4u, p.offset);
    ASSERT_EQUAL(1u, p.hits);
}

TEST("require that match params are set up straight with ranking off array and heap size is 0") {
    MatchParams p(10, 6, 4, 0.7, 4, 4, true, false);
    ASSERT_EQUAL(10u, p.numDocs);
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
            SearchRequest::SP request = MyWorld::createSimpleRequest("a1", "all");
            if (query_time) {
                inject_match_phase_limiting(request->propertiesMap.lookupCreate(search::MapNames::RANK), "limiter", 150, descending);
            }
            if (use_sorting) {
                request->sortSpec = "-a1";
            }
            SearchReply::UP reply = world.performSearch(*request, want_threads);
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

TEST("require that same element search works") {
    MyWorld world;
    world.basicSetup();
    world.add_same_element_results("foo", "bar");
    SearchRequest::SP request = MyWorld::createSameElementRequest("foo", "bar");
    SearchReply::UP reply = world.performSearch(*request, 1);
    ASSERT_EQUAL(1u, reply->hits.size());
    EXPECT_EQUAL(document::DocumentId("id:ns:searchdocument::20").getGlobalId(), reply->hits[0].gid);
}

TEST("require that docsum matcher can extract matching elements from same element blueprint") {
    MyWorld world;
    world.basicSetup();
    world.add_same_element_results("foo", "bar");
    auto request = MyWorld::create_docsum_request(make_same_element_stack_dump("foo", "bar"), {20});
    MatchingElementsFields fields;
    fields.add_mapping("my", "my.a1");
    fields.add_mapping("my", "my.f1");
    auto result = world.get_matching_elements(*request, fields);
    const auto &list = result->get_matching_elements(20, "my");
    ASSERT_EQUAL(list.size(), 1u);
    EXPECT_EQUAL(list[0], 2u);
}

TEST("require that docsum matcher can extract matching elements from single attribute term") {
    MyWorld world;
    world.basicSetup();
    world.add_same_element_results("foo", "bar");
    auto request = MyWorld::create_docsum_request(make_simple_stack_dump("my.a1", "foo"), {20});
    MatchingElementsFields fields;
    fields.add_mapping("my", "my.a1");
    fields.add_mapping("my", "my.f1");
    auto result = world.get_matching_elements(*request, fields);
    const auto &list = result->get_matching_elements(20, "my");
    ASSERT_EQUAL(list.size(), 2u);
    EXPECT_EQUAL(list[0], 2u);
    EXPECT_EQUAL(list[1], 3u);
}

struct GlobalFilterParamsFixture {
   BlueprintFactory factory;
   search::fef::test::IndexEnvironment index_env;
   RankSetup rank_setup;
   Properties rank_properties;
    GlobalFilterParamsFixture(double lower_limit, double upper_limit)
       : factory(),
         index_env(),
         rank_setup(factory, index_env),
         rank_properties()
   {
       rank_setup.set_global_filter_lower_limit(lower_limit);
       rank_setup.set_global_filter_upper_limit(upper_limit);
   }
   void set_query_properties(vespalib::stringref lower_limit, vespalib::stringref upper_limit) {
       rank_properties.add(GlobalFilterLowerLimit::NAME, lower_limit);
       rank_properties.add(GlobalFilterUpperLimit::NAME, upper_limit);
   }
   AttributeBlueprintParams extract(uint32_t active_docids = 9, uint32_t docid_limit = 10) const {
       return MatchToolsFactory::extract_global_filter_params(rank_setup, rank_properties, active_docids, docid_limit);
   }
};

TEST_F("global filter params are extracted from rank profile", GlobalFilterParamsFixture(0.2, 0.8))
{
    auto params = f.extract();
    EXPECT_EQUAL(0.2, params.global_filter_lower_limit);
    EXPECT_EQUAL(0.8, params.global_filter_upper_limit);
}

TEST_F("global filter params are extracted from query", GlobalFilterParamsFixture(0.2, 0.8))
{
    f.set_query_properties("0.15", "0.75");
    auto params = f.extract();
    EXPECT_EQUAL(0.15, params.global_filter_lower_limit);
    EXPECT_EQUAL(0.75, params.global_filter_upper_limit);
}

TEST_F("global filter params are scaled with active hit ratio", GlobalFilterParamsFixture(0.2, 0.8))
{
    auto params = f.extract(5, 10);
    EXPECT_EQUAL(0.12, params.global_filter_lower_limit);
    EXPECT_EQUAL(0.48, params.global_filter_upper_limit);
}

TEST_MAIN() { TEST_RUN_ALL(); }
