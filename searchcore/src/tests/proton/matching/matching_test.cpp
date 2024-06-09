// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/matching/fakesearchcontext.h>
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
#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vespa/searchlib/fef/i_ranking_assets_repo.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/ranksetup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/test/mock_attribute_context.h>
#include <vespa/document/base/globalid.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/featureset.h>
#include <vespa/vespalib/util/limited_thread_bundle_wrapper.h>
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
using vespalib::FeatureSet;
using vespalib::nbostream;

constexpr uint32_t NUM_DOCS = 1000;

class MatchingTestSharedState {
    std::unique_ptr<vespalib::SimpleThreadBundle> _thread_bundle;
    std::unique_ptr<MockAttributeContext>         _attribute_context;
    std::unique_ptr<DocumentMetaStore>            _meta_store;
public:
    static constexpr size_t max_threads = 75;
    MatchingTestSharedState();
    ~MatchingTestSharedState();
    vespalib::ThreadBundle& thread_bundle();
    IAttributeContext& attribute_context();
    const proton::IDocumentMetaStore& meta_store();
};

MatchingTestSharedState::MatchingTestSharedState()
    : _thread_bundle(),
      _attribute_context(),
      _meta_store()
{
}

MatchingTestSharedState::~MatchingTestSharedState() = default;

vespalib::ThreadBundle&
MatchingTestSharedState::thread_bundle()
{
    if (!_thread_bundle) {
        _thread_bundle = std::make_unique<vespalib::SimpleThreadBundle>(max_threads);
    }
    return *_thread_bundle;
}

IAttributeContext&
MatchingTestSharedState::attribute_context()
{
    if (!_attribute_context) {
        _attribute_context = std::make_unique<MockAttributeContext>();
        // attribute context
        {
            auto attr = std::make_shared<SingleInt32ExtAttribute>("a1");
            AttributeVector::DocId docid(0);
            for (uint32_t i = 0; i < NUM_DOCS; ++i) {
                attr->addDoc(docid);
                attr->add(i, docid); // value = docid
            }
            assert(docid + 1 == NUM_DOCS);
            _attribute_context->add(attr);
        }
        {
            auto attr = std::make_shared<SingleInt32ExtAttribute>("a2");
            AttributeVector::DocId docid(0);
            for (uint32_t i = 0; i < NUM_DOCS; ++i) {
                attr->addDoc(docid);
                attr->add(i * 2, docid); // value = docid * 2
            }
            assert(docid + 1 == NUM_DOCS);
            _attribute_context->add(attr);
        }
        {
            auto attr = std::make_shared<SingleInt32ExtAttribute>("a3");
            AttributeVector::DocId docid(0);
            for (uint32_t i = 0; i < NUM_DOCS; ++i) {
                attr->addDoc(docid);
                attr->add(i%10, docid);
            }
            assert(docid + 1 == NUM_DOCS);
            _attribute_context->add(attr);
        }
    }
    return *_attribute_context;
}

const proton::IDocumentMetaStore&
MatchingTestSharedState::meta_store()
{
    if (!_meta_store) {
        _meta_store = std::make_unique<DocumentMetaStore>(std::make_shared<bucketdb::BucketDBOwner>());
        // metaStore
        for (uint32_t i = 0; i < NUM_DOCS; ++i) {
            document::DocumentId docId(vespalib::make_string("id:ns:searchdocument::%u", i));
            const document::GlobalId &gid = docId.getGlobalId();
            document::BucketId bucketId(BucketFactory::getBucketId(docId));
            uint32_t docSize = 1;
            _meta_store->put(gid, bucketId, Timestamp(0u), docSize, i, 0u);
            _meta_store->setBucketState(bucketId, true);
        }
    }
    return *_meta_store;
}

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
    builder.addSameElement(2, "my", 0, Weight(1));
    builder.addStringTerm(a1_term, "a1", 1, Weight(1));
    builder.addStringTerm(f1_term, "f1", 2, Weight(1));
    return StackDumpCreator::create(*builder.build());
}

//-----------------------------------------------------------------------------

struct EmptyRankingAssetsRepo : public search::fef::IRankingAssetsRepo {
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
    MatchingTestSharedState&         shared_state;
    Schema                           schema;
    Properties                       config;
    FakeSearchContext                searchContext;
    IAttributeContext&               attributeContext;
    std::shared_ptr<SessionManager>  sessionManager;
    const proton::IDocumentMetaStore& metaStore;
    MatchingStats                    matchingStats;
    vespalib::TestClock              clock;
    QueryLimiter                     queryLimiter;
    EmptyRankingAssetsRepo           constantValueRepo;

    MyWorld(MatchingTestSharedState& shared_state);
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

