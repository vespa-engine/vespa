// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fnet/transport.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchvisitor/searchenvironment.h>
#include <vespa/searchvisitor/search_environment_snapshot.h>
#include <vespa/searchvisitor/searchvisitor.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("searchvisitor_test");

using namespace document;
using namespace search::query;
using namespace search;
using namespace storage;

namespace streaming {

vespalib::string get_doc_id(int id) {
    return "id:test:test::" + std::to_string(id);
}

/**
 * This class reflects the document type defined in cfg/test.sd.
 */
struct MyDocument {
    int id;
    MyDocument(int id_in) : id(id_in) {}
    std::unique_ptr<Document> to_document(const DocumentTypeRepo& repo, const DataType& doc_type) const {
        auto result = std::make_unique<Document>(repo, doc_type, DocumentId(get_doc_id(id)));
        result->setValue("id", std::make_unique<IntFieldValue>(id));
        return result;
    }
};

using DocumentVector = std::vector<MyDocument>;

struct MyHit {
    vespalib::string doc_id;
    double rank;
    MyHit(int id, double rank_in) noexcept : doc_id(get_doc_id(id)), rank(rank_in) {}
    MyHit(int id) noexcept : doc_id(get_doc_id(id)), rank(0.0) {}
    MyHit(const vespalib::string& doc_id_in, double rank_in) noexcept : doc_id(doc_id_in), rank(rank_in) {}
    bool operator==(const MyHit& rhs) const {
        return (doc_id == rhs.doc_id) &&
                (rank == rhs.rank);
    }
};

using HitVector = std::vector<MyHit>;

std::ostream& operator<<(std::ostream& oss, const MyHit& hit) {
    oss << "{doc_id=" << hit.doc_id << ",rank=" << hit.rank << "}";
    return oss;
}

class RequestBuilder {
private:
    vdslib::Parameters _params;
    QueryBuilder<SimpleQueryNodeTypes> _builder;
    int32_t _term_id;

public:
    RequestBuilder() : _params(), _builder(), _term_id(1)
    {
        search_cluster("mycl");
        rank_profile("default");
        summary_class("default");
        summary_count(10);
    }
    RequestBuilder& set_param(const vespalib::string& key, const vespalib::string& value) {
        _params.set(key, value);
        return *this;
    }
    RequestBuilder& search_cluster(const vespalib::string& value) { return set_param("searchcluster", value); }
    RequestBuilder& rank_profile(const vespalib::string& value) { return set_param("rankprofile", value); }
    RequestBuilder& summary_class(const vespalib::string& value) { return set_param("summaryclass", value); }
    RequestBuilder& summary_count(uint32_t value) { return set_param("summarycount", std::to_string(value)); }
    RequestBuilder& string_term(const vespalib::string& term, const vespalib::string& field) {
        _builder.addStringTerm(term, field, _term_id++, Weight(100));
        return *this;
    }
    RequestBuilder& number_term(const vespalib::string& term, const vespalib::string& field) {
        _builder.addNumberTerm(term, field, _term_id++, Weight(100));
        return *this;
    }
    vdslib::Parameters build() {
        auto node = _builder.build();
        vespalib::string query_stack_dump = StackDumpCreator::create(*node);
        _params.set("query", query_stack_dump);
        return _params;
    }
};

struct VisitorSession {
    std::unique_ptr<SearchVisitor> search_visitor;
    Visitor& visitor;
    Visitor::HitCounter hit_counter;
    VisitorSession(SearchVisitor* sv)
        : search_visitor(sv),
          visitor(*search_visitor),
          hit_counter()
    {
    }
    void handle_documents(Visitor::DocEntryList& docs) {
        document::BucketId bucket_id;
        visitor.handleDocuments(bucket_id, docs, hit_counter);
    }
    std::unique_ptr<documentapi::QueryResultMessage> generate_query_result() {
        return search_visitor->generate_query_result(hit_counter);
    }
};

class SearchVisitorTest : public testing::Test {
public:
    framework::defaultimplementation::FakeClock _clock;
    StorageComponentRegisterImpl      _componentRegister;
    std::unique_ptr<StorageComponent> _component;
    SearchEnvironment                 _env;
    SearchVisitorFactory              _factory;
    std::shared_ptr<DocumentTypeRepo> _repo;
    const document::DocumentType*     _doc_type;

    SearchVisitorTest();
    ~SearchVisitorTest() override;

