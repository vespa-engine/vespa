// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/check_require.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/fnet/databuffer.h>
#include <vespa/fnet/transport.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/searchlib/query/tree/query_to_protobuf.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchvisitor/search_environment_snapshot.h>
#include <vespa/searchvisitor/searchenvironment.h>
#include <vespa/searchvisitor/searchvisitor.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/test/test_path.h>

#include <string>

#include <vespa/log/log.h>
LOG_SETUP("searchvisitor_test");

using namespace document;
using namespace search::query;
using namespace search;
using namespace storage;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;

namespace streaming {

std::string get_doc_id(int id) {
    return "id:test:test::" + std::to_string(id);
}

std::string src_cfg(std::string_view prefix, std::string_view suffix) {
    return std::string(prefix) + TEST_PATH("cfg") + std::string(suffix);
}

void set_tensor_field(Document& doc, const std::string& field_name, const std::string& spec_expr) {
    if (spec_expr.empty()) {
        return;
    }
    const auto& data_type = dynamic_cast<const TensorDataType&>(doc.getField(field_name).getDataType());
    auto        fv = std::make_unique<TensorFieldValue>(data_type);
    *fv = SimpleValue::from_spec(TensorSpec::from_expr(spec_expr));
    doc.setValue(field_name, std::move(fv));
}

/**
 * This class reflects the document type defined in cfg/test.sd.
 */
struct MyDocument {
    int         id;
    std::string vec;
    std::string vec2;
    MyDocument(int id_in) : id(id_in), vec(), vec2() {}
    MyDocument(int id_in, std::string vec_in, std::string vec2_in = "")
        : id(id_in), vec(std::move(vec_in)), vec2(std::move(vec2_in)) {}
    std::unique_ptr<Document> to_document(const DocumentTypeRepo& repo, const DataType& doc_type) const {
        auto result = std::make_unique<Document>(repo, doc_type, DocumentId(get_doc_id(id)));
        result->setValue("id", std::make_unique<IntFieldValue>(id));
        set_tensor_field(*result, "vec", vec);
        set_tensor_field(*result, "vec2", vec2);
        return result;
    }
};

using DocumentVector = std::vector<MyDocument>;

struct MyHit {
    std::string doc_id;
    double      rank;
    MyHit(int id, double rank_in) noexcept : doc_id(get_doc_id(id)), rank(rank_in) {}
    MyHit(int id) noexcept : doc_id(get_doc_id(id)), rank(0.0) {}
    MyHit(const std::string& doc_id_in, double rank_in) noexcept : doc_id(doc_id_in), rank(rank_in) {}
    bool operator==(const MyHit& rhs) const { return (doc_id == rhs.doc_id) && (rank == rhs.rank); }
};

using HitVector = std::vector<MyHit>;

std::ostream& operator<<(std::ostream& oss, const MyHit& hit) {
    oss << "{doc_id=" << hit.doc_id << ",rank=" << hit.rank << "}";
    return oss;
}

class RequestBuilder {
private:
    vdslib::Parameters                 _params;
    QueryBuilder<SimpleQueryNodeTypes>               _builder;
    int32_t                                          _term_id;
    bool                                             _use_protobuf;
    std::vector<std::pair<std::string, std::string>> _rank_properties;