        // grouping
        sessionManager = std::make_shared<SessionManager>(100);

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
            EXPECT_EQ(reply.match_features.names.size(), 0u);
            EXPECT_EQ(reply.match_features.values.size(), 0u);
        } else {
            ASSERT_EQ(reply.match_features.names.size(), 5u);
            EXPECT_EQ(reply.match_features.names[0], "attribute(a1)");
            EXPECT_EQ(reply.match_features.names[1], "attribute(a2)");
            EXPECT_EQ(reply.match_features.names[2], "matches(a1)");
            EXPECT_EQ(reply.match_features.names[3], "matches(f1)");
            EXPECT_EQ(reply.match_features.names[4], "rankingExpression(\"tensor(x[3])(x)\")");
            ASSERT_EQ(reply.match_features.values.size(), 5 * reply.hits.size());
            for (size_t i = 0; i < reply.hits.size(); ++i) {
                const auto *f = &reply.match_features.values[i * 5];
                EXPECT_GT(f[0].as_double(), 0.0);
                EXPECT_GT(f[1].as_double(), 0.0);
                EXPECT_EQ(f[0].as_double(), reply.hits[i].metric);
                EXPECT_EQ(f[0].as_double() * 2, f[1].as_double());
                EXPECT_EQ(f[2].as_double(), double(matched_field == "a1"));
                EXPECT_EQ(f[3].as_double(), double(matched_field == "f1"));
                EXPECT_TRUE(f[4].is_data());
                {
                    nbostream buf(f[4].as_data().data, f[4].as_data().size);
                    auto actual = spec_from_value(*SimpleValue::from_stream(buf));
                    auto expect = TensorSpec("tensor(x[3])").add({{"x", 0}}, 0).add({{"x", 1}}, 1).add({{"x", 2}}, 2);
                    EXPECT_EQ(actual, expect);
                }
            }
        }
    }

    static void verify_match_feature_renames(SearchReply &reply, const vespalib::string &matched_field) {
        if (reply.hits.empty()) {
            EXPECT_EQ(reply.match_features.names.size(), 0u);
            EXPECT_EQ(reply.match_features.values.size(), 0u);
        } else {
            ASSERT_EQ(reply.match_features.names.size(), 5u);
            EXPECT_EQ(reply.match_features.names[3], "foobar");
            EXPECT_EQ(reply.match_features.names[4], "tensor(x[3])(x)");
            ASSERT_EQ(reply.match_features.values.size(), 5 * reply.hits.size());
            for (size_t i = 0; i < reply.hits.size(); ++i) {
                const auto *f = &reply.match_features.values[i * 5];
                EXPECT_EQ(f[3].as_double(), double(matched_field == "f1"));
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
        return std::make_shared<Matcher>(schema, config, clock.nowRef(), queryLimiter, constantValueRepo, 0);
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
        auto mtf = matcher->create_match_tools_factory(req, searchContext, attributeContext, metaStore, overrides,
                                                       ttb(), nullptr, searchContext.getDocIdLimit(), true);
        auto diversity = mtf->createDiversifier(HeapSize::lookup(config));
        EXPECT_EQ(expectDiverse, static_cast<bool>(diversity));
    }

    double get_first_phase_termwise_limit() {
        Matcher::SP matcher = createMatcher();
        SearchRequest::SP request = createSimpleRequest("f1", "spread");
        search::fef::Properties overrides;
        auto mtf = matcher->create_match_tools_factory(*request, searchContext, attributeContext, metaStore, overrides,
                                                       ttb(), nullptr, searchContext.getDocIdLimit(), true);
        MatchTools::UP match_tools = mtf->createMatchTools();
        match_tools->setup_first_phase(nullptr);
        return match_tools->match_data().get_termwise_limit();
    }

    SearchReply::UP performSearch(const SearchRequest & req, size_t threads) {
        Matcher::SP matcher = createMatcher();
        SearchSession::OwnershipBundle owned_objects({std::make_unique<MockAttributeContext>(),
                                                      std::make_unique<FakeSearchContext>()},
                                                     std::make_shared<MySearchHandler>(matcher));
        assert(threads <= MatchingTestSharedState::max_threads);
        vespalib::LimitedThreadBundleWrapper threadBundle(shared_state.thread_bundle(), threads);
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

MyWorld::MyWorld(MatchingTestSharedState& shared_state_in)
    : shared_state(shared_state_in),
      schema(),
      config(),
      searchContext(),
      attributeContext(shared_state.attribute_context()),
      sessionManager(),
      metaStore(shared_state.meta_store()),
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
        EXPECT_EQ("x", fields[0]);
        EXPECT_EQ("y", fields[1]);
    }
    {
        std::vector<vespalib::string> fields;
        EXPECT_TRUE(resolver.resolve("bar", fields));
        ASSERT_TRUE(fields.size() == 1u);
        EXPECT_EQ("z", fields[0]);
    }
    {
        std::vector<vespalib::string> fields;
        EXPECT_TRUE(!resolver.resolve("baz", fields));
        ASSERT_TRUE(fields.size() == 1u);
        EXPECT_EQ("baz", fields[0]);
    }
}

class MatchingTest : public ::testing::Test {
    static std::unique_ptr<MatchingTestSharedState> _shared_state;
protected:
    MatchingTest();
    ~MatchingTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
    static MatchingTestSharedState& shared_state();
};

MatchingTest::MatchingTest() = default;

MatchingTest::~MatchingTest() = default;

void
MatchingTest::SetUpTestSuite()
{
    _shared_state = std::make_unique<MatchingTestSharedState>();
}

void
MatchingTest::TearDownTestSuite()
{
    _shared_state.reset();
}

MatchingTestSharedState&
MatchingTest::shared_state()
{
    return *_shared_state;
}

std::unique_ptr<MatchingTestSharedState> MatchingTest::_shared_state;

TEST_F(MatchingTest, require_that_view_resolver_can_be_set_up_directly)
{
    ViewResolver resolver;
    resolver.add("foo", "x").add("foo", "y").add("bar", "z");
    verifyViewResolver(resolver);
}

TEST_F(MatchingTest, require_that_view_resolver_can_be_set_up_from_schema)
{
    Schema schema;
    Schema::FieldSet foo("foo");
    foo.addField("x").addField("y");
    Schema::FieldSet bar("bar");
    bar.addField("z");
    schema.addFieldSet(foo);
    schema.addFieldSet(bar);
    ViewResolver resolver = ViewResolver::createFromSchema(schema);
    verifyViewResolver(resolver);
}

//-----------------------------------------------------------------------------

TEST_F(MatchingTest, require_that_matching_is_performed_with_multi_threaded_matcher)
{
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world(shared_state());
        world.basicSetup();
        world.basicResults();
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(*request, threads);
        EXPECT_EQ(9u, world.matchingStats.docsMatched());
        EXPECT_EQ(9u, reply->hits.size());
        EXPECT_GT(world.matchingStats.matchTimeAvg(), 0.0000001);
    }
}