    std::unique_ptr<VisitorSession> make_visitor_session(const vdslib::Parameters& params) {
        VisitorFactory& factory(_factory);
        auto *visitor = factory.makeVisitor(*_component, _env, params);
        auto *search_visitor = dynamic_cast<SearchVisitor *>(visitor);
        assert(search_visitor != nullptr);
        return std::make_unique<VisitorSession>(search_visitor);
    }
    Visitor::DocEntryList make_documents(const std::vector<MyDocument>& docs) const {
        Visitor::DocEntryList result;
        for (const auto& doc : docs) {
            result.push_back(spi::DocEntry::create(spi::Timestamp(),
                                                   doc.to_document(*_repo, *_doc_type)));
        }
        return result;
    }
    std::unique_ptr<documentapi::QueryResultMessage> execute_query(const vdslib::Parameters& params,
                                                                   const DocumentVector& docs) {
        auto session = make_visitor_session(params);
        auto entries = make_documents(docs);
        session->handle_documents(entries);
        return session->generate_query_result();
    }
};

SearchVisitorTest::SearchVisitorTest() :
    _componentRegister(),
    _env(::config::ConfigUri("dir:cfg"), nullptr, ""),
    _factory(::config::ConfigUri("dir:cfg"), nullptr, ""),
    _repo(std::make_shared<DocumentTypeRepo>(readDocumenttypesConfig("cfg/documenttypes.cfg"))),
    _doc_type(_repo->getDocumentType("test"))
{
    assert(_doc_type != nullptr);
    _componentRegister.setNodeInfo("mycl", lib::NodeType::STORAGE, 1);
    _componentRegister.setClock(_clock);
    _componentRegister.setDocumentTypeRepo(_repo);
    _component = std::make_unique<StorageComponent>(_componentRegister, "storage");
}

SearchVisitorTest::~SearchVisitorTest()
{
    _env.clear_thread_local_env_map();
}

TEST_F(SearchVisitorTest, search_environment_is_configured)
{
    auto env = _env.get_snapshot("mycl");
    ASSERT_TRUE(env);
    EXPECT_TRUE(env->get_rank_manager_snapshot());
    EXPECT_TRUE(env->get_vsm_fields_config());
    EXPECT_TRUE(env->get_docsum_tools());
}

HitVector
to_hit_vector(vdslib::SearchResult& res)
{
    HitVector result;
    const char* doc_id;
    double rank;
    for (size_t i = 0; i < res.getHitCount(); ++i) {
        res.getHit(i, doc_id, rank);
        result.emplace_back(vespalib::string(doc_id), rank);
    }
    return result;
}

HitVector
to_hit_vector(vdslib::DocumentSummary& sum)
{
    HitVector result;
    const char* doc_id;
    const void* buf;
    size_t sz;
    for (size_t i = 0; i < sum.getSummaryCount(); ++i) {
        sum.getSummary(i, doc_id, buf, sz);
        result.emplace_back(vespalib::string(doc_id), 0.0);
    }
    return result;
}

void
expect_hits(const HitVector& exp_hits, documentapi::QueryResultMessage& res)
{
    EXPECT_EQ(exp_hits.size(), res.getSearchResult().getHitCount());
    EXPECT_EQ(exp_hits, to_hit_vector(res.getSearchResult()));
}

void
expect_summary(const HitVector& exp_summary, documentapi::QueryResultMessage& res)
{
    EXPECT_EQ(exp_summary.size(), res.getDocumentSummary().getSummaryCount());
    EXPECT_EQ(exp_summary, to_hit_vector(res.getDocumentSummary()));
}

void
expect_match_features(const std::vector<vespalib::string>& exp_names,
                      const std::vector<vespalib::FeatureSet::Value>& exp_values,
                      documentapi::QueryResultMessage& res)
{
    const auto& mf = res.getSearchResult().get_match_features();
    EXPECT_EQ(exp_names, mf.names);
    EXPECT_EQ(exp_values, mf.values);
}


TEST_F(SearchVisitorTest, basic_query_execution_in_search_visitor)
{
    auto res = execute_query(RequestBuilder().number_term("[5;10]", "id").build(),
                             {{3},{7},{4},{5},{9}});
    expect_hits({{9,19.0}, {7,17.0}, {5,15.0}}, *res);
    // Document summaries are ordered in document id order:
    expect_summary({{5}, {7}, {9}}, *res);
    expect_match_features({}, {}, *res);
}

TEST_F(SearchVisitorTest, match_features_returned_in_search_result)
{
    auto res = execute_query(RequestBuilder().
                                     rank_profile("match_features").
                                     number_term("[5;10]", "id").build(),
                             {{5},{4},{7}});
    expect_hits({{7,17.0}, {5,15.0}}, *res);
    // Raw match features are ordered in matching order.
    expect_match_features({"attribute(id)", "myfunc"}, {{5.0}, {25.0}, {7.0}, {27.0}}, *res);
}

TEST_F(SearchVisitorTest, visitor_only_require_weak_read_consistency)
{
    vdslib::Parameters params;
    auto session = make_visitor_session(params);
    EXPECT_TRUE(session->visitor.getRequiredReadConsistency() == spi::ReadConsistency::WEAK);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