    void encode_rank_properties() {
        // Same format as decoded by SearchVisitor: a count followed by FS4Properties
        FNET_DataBuffer buf;
        auto            write_string = [&buf](const std::string& str) {
            buf.WriteInt32(str.size());
            buf.WriteBytes(str.data(), str.size());
        };
        buf.WriteInt32(1);
        write_string("rank");
        buf.WriteInt32(_rank_properties.size());
        for (const auto& [key, value] : _rank_properties) {
            write_string(key);
            write_string(value);
        }
        _params.set("rankproperties", std::string_view(buf.GetData(), buf.GetDataLen()));
    }

public:
    RequestBuilder() : _params(), _builder(), _term_id(1), _use_protobuf(false), _rank_properties() {
        search_cluster("mycl");
        rank_profile("default");
        summary_class("default");
        summary_count(10);
    }
    RequestBuilder& set_param(const std::string& key, const std::string& value) {
        _params.set(key, value);
        return *this;
    }
    RequestBuilder& search_cluster(const std::string& value) { return set_param("searchcluster", value); }
    RequestBuilder& rank_profile(const std::string& value) { return set_param("rankprofile", value); }
    RequestBuilder& summary_class(const std::string& value) { return set_param("summaryclass", value); }
    RequestBuilder& summary_count(uint32_t value) { return set_param("summarycount", std::to_string(value)); }
    RequestBuilder& sort(const std::string& value) { return set_param("sort", value); }
    RequestBuilder& query_stack_count(uint32_t value) { return set_param("querystackcount", std::to_string(value)); }
    RequestBuilder& string_term(const std::string& term, const std::string& field) {
        _builder.addStringTerm(term, field, _term_id++, Weight(100));
        return *this;
    }
    RequestBuilder& number_term(const std::string& term, const std::string& field) {
        _builder.addNumberTerm(term, field, _term_id++, Weight(100));
        return *this;
    }
    // Sparse tensor WAND is only supported by the protobuf query tree
    RequestBuilder& sparse_tensor_wand_term(const std::string& query_tensor_name, const std::string& field,
                                            uint32_t target_hits, double score_threshold) {
        _builder.add_sparse_tensor_wand_term(query_tensor_name, field, _term_id++, Weight(100), target_hits,
                                             score_threshold);
        _use_protobuf = true;
        return *this;
    }
    RequestBuilder& query_tensor(const std::string& name, const std::string& spec_expr) {
        vespalib::nbostream stream;
        vespalib::eval::encode_value(*SimpleValue::from_spec(TensorSpec::from_expr(spec_expr)), stream);
        _rank_properties.emplace_back("query(" + name + ")", std::string(stream.peek(), stream.size()));
        return *this;
    }
    RequestBuilder& dump_features() {
        constexpr int32_t QFLAG_DUMP_FEATURES = 0x00040000;
        _params.set("queryflags", QFLAG_DUMP_FEATURES);
        return *this;
    }
    vdslib::Parameters build() {
        auto node = _builder.build();
        if (_use_protobuf) {
            QueryToProtobuf converter;
            _params.set("querytree", converter.serialize(*node).SerializeAsString());
        } else {
            auto query_stack_dump = StackDumpCreator::create(*node);
            _params.set("query", std::string(query_stack_dump.data(), query_stack_dump.size()));
        }
        if (!_rank_properties.empty()) {
            encode_rank_properties();
        }
        return _params;
    }
};

struct VisitorSession {
    std::unique_ptr<SearchVisitor> search_visitor;
    Visitor&                       visitor;
    Visitor::HitCounter            hit_counter;
    VisitorSession(SearchVisitor* sv) : search_visitor(sv), visitor(*search_visitor), hit_counter() {}
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
    StorageComponentRegisterImpl                _componentRegister;
    std::unique_ptr<StorageComponent>           _component;
    SearchEnvironment                           _env;
    SearchVisitorFactory                        _factory;
    std::shared_ptr<DocumentTypeRepo>           _repo;
    const document::DocumentType*               _doc_type;

    SearchVisitorTest();
    ~SearchVisitorTest() override;