TEST_F(MatchingTest, require_that_match_features_are_calculated_with_multi_threaded_matcher)
{
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world(shared_state());
        world.basicSetup();
        world.basicResults();
        world.setup_match_features();
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(*request, threads);
        EXPECT_GT(reply->hits.size(), 0u);
        MyWorld::verify_match_features(*reply, "f1");
    }
}

TEST_F(MatchingTest, require_that_match_features_can_be_renamed)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.basicResults();
    world.setup_match_features();
    world.setup_feature_renames();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_GT(reply->hits.size(), 0u);
    MyWorld::verify_match_feature_renames(*reply, "f1");
}

TEST_F(MatchingTest, require_that_no_hits_gives_no_match_feature_names)
 {
    MyWorld world(shared_state());
    world.basicSetup();
    world.basicResults();
    world.setup_match_features();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "not_found");
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_EQ(reply->hits.size(), 0u);
    MyWorld::verify_match_features(*reply, "f1");
}

TEST_F(MatchingTest, require_that_matching_also_returns_hits_when_only_bitvector_is_used_with_multi_threaded_matcher)
 {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world(shared_state());
        world.basicSetup(0, 0);
        world.verbose_a1_result("all");
        SearchRequest::SP request = MyWorld::createSimpleRequest("a1", "all");
        SearchReply::UP reply = world.performSearch(*request, threads);
        EXPECT_EQ(985u, world.matchingStats.docsMatched());
        EXPECT_EQ(10u, reply->hits.size());
        EXPECT_GT(world.matchingStats.matchTimeAvg(), 0.0000001);
    }
}

TEST_F(MatchingTest, require_that_ranking_is_performed_with_multi_threaded_matcher)
 {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world(shared_state());
        world.basicSetup();
        world.basicResults();
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(*request, threads);
        EXPECT_EQ(9u, world.matchingStats.docsMatched());
        EXPECT_EQ(9u, world.matchingStats.docsRanked());
        EXPECT_EQ(0u, world.matchingStats.docsReRanked());
        ASSERT_TRUE(reply->hits.size() == 9u);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::900").getGlobalId(),  reply->hits[0].gid);
        EXPECT_EQ(900.0, reply->hits[0].metric);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::800").getGlobalId(),  reply->hits[1].gid);
        EXPECT_EQ(800.0, reply->hits[1].metric);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::700").getGlobalId(),  reply->hits[2].gid);
        EXPECT_EQ(700.0, reply->hits[2].metric);
        EXPECT_GT(world.matchingStats.matchTimeAvg(), 0.0000001);
        EXPECT_EQ(0.0, world.matchingStats.rerankTimeAvg());
    }
}

TEST_F(MatchingTest, require_that_reranking_is_performed_with_multi_threaded_matcher)
 {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world(shared_state());
        world.basicSetup();
        world.setupSecondPhaseRanking();
        world.basicResults();
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        SearchReply::UP reply = world.performSearch(*request, threads);
        EXPECT_EQ(9u, world.matchingStats.docsMatched());
        EXPECT_EQ(9u, world.matchingStats.docsRanked());
        EXPECT_EQ(3u, world.matchingStats.docsReRanked());
        ASSERT_TRUE(reply->hits.size() == 9u);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::900").getGlobalId(),  reply->hits[0].gid);
        EXPECT_EQ(1800.0, reply->hits[0].metric);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::800").getGlobalId(),  reply->hits[1].gid);
        EXPECT_EQ(1600.0, reply->hits[1].metric);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::700").getGlobalId(),  reply->hits[2].gid);
        EXPECT_EQ(1400.0, reply->hits[2].metric);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::600").getGlobalId(),  reply->hits[3].gid);
        EXPECT_EQ(600.0, reply->hits[3].metric);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::500").getGlobalId(),  reply->hits[4].gid);
        EXPECT_EQ(500.0, reply->hits[4].metric);
        EXPECT_GT(world.matchingStats.matchTimeAvg(), 0.0000001);
        EXPECT_GT(world.matchingStats.rerankTimeAvg(), 0.0000001);
    }
}

TEST_F(MatchingTest, require_that_reranking_is_not_diverse_when_not_requested_to_be)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.setupSecondPhaseRanking();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    world.verify_diversity_filter(*request, false);
}

using namespace search::fef::indexproperties::matchphase;

TEST_F(MatchingTest, require_that_reranking_is_diverse_when_requested_to_be)
{
    MyWorld world(shared_state());
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

TEST_F(MatchingTest, require_that_reranking_is_diverse_with_diversity_1_of_1)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.setupSecondPhaseRanking();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    auto & rankProperies = request->propertiesMap.lookupCreate(MapNames::RANK);
    rankProperies.add(DiversityAttribute::NAME, "a2")
                 .add(DiversityMinGroups::NAME, "3")
                 .add(DiversityCutoffStrategy::NAME, "strict");
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_EQ(9u, world.matchingStats.docsMatched());
    EXPECT_EQ(9u, world.matchingStats.docsRanked());
    EXPECT_EQ(3u, world.matchingStats.docsReRanked());
    ASSERT_TRUE(reply->hits.size() == 9u);
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::900").getGlobalId(),  reply->hits[0].gid);
    EXPECT_EQ(1800.0, reply->hits[0].metric);
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::800").getGlobalId(),  reply->hits[1].gid);
    EXPECT_EQ(1600.0, reply->hits[1].metric);
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::700").getGlobalId(),  reply->hits[2].gid);
    EXPECT_EQ(1400.0, reply->hits[2].metric);
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::600").getGlobalId(),  reply->hits[3].gid);
    EXPECT_EQ(600.0, reply->hits[3].metric);
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::500").getGlobalId(),  reply->hits[4].gid);
    EXPECT_EQ(500.0, reply->hits[4].metric);
}

TEST_F(MatchingTest, require_that_reranking_is_diverse_with_diversity_1_of_10)
 {
    MyWorld world(shared_state());
    world.basicSetup();
    world.setupSecondPhaseRanking();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
    auto & rankProperies = request->propertiesMap.lookupCreate(MapNames::RANK);
    rankProperies.add(DiversityAttribute::NAME, "a3")
                 .add(DiversityMinGroups::NAME, "3")
                 .add(DiversityCutoffStrategy::NAME, "strict");
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_EQ(9u, world.matchingStats.docsMatched());
    EXPECT_EQ(9u, world.matchingStats.docsRanked());
    EXPECT_EQ(1u, world.matchingStats.docsReRanked());
    ASSERT_TRUE(reply->hits.size() == 9u);
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::900").getGlobalId(),  reply->hits[0].gid);
    EXPECT_EQ(1800.0, reply->hits[0].metric);
    //TODO This is of course incorrect until the selectBest method sees everything.
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::800").getGlobalId(),  reply->hits[1].gid);
    EXPECT_EQ(800.0, reply->hits[1].metric);
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::700").getGlobalId(),  reply->hits[2].gid);
    EXPECT_EQ(700.0, reply->hits[2].metric);
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::600").getGlobalId(),  reply->hits[3].gid);
    EXPECT_EQ(600.0, reply->hits[3].metric);
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::500").getGlobalId(),  reply->hits[4].gid);
    EXPECT_EQ(500.0, reply->hits[4].metric);
}

TEST_F(MatchingTest, require_that_sortspec_can_be_used_with_multi_threaded_matcher)
{
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world(shared_state());
        world.basicSetup();
        world.basicResults();
        SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "spread");
        request->sortSpec = "+a1";
        SearchReply::UP reply = world.performSearch(*request, threads);
        ASSERT_EQ(9u, reply->hits.size());
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::100").getGlobalId(),  reply->hits[0].gid);
        EXPECT_EQ(zero_rank_value, reply->hits[0].metric);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::200").getGlobalId(),  reply->hits[1].gid);
        EXPECT_EQ(zero_rank_value, reply->hits[1].metric);
        EXPECT_EQ(document::DocumentId("id:ns:searchdocument::300").getGlobalId(),  reply->hits[2].gid);
        EXPECT_EQ(zero_rank_value, reply->hits[2].metric);
        EXPECT_FALSE(reply->sortIndex.empty());
        EXPECT_FALSE(reply->sortData.empty());
    }
}

ExpressionNode::UP createAttr() { return std::make_unique<AttributeNode>("a1"); }

TEST_F(MatchingTest, require_that_grouping_is_performed_with_multi_threaded_matcher)
 {
    for (size_t threads = 1; threads <= 16; ++threads) {
        MyWorld world(shared_state());
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
            EXPECT_EQ(1u, n);
            Grouping gresult;
            gresult.deserialize(is);
            Grouping gexpect;
            gexpect.setRoot(Group().addResult(SumAggregationResult()
                                                      .setExpression(createAttr())
                                                      .setResult(Int64ResultNode(4500))));
            EXPECT_EQ(gexpect.root().asString(), gresult.root().asString());
        }
        EXPECT_GT(world.matchingStats.groupingTimeAvg(), 0.0000001);
    }
}