    std::unique_ptr<VisitorSession> make_visitor_session(const vdslib::Parameters& params) {
        VisitorFactory& factory(_factory);
        auto*           visitor = factory.makeVisitor(*_component, _env, params);
        auto*           search_visitor = dynamic_cast<SearchVisitor*>(visitor);
        CHECK(search_visitor != nullptr);
        return std::make_unique<VisitorSession>(search_visitor);
    }
    Visitor::DocEntryList make_documents(const std::vector<MyDocument>& docs) const {
        Visitor::DocEntryList result;
        for (const auto& doc : docs) {
            result.push_back(spi::DocEntry::create(spi::Timestamp(), doc.to_document(*_repo, *_doc_type)));
        }
        return result;
    }
    std::unique_ptr<documentapi::QueryResultMessage> execute_query(const vdslib::Parameters& params,
                                                                   const DocumentVector&     docs) {
        auto session = make_visitor_session(params);
        auto entries = make_documents(docs);
        session->handle_documents(entries);
        return session->generate_query_result();
    }
};

SearchVisitorTest::SearchVisitorTest()
    : _componentRegister(),
      _env(::config::ConfigUri(src_cfg("dir:", "")), nullptr, ""),
      _factory(::config::ConfigUri(src_cfg("dir:", "")), nullptr, ""),
      _repo(std::make_shared<DocumentTypeRepo>(readDocumenttypesConfig(src_cfg("", "/documenttypes.cfg")))),
      _doc_type(_repo->getDocumentType("test")) {
    CHECK(_doc_type != nullptr);
    _componentRegister.setNodeInfo("mycl", lib::NodeType::STORAGE, 1);
    _componentRegister.setClock(_clock);
    _componentRegister.setDocumentTypeRepo(_repo);
    _component = std::make_unique<StorageComponent>(_componentRegister, "storage");
}

SearchVisitorTest::~SearchVisitorTest() {
    _env.clear_thread_local_env_map();
}

TEST_F(SearchVisitorTest, search_environment_is_configured) {
    auto env = _env.get_snapshot("mycl");
    ASSERT_TRUE(env);
    EXPECT_TRUE(env->get_rank_manager_snapshot());
    EXPECT_TRUE(env->get_vsm_fields_config());
    EXPECT_TRUE(env->get_docsum_tools());
}

HitVector to_hit_vector(vdslib::SearchResult& res) {
    HitVector   result;
    const char* doc_id;
    double      rank;
    for (size_t i = 0; i < res.getHitCount(); ++i) {
        res.getHit(i, doc_id, rank);
        result.emplace_back(std::string(doc_id), rank);
    }
    return result;
}

HitVector to_hit_vector(vdslib::DocumentSummary& sum) {
    HitVector   result;
    const char* doc_id;
    const void* buf;
    size_t      sz;
    for (size_t i = 0; i < sum.getSummaryCount(); ++i) {
        sum.getSummary(i, doc_id, buf, sz);
        result.emplace_back(std::string(doc_id), 0.0);
    }
    return result;
}

void expect_hits(const HitVector& exp_hits, documentapi::QueryResultMessage& res) {
    EXPECT_EQ(exp_hits.size(), res.getSearchResult().getHitCount());
    EXPECT_EQ(exp_hits, to_hit_vector(res.getSearchResult()));
}

void expect_summary(const HitVector& exp_summary, documentapi::QueryResultMessage& res) {
    EXPECT_EQ(exp_summary.size(), res.getDocumentSummary().getSummaryCount());
    EXPECT_EQ(exp_summary, to_hit_vector(res.getDocumentSummary()));
}

void expect_match_features(const std::vector<std::string>&                 exp_names,
                           const std::vector<vespalib::FeatureSet::Value>& exp_values,
                           documentapi::QueryResultMessage&                res) {
    const auto& mf = res.getSearchResult().get_match_features();
    EXPECT_EQ(exp_names, mf.names);
    EXPECT_EQ(exp_values, mf.values);
}

TEST_F(SearchVisitorTest, basic_query_execution_in_search_visitor) {
    auto res = execute_query(RequestBuilder().number_term("[5;10]", "id").build(), {{3}, {7}, {4}, {5}, {9}});
    expect_hits({{9, 19.0}, {7, 17.0}, {5, 15.0}}, *res);
    // Document summaries are ordered in document id order:
    expect_summary({{5}, {7}, {9}}, *res);
    expect_match_features({}, {}, *res);
}

TEST_F(SearchVisitorTest, match_features_returned_in_search_result) {
    auto res = execute_query(RequestBuilder().rank_profile("match_features").number_term("[5;10]", "id").build(),
                             {{5}, {4}, {7}});
    expect_hits({{7, 17.0}, {5, 15.0}}, *res);
    // Raw match features are ordered in matching order.
    expect_match_features({"attribute(id)", "myfunc"}, {{5.0}, {25.0}, {7.0}, {27.0}}, *res);
}

TEST_F(SearchVisitorTest, sparse_tensor_wand_ranks_documents_by_dot_product) {
    auto res = execute_query(RequestBuilder()
                                 .rank_profile("sparse_tensor_wand")
                                 .sparse_tensor_wand_term("qt", "vec", 10, 0.0)
                                 .query_tensor("qt", "tensor(x{}):{a:1,b:2}")
                                 .build(),
                             {{1, "tensor(x{}):{a:1}"},
                              {2, "tensor(x{}):{b:3}"},
                              {3, "tensor(x{}):{c:5}"},
                              {4, "tensor(x{}):{a:2,b:1}"},
                              {5}});
    // Documents 3 (no overlapping labels) and 5 (no tensor) do not match
    expect_hits({{2, 6.0}, {4, 4.0}, {1, 1.0}}, *res);
}

namespace {

void check_score_threshold_raised_by_hits(SearchVisitorTest& test, bool dump_features) {
    SCOPED_TRACE(dump_features ? "dump features" : "no dump features");
    RequestBuilder builder;
    builder.rank_profile("sparse_tensor_wand")
        .sparse_tensor_wand_term("qt", "vec", 2, 0.0)
        .query_tensor("qt", "tensor(x{}):{a:1,b:2}");
    if (dump_features) {
        // Unpacks match data a second time for the dump processor, which must not add the hit to the heap again
        builder.dump_features();
    }
    auto res = test.execute_query(builder.build(), {{1, "tensor(x{}):{b:4}"},
                                                    {2, "tensor(x{}):{b:3}"},
                                                    {3, "tensor(x{}):{a:5}"},
                                                    {4, "tensor(x{}):{a:7}"}});
    // After documents 1 and 2 the heap is full with threshold 6, so document 3 does not match
    expect_hits({{1, 8.0}, {4, 7.0}, {2, 6.0}}, *res);
}

} // namespace

TEST_F(SearchVisitorTest, sparse_tensor_wand_score_threshold_is_raised_by_hits) {
    check_score_threshold_raised_by_hits(*this, false);
    check_score_threshold_raised_by_hits(*this, true);
}

TEST_F(SearchVisitorTest, sparse_tensor_wand_searching_fieldset_sets_raw_score_per_field) {
    auto res = execute_query(RequestBuilder()
                                 .rank_profile("sparse_tensor_wand")
                                 .sparse_tensor_wand_term("qt", "vecs", 1, 0.0)
                                 .query_tensor("qt", "tensor(x{}):{a:1,b:2}")
                                 .build(),
                             {{1, "tensor(x{}):{a:1}", "tensor(x{}):{b:1}"},
                              {2, "tensor(x{}):{a:3}"},
                              {3, "", "tensor(x{}):{a:2.5}"}});
    // Rank is rawScore(vec) + 1000 * rawScore(vec2). The best field score of document 1 (2) sets the
    // threshold, which document 2 (3) raises, so document 3 (2.5) does not match.
    expect_hits({{1, 2001.0}, {2, 3.0}}, *res);
}

TEST_F(SearchVisitorTest, visitor_only_require_weak_read_consistency) {
    vdslib::Parameters params;
    auto               session = make_visitor_session(params);
    EXPECT_TRUE(session->visitor.getRequiredReadConsistency() == spi::ReadConsistency::WEAK);
}

namespace {

void check_sorting(SearchVisitorTest& test, const std::string& sort_spec, const HitVector& exp_hits,
                   const std::vector<std::string>& exp_errors) {
    SCOPED_TRACE(sort_spec);
    auto res = test.execute_query(RequestBuilder()
                                      .rank_profile("default")
                                      .number_term("[4;10]", "id")
                                      .sort(sort_spec)
                                      .query_stack_count(1)
                                      .build(),
                                  {{5}, {4}, {3}, {7}});
    expect_hits(exp_hits, *res);
    EXPECT_EQ(exp_errors, res->getSearchResult().get_errors());
}

} // namespace

TEST_F(SearchVisitorTest, sorting_works) {
    check_sorting(*this, "-id", {{7, 17.0}, {5, 15.0}, {4, 14.0}}, {});
    check_sorting(*this, "+id", {{4, 14.0}, {5, 15.0}, {7, 17.0}}, {});
    check_sorting(*this, "-badid", {{7, 17.0}, {5, 15.0}, {4, 14.0}},
                  {"Cannot locate field 'badid' in field name registry"});
}

} // namespace streaming

GTEST_MAIN_RUN_ALL_TESTS()