TEST_F(MatchingTest, require_that_summary_features_are_filled)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.basicResults();
    DocsumRequest::SP req = MyWorld::createSimpleDocsumRequest("f1", "foo");
    FeatureSet::SP fs = world.getSummaryFeatures(*req);
    const FeatureSet::Value * f = nullptr;
    EXPECT_EQ(5u, fs->numFeatures());
    EXPECT_EQ("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQ("matches(f1)", fs->getNames()[1]);
    EXPECT_EQ("rankingExpression(\"reduce(tensor(x[3])(x),sum)\")", fs->getNames()[2]);
    EXPECT_EQ("rankingExpression(\"tensor(x[3])(x)\")", fs->getNames()[3]);
    EXPECT_EQ("value(100)", fs->getNames()[4]);
    EXPECT_EQ(3u, fs->numDocs());
    f = fs->getFeaturesByDocId(10);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQ(10, f[0].as_double());
    EXPECT_EQ(1, f[1].as_double());
    EXPECT_EQ(100, f[4].as_double());
    f = fs->getFeaturesByDocId(15);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQ(15, f[0].as_double());
    EXPECT_EQ(0, f[1].as_double());
    EXPECT_EQ(100, f[4].as_double());
    f = fs->getFeaturesByDocId(30);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQ(30, f[0].as_double());
    EXPECT_EQ(1, f[1].as_double());
    EXPECT_TRUE(f[2].is_double());
    EXPECT_TRUE(!f[2].is_data());
    EXPECT_EQ(f[2].as_double(), 3.0); // 0 + 1 + 2
    EXPECT_TRUE(!f[3].is_double());
    EXPECT_TRUE(f[3].is_data());
    EXPECT_EQ(100, f[4].as_double());
    {
        nbostream buf(f[3].as_data().data, f[3].as_data().size);
        auto actual = spec_from_value(*SimpleValue::from_stream(buf));
        auto expect = TensorSpec("tensor(x[3])").add({{"x", 0}}, 0).add({{"x", 1}}, 1).add({{"x", 2}}, 2);
        EXPECT_EQ(actual, expect);
    }
}

TEST_F(MatchingTest, require_that_rank_features_are_filled)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.basicResults();
    DocsumRequest::SP req = MyWorld::createSimpleDocsumRequest("f1", "foo");
    FeatureSet::SP fs = world.getRankFeatures(*req);
    const FeatureSet::Value * f = nullptr;
    EXPECT_EQ(1u, fs->numFeatures());
    EXPECT_EQ("attribute(a2)", fs->getNames()[0]);
    EXPECT_EQ(3u, fs->numDocs());
    f = fs->getFeaturesByDocId(10);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQ(20, f[0].as_double());
    f = fs->getFeaturesByDocId(15);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQ(30, f[0].as_double());
    f = fs->getFeaturesByDocId(30);
    EXPECT_TRUE(f != nullptr);
    EXPECT_EQ(60, f[0].as_double());
}

TEST_F(MatchingTest, require_that_search_session_can_be_cached)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.basicResults();
    SearchRequest::SP request = MyWorld::createSimpleRequest("f1", "foo");
    request->propertiesMap.lookupCreate(search::MapNames::CACHES).add("query", "true");
    request->sessionId.push_back('a');
    EXPECT_EQ(0u, world.sessionManager->getSearchStats().numInsert);
    SearchReply::UP reply = world.performSearch(*request, 1);
    EXPECT_EQ(1u, world.sessionManager->getSearchStats().numInsert);
    SearchSession::SP session = world.sessionManager->pickSearch("a");
    ASSERT_TRUE(session.get());
    EXPECT_EQ(request->getTimeOfDoom(), session->getTimeOfDoom());
    EXPECT_EQ("a", session->getSessionId());
}

TEST_F(MatchingTest, require_that_summary_features_can_be_renamed)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.setup_feature_renames();
    world.basicResults();
    DocsumRequest::SP req = MyWorld::createSimpleDocsumRequest("f1", "foo");
    FeatureSet::SP fs = world.getSummaryFeatures(*req);
    const FeatureSet::Value * f = nullptr;
    EXPECT_EQ(5u, fs->numFeatures());
    EXPECT_EQ("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQ("foobar", fs->getNames()[1]);
    EXPECT_EQ("rankingExpression(\"reduce(tensor(x[3])(x),sum)\")", fs->getNames()[2]);
    EXPECT_EQ("tensor(x[3])(x)", fs->getNames()[3]);
    EXPECT_EQ(3u, fs->numDocs());
    f = fs->getFeaturesByDocId(30);
    EXPECT_TRUE(f != nullptr);
    EXPECT_TRUE(f[2].is_double());
    EXPECT_TRUE(f[3].is_data());
}

TEST_F(MatchingTest, require_that_getSummaryFeatures_can_use_cached_query_setup)
{
    MyWorld world(shared_state());
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
    ASSERT_EQ(5u, fs->numFeatures());
    EXPECT_EQ("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQ("matches(f1)", fs->getNames()[1]);
    EXPECT_EQ("rankingExpression(\"reduce(tensor(x[3])(x),sum)\")", fs->getNames()[2]);
    EXPECT_EQ("rankingExpression(\"tensor(x[3])(x)\")", fs->getNames()[3]);
    EXPECT_EQ("value(100)", fs->getNames()[4]);
    ASSERT_EQ(1u, fs->numDocs());
    const auto *f = fs->getFeaturesByDocId(30);
    ASSERT_TRUE(f);
    EXPECT_EQ(30, f[0].as_double());
    EXPECT_EQ(100, f[4].as_double());

    // getSummaryFeatures can be called multiple times.
    fs = world.getSummaryFeatures(*docsum_request);
    ASSERT_EQ(5u, fs->numFeatures());
    EXPECT_EQ("attribute(a1)", fs->getNames()[0]);
    EXPECT_EQ("matches(f1)", fs->getNames()[1]);
    EXPECT_EQ("rankingExpression(\"reduce(tensor(x[3])(x),sum)\")", fs->getNames()[2]);
    EXPECT_EQ("rankingExpression(\"tensor(x[3])(x)\")", fs->getNames()[3]);
    EXPECT_EQ("value(100)", fs->getNames()[4]);
    ASSERT_EQ(1u, fs->numDocs());
    f = fs->getFeaturesByDocId(30);
    ASSERT_TRUE(f);
    EXPECT_EQ(30, f[0].as_double());
    EXPECT_EQ(100, f[4].as_double());
}

void count_f1_matches(FeatureSet &fs, double& sum) {
    ASSERT_TRUE(fs.getNames().size() > 1);
    ASSERT_EQ(fs.getNames()[1], "matches(f1)");
    sum = 0.0;
    for (size_t i = 0; i < fs.numDocs(); ++i) {
        auto *f = fs.getFeaturesByIndex(i);
        sum += f[1].as_double();
    }
}

TEST_F(MatchingTest, require_that_getSummaryFeatures_prefers_cached_query_setup)
{
    MyWorld world(shared_state());
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
    EXPECT_EQ(5u, fs->numFeatures());
    EXPECT_EQ(3u, fs->numDocs());
    double sum = 0.0;
    ASSERT_NO_FATAL_FAILURE(count_f1_matches(*fs, sum));
    EXPECT_EQ(0.0, sum); // "spread" has no hits

    // Empty cache
    auto pruneTime = vespalib::steady_clock::now() + 600s;
    world.sessionManager->pruneTimedOutSessions(pruneTime);

    fs = world.getSummaryFeatures(*req);
    EXPECT_EQ(5u, fs->numFeatures());
    EXPECT_EQ(3u, fs->numDocs());
    ASSERT_NO_FATAL_FAILURE(count_f1_matches(*fs, sum));
    EXPECT_EQ(2.0, sum); // "foo" has two hits
}

TEST_F(MatchingTest, require_that_match_params_are_set_up_straight_with_ranking_on)
{
    MatchParams p(10, 2, 4, 0.7, 0.75, 0, 1, true, true);
    ASSERT_EQ(10u, p.numDocs);
    ASSERT_EQ(2u, p.heapSize);
    ASSERT_EQ(4u, p.arraySize);
    ASSERT_EQ(0.7, p.first_phase_rank_score_drop_limit.value());
    ASSERT_EQ(0.75, p.second_phase_rank_score_drop_limit.value());
    ASSERT_EQ(0u, p.offset);
    ASSERT_EQ(1u, p.hits);
}

TEST_F(MatchingTest, require_that_match_params_can_turn_off_rank_score_drop_limits)
{
    MatchParams p(10, 2, 4, std::nullopt, std::nullopt, 0, 1, true, true);
    ASSERT_EQ(10u, p.numDocs);
    ASSERT_EQ(2u, p.heapSize);
    ASSERT_EQ(4u, p.arraySize);
    ASSERT_FALSE(p.first_phase_rank_score_drop_limit.has_value());
    ASSERT_FALSE(p.second_phase_rank_score_drop_limit.has_value());
    ASSERT_EQ(0u, p.offset);
    ASSERT_EQ(1u, p.hits);
}


TEST_F(MatchingTest, require_that_match_params_are_set_up_straight_with_ranking_on_arraySize_is_atleast_the_size_of_heapSize)
{
    MatchParams p(10, 6, 4, 0.7, std::nullopt, 1, 1, true, true);
    ASSERT_EQ(10u, p.numDocs);
    ASSERT_EQ(6u, p.heapSize);
    ASSERT_EQ(6u, p.arraySize);
    ASSERT_EQ(0.7, p.first_phase_rank_score_drop_limit.value());
    ASSERT_FALSE(p.second_phase_rank_score_drop_limit.has_value());
    ASSERT_EQ(1u, p.offset);
    ASSERT_EQ(1u, p.hits);
}

TEST_F(MatchingTest, require_that_match_params_are_set_up_straight_with_ranking_on_arraySize_is_atleast_the_size_of_hits_plus_offset)
{
    MatchParams p(10, 6, 4, 0.7, std::nullopt, 4, 4, true, true);
    ASSERT_EQ(10u, p.numDocs);
    ASSERT_EQ(6u, p.heapSize);
    ASSERT_EQ(8u, p.arraySize);
    ASSERT_EQ(0.7, p.first_phase_rank_score_drop_limit.value());
    ASSERT_EQ(4u, p.offset);
    ASSERT_EQ(4u, p.hits);
}

TEST_F(MatchingTest, require_that_match_params_are_capped_by_numDocs)
{
    MatchParams p(1, 6, 4, 0.7, std::nullopt, 4, 4, true, true);
    ASSERT_EQ(1u, p.numDocs);
    ASSERT_EQ(1u, p.heapSize);
    ASSERT_EQ(1u, p.arraySize);
    ASSERT_EQ(0.7, p.first_phase_rank_score_drop_limit.value());
    ASSERT_EQ(1u, p.offset);
    ASSERT_EQ(0u, p.hits);
}

TEST_F(MatchingTest, require_that_match_params_are_capped_by_numDocs_and_hits_adjusted_down)
{
    MatchParams p(5, 6, 4, 0.7, std::nullopt, 4, 4, true, true);
    ASSERT_EQ(5u, p.numDocs);
    ASSERT_EQ(5u, p.heapSize);
    ASSERT_EQ(5u, p.arraySize);
    ASSERT_EQ(0.7, p.first_phase_rank_score_drop_limit.value());
    ASSERT_EQ(4u, p.offset);
    ASSERT_EQ(1u, p.hits);
}

TEST_F(MatchingTest, require_that_match_params_are_set_up_straight_with_ranking_off_array_and_heap_size_is_0)
{
    MatchParams p(10, 6, 4, 0.7, std::nullopt, 4, 4, true, false);
    ASSERT_EQ(10u, p.numDocs);
    ASSERT_EQ(0u, p.heapSize);
    ASSERT_EQ(0u, p.arraySize);
    ASSERT_EQ(0.7, p.first_phase_rank_score_drop_limit.value());
    ASSERT_EQ(4u, p.offset);
    ASSERT_EQ(4u, p.hits);
}

TEST_F(MatchingTest, require_that_match_phase_limiting_works)
{
    for (int s = 0; s <= 1; ++s) {
        for (int i = 0; i <= 6; ++i) {
            bool enable = (i != 0);
            bool index_time = (i == 1) || (i == 2) || (i == 5) || (i == 6);
            bool query_time = (i == 3) || (i == 4) || (i == 5) || (i == 6);
            bool descending = (i == 2) || (i == 4) || (i == 6);
            bool use_sorting = (s == 1);
            size_t want_threads = 75;
            MyWorld world(shared_state());
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
            ASSERT_EQ(10u, reply->hits.size());
            if (enable) {
                EXPECT_EQ(79u, reply->totalHitCount);
                if (!use_sorting) {
                    EXPECT_EQ(997.0, reply->hits[0].metric);
                    EXPECT_EQ(994.0, reply->hits[1].metric);
                    EXPECT_EQ(991.0, reply->hits[2].metric);
                    EXPECT_EQ(987.0, reply->hits[3].metric);
                    EXPECT_EQ(974.0, reply->hits[4].metric);
                    EXPECT_EQ(963.0, reply->hits[5].metric);
                    EXPECT_EQ(961.0, reply->hits[6].metric);
                    EXPECT_EQ(951.0, reply->hits[7].metric);
                    EXPECT_EQ(948.0, reply->hits[8].metric);
                    EXPECT_EQ(935.0, reply->hits[9].metric);
                }
            } else {
                EXPECT_EQ(985u, reply->totalHitCount);
                if (!use_sorting) {
                    EXPECT_EQ(999.0, reply->hits[0].metric);
                    EXPECT_EQ(998.0, reply->hits[1].metric);
                    EXPECT_EQ(997.0, reply->hits[2].metric);
                    EXPECT_EQ(996.0, reply->hits[3].metric);
                }
            }
        }
    }
}

TEST_F(MatchingTest, require_that_arithmetic_used_for_rank_drop_limit_works)
{
    double small = -HUGE_VAL;
    double limit = -std::numeric_limits<feature_t>::quiet_NaN();
    EXPECT_TRUE(!(small <= limit));
}

TEST_F(MatchingTest, require_that_termwise_limit_is_set_correctly_for_first_phase_ranking_program)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.basicResults();
    EXPECT_EQ(1.0, world.get_first_phase_termwise_limit());
    world.set_property(indexproperties::matching::TermwiseLimit::NAME, "0.02");
    EXPECT_EQ(0.02, world.get_first_phase_termwise_limit());
}

TEST_F(MatchingTest, require_that_fields_are_tagged_with_data_type)
{
    MyWorld world(shared_state());
    world.basicSetup();
    auto int32_field = world.get_field_info("a1");
    auto string_field = world.get_field_info("f1");
    auto tensor_field = world.get_field_info("tensor_field");
    auto predicate_field = world.get_field_info("predicate_field");
    ASSERT_TRUE(bool(int32_field));
    ASSERT_TRUE(bool(string_field));
    ASSERT_TRUE(bool(tensor_field));
    ASSERT_TRUE(bool(predicate_field));
    EXPECT_EQ(int32_field->get_data_type(), FieldInfo::DataType::INT32);
    EXPECT_EQ(string_field->get_data_type(), FieldInfo::DataType::STRING);
    EXPECT_EQ(tensor_field->get_data_type(), FieldInfo::DataType::TENSOR);
    EXPECT_EQ(predicate_field->get_data_type(), FieldInfo::DataType::BOOLEANTREE);
}

TEST_F(MatchingTest, require_that_same_element_search_works)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.add_same_element_results("foo", "bar");
    SearchRequest::SP request = MyWorld::createSameElementRequest("foo", "bar");
    SearchReply::UP reply = world.performSearch(*request, 1);
    ASSERT_EQ(1u, reply->hits.size());
    EXPECT_EQ(document::DocumentId("id:ns:searchdocument::20").getGlobalId(), reply->hits[0].gid);
}

TEST_F(MatchingTest, require_that_docsum_matcher_can_extract_matching_elements_from_same_element_blueprint)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.add_same_element_results("foo", "bar");
    auto request = MyWorld::create_docsum_request(make_same_element_stack_dump("foo", "bar"), {20});
    MatchingElementsFields fields;
    fields.add_mapping("my", "my.a1");
    fields.add_mapping("my", "my.f1");
    auto result = world.get_matching_elements(*request, fields);
    const auto &list = result->get_matching_elements(20, "my");
    ASSERT_EQ(list.size(), 1u);
    EXPECT_EQ(list[0], 2u);
}

TEST_F(MatchingTest, require_that_docsum_matcher_can_extract_matching_elements_from_single_attribute_term)
{
    MyWorld world(shared_state());
    world.basicSetup();
    world.add_same_element_results("foo", "bar");
    auto request = MyWorld::create_docsum_request(make_simple_stack_dump("my.a1", "foo"), {20});
    MatchingElementsFields fields;
    fields.add_mapping("my", "my.a1");
    fields.add_mapping("my", "my.f1");
    auto result = world.get_matching_elements(*request, fields);
    const auto &list = result->get_matching_elements(20, "my");
    ASSERT_EQ(list.size(), 2u);
    EXPECT_EQ(list[0], 2u);
    EXPECT_EQ(list[1], 3u);
}

using FMA = vespalib::FuzzyMatchingAlgorithm;

struct AttributeBlueprintParamsFixture {
   BlueprintFactory factory;
   search::fef::test::IndexEnvironment index_env;
   RankSetup rank_setup;
   Properties rank_properties;
   AttributeBlueprintParamsFixture(double lower_limit, double upper_limit, double target_hits_max_adjustment_factor,
                                   FMA fuzzy_matching_algorithm)
       : factory(),
         index_env(),
         rank_setup(factory, index_env),
         rank_properties()
   {
       rank_setup.set_global_filter_lower_limit(lower_limit);
       rank_setup.set_global_filter_upper_limit(upper_limit);
       rank_setup.set_target_hits_max_adjustment_factor(target_hits_max_adjustment_factor);
       rank_setup.set_fuzzy_matching_algorithm(fuzzy_matching_algorithm);
   }
   void set_query_properties(vespalib::stringref lower_limit, vespalib::stringref upper_limit,
                             vespalib::stringref target_hits_max_adjustment_factor,
                             const vespalib::string & fuzzy_matching_algorithm) {
       rank_properties.add(GlobalFilterLowerLimit::NAME, lower_limit);
       rank_properties.add(GlobalFilterUpperLimit::NAME, upper_limit);
       rank_properties.add(TargetHitsMaxAdjustmentFactor::NAME, target_hits_max_adjustment_factor);
       rank_properties.add(FuzzyAlgorithm::NAME, fuzzy_matching_algorithm);
   }
   ~AttributeBlueprintParamsFixture();
   AttributeBlueprintParams extract(uint32_t active_docids = 9, uint32_t docid_limit = 10) const {
       return MatchToolsFactory::extract_attribute_blueprint_params(rank_setup, rank_properties, active_docids, docid_limit);
   }
};

AttributeBlueprintParamsFixture::~AttributeBlueprintParamsFixture() = default;

TEST_F(MatchingTest, attribute_blueprint_params_are_extracted_from_rank_profile)
{
    AttributeBlueprintParamsFixture f(0.2, 0.8, 5.0, FMA::DfaTable);
    auto params = f.extract();
    EXPECT_EQ(0.2, params.global_filter_lower_limit);
    EXPECT_EQ(0.8, params.global_filter_upper_limit);
    EXPECT_EQ(5.0, params.target_hits_max_adjustment_factor);
    EXPECT_EQ(FMA::DfaTable, params.fuzzy_matching_algorithm);
}

TEST_F(MatchingTest, attribute_blueprint_params_are_extracted_from_query)
{
    AttributeBlueprintParamsFixture f(0.2, 0.8, 5.0, FMA::DfaTable);
    f.set_query_properties("0.15", "0.75", "3.0", "dfa_explicit");
    auto params = f.extract();
    EXPECT_EQ(0.15, params.global_filter_lower_limit);
    EXPECT_EQ(0.75, params.global_filter_upper_limit);
    EXPECT_EQ(3.0, params.target_hits_max_adjustment_factor);
    EXPECT_EQ(FMA::DfaExplicit, params.fuzzy_matching_algorithm);
}

TEST_F(MatchingTest, global_filter_params_are_scaled_with_active_hit_ratio)
{
    AttributeBlueprintParamsFixture f(0.2, 0.8, 5.0, FMA::DfaTable);
    auto params = f.extract(5, 10);
    EXPECT_EQ(0.12, params.global_filter_lower_limit);
    EXPECT_EQ(0.48, params.global_filter_upper_limit);
}

GTEST_MAIN_RUN_ALL_TESTS()
