// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/check_require.h>
#include <vespa/document/base/fieldpath.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/streaming/sparse_tensor_wand_query_node.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vsm/searcher/mock_field_searcher_env.h>
#include <vespa/vsm/searcher/sparse_tensor_wand_field_searcher.h>
#include <vespa/vsm/vsm/fieldsearchspec.h>

using namespace search::fef;
using namespace search::streaming;
using namespace vespalib::eval;
using namespace vsm;

using document::FieldPathEntry;
using document::TensorDataType;
using document::TensorFieldValue;
using vespalib::Issue;

namespace {

std::unique_ptr<SparseTensorWandQueryNode> make_node(const std::string& query_tensor_name, uint32_t target_hits,
                                                     double score_threshold) {
    return std::make_unique<SparseTensorWandQueryNode>(nullptr, query_tensor_name, "my_tensor_field", target_hits,
                                                       score_threshold, 7, search::query::Weight(100));
}

struct MyIssues : Issue::Handler {
    std::vector<std::string> list;
    Issue::Binding           capture;
    MyIssues() : list(), capture(Issue::listen(*this)) {}
    ~MyIssues() override;
    void handle(const Issue& issue) override { list.push_back(issue.message()); }
};

MyIssues::~MyIssues() = default;

} // namespace

struct MockQuery {
    std::vector<std::unique_ptr<SparseTensorWandQueryNode>> nodes;
    QueryTermList                                           term_list;
    MockQuery& add(const std::string& query_tensor_name, uint32_t target_hits, double score_threshold) {
        nodes.push_back(make_node(query_tensor_name, target_hits, score_threshold));
        term_list.push_back(nodes.back().get());
        return *this;
    }
    SparseTensorWandQueryNode& get(size_t idx) const {
        CHECK(idx < nodes.size());
        return *nodes[idx];
    }
    void reset() {
        for (auto term : term_list) {
            term->reset();
        }
    }
};

class SparseTensorWandSearcherTest : public testing::Test {
public:
    vsm::test::MockFieldSearcherEnv     env;
    ValueType                           tensor_type;
    TensorDataType                      data_type;
    vsm::FieldIdT                       field_id;
    MockQuery                           query;
    SparseTensorWandFieldSearcher       searcher;
    SimpleTermData                      term_data;
    MatchData::UP                       match_data;
    search::fef::test::IndexEnvironment index_env;
    uint32_t                            next_docid;

    SparseTensorWandSearcherTest() : SparseTensorWandSearcherTest("tensor(x{})") {}
    explicit SparseTensorWandSearcherTest(const std::string& field_type_spec)
        : env(),
          tensor_type(ValueType::from_spec(field_type_spec)),
          data_type(tensor_type),
          field_id(2),
          query(),
          searcher(field_id),
          term_data(),
          match_data(MatchData::makeTestInstance(1, 1)),
          index_env(),
          next_docid(1) {
        term_data.addField(field_id).setHandle(0);
        env.field_paths->resize(field_id + 1);
        (*env.field_paths)[field_id].push_back(std::make_unique<FieldPathEntry>(data_type, "my_tensor_field"));
    }
    ~SparseTensorWandSearcherTest() override;
    void set_query_tensor(const std::string& query_tensor_name, const std::string& spec_expr) {
        auto tensor = SimpleValue::from_spec(TensorSpec::from_expr(spec_expr));
        search::fef::indexproperties::type::QueryFeature::set(env.index_env.getProperties(), query_tensor_name,
                                                              tensor->type().to_spec());
        vespalib::nbostream stream;
        vespalib::eval::encode_value(*tensor, stream);
        env.query_props.add(query_tensor_name, std::string_view(stream.peek(), stream.size()));
    }
    void prepare() { env.prepare(searcher, query.term_list); }
    TensorFieldValue field_value(const std::string& spec_expr) {
        TensorFieldValue fv(data_type);
        fv = SimpleValue::from_spec(TensorSpec::from_expr(spec_expr));
        return fv;
    }
    void match(const std::string& spec_expr, bool use_fast_value = false) {
        TensorFieldValue fv(data_type);
        auto             spec = TensorSpec::from_expr(spec_expr);
        if (use_fast_value) {
            fv = value_from_spec(spec, FastValueBuilderFactory::get());
        } else {
            fv = SimpleValue::from_spec(spec);
        }
        query.reset();
        searcher.onValue(fv);
    }
    // Simulates that the document became a hit, which updates the heap.
    void unpack(SparseTensorWandQueryNode& node) {
        node.unpack_match_data(next_docid++, term_data, *match_data, index_env, search::common::ElementIds::select_all());
    }
    void expect_match(const std::string& spec_expr, double exp_score, SparseTensorWandQueryNode& node,
                      bool use_fast_value = false) {
        match(spec_expr, use_fast_value);
        EXPECT_TRUE(node.evaluate()) << spec_expr;
        ASSERT_EQ(1u, node.get_field_scores().size()) << spec_expr;
        EXPECT_EQ(field_id, node.get_field_scores()[0].first) << spec_expr;
        EXPECT_DOUBLE_EQ(exp_score, node.get_field_scores()[0].second) << spec_expr;
        unpack(node);
    }
    void expect_not_match(const std::string& spec_expr, SparseTensorWandQueryNode& node) {
        match(spec_expr);
        EXPECT_FALSE(node.evaluate()) << spec_expr;
    }
};

SparseTensorWandSearcherTest::~SparseTensorWandSearcherTest() = default;

TEST_F(SparseTensorWandSearcherTest, documents_with_dot_product_above_threshold_match) {
    query.add("qt", 100, 0.0);
    auto& node = query.get(0);
    set_query_tensor("qt", "tensor(x{}):{a:1,b:1,c:3}");
    prepare();

    expect_match("tensor(x{}):{a:1,b:2}", 3.0, node);
    expect_match("tensor(x{}):{b:10,c:1}", 13.0, node, true);
    expect_not_match("tensor(x{}):{d:5}", node);
    expect_not_match("tensor(x{}):{a:-1}", node);
    expect_not_match("tensor(x{}):{}", node);
}

TEST_F(SparseTensorWandSearcherTest, initial_score_threshold_is_used) {
    query.add("qt", 100, 3.5);
    auto& node = query.get(0);
    set_query_tensor("qt", "tensor(x{}):{a:1,b:1,c:3}");
    prepare();

    expect_not_match("tensor(x{}):{a:1,b:2}", node);
    expect_match("tensor(x{}):{a:4}", 4.0, node);
}

TEST_F(SparseTensorWandSearcherTest, score_threshold_is_raised_by_heap_of_hits) {
    query.add("qt", 2, 0.0);
    auto& node = query.get(0);
    set_query_tensor("qt", "tensor(x{}):{a:1,b:1,c:3}");
    prepare();

    expect_match("tensor(x{}):{a:1,b:2}", 3.0, node);
    expect_match("tensor(x{}):{c:1}", 3.0, node);
    // The heap is now full, and the threshold is 3
    expect_not_match("tensor(x{}):{a:2,b:1}", node);
    expect_match("tensor(x{}):{a:4}", 4.0, node);
    // A match that is not unpacked (not a document hit) does not update the heap
    match("tensor(x{}):{b:10,c:1}");
    EXPECT_TRUE(node.evaluate());
    // The threshold is still 3 (heap has 3 and 4)
    expect_match("tensor(x{}):{a:3.5}", 3.5, node);
    // The threshold is now 3.5 (heap has 3.5 and 4)
    expect_not_match("tensor(x{}):{a:3.5}", node);
    expect_match("tensor(x{}):{a:3.75}", 3.75, node);
}

TEST_F(SparseTensorWandSearcherTest, searchers_for_multiple_fields_share_score_threshold) {
    query.add("qt", 1, 0.0);
    auto& node = query.get(0);
    set_query_tensor("qt", "tensor(x{}):{a:1,b:1,c:3}");
    constexpr vsm::FieldIdT other_field_id = 3;
    env.field_paths->resize(other_field_id + 1);
    (*env.field_paths)[other_field_id].push_back(std::make_unique<FieldPathEntry>(data_type, "my_other_field"));
    SparseTensorWandFieldSearcher other_searcher(other_field_id);
    prepare();
    env.prepare(other_searcher, query.term_list);
    TermFieldMatchData& tfmd = *match_data->resolveTermField(0);

    query.reset();
    searcher.onValue(field_value("tensor(x{}):{a:2}"));
    other_searcher.onValue(field_value("tensor(x{}):{c:1}"));
    EXPECT_TRUE(node.evaluate());
    using FieldScores = std::vector<std::pair<uint32_t, double>>;
    EXPECT_EQ((FieldScores{{field_id, 2.0}, {other_field_id, 3.0}}), node.get_field_scores());
    unpack(node);
    EXPECT_EQ(2.0, tfmd.getRawScore());
    // The heap tracks the best score (3) among the fields of the hit, and is shared by both searchers
    EXPECT_EQ(3.0, node.get_min_score());
    query.reset();
    other_searcher.onValue(field_value("tensor(x{}):{a:3}"));
    EXPECT_FALSE(node.evaluate());
    expect_match("tensor(x{}):{a:3.5}", 3.5, node);
}

TEST_F(SparseTensorWandSearcherTest, query_tensor_type_must_match_field_type) {
    query.add("qt1", 100, 0.0);
    query.add("qt2", 100, 0.0);
    query.add("qt3", 100, 0.0);
    set_query_tensor("qt1", "tensor<float>(x{}):{a:1}");
    set_query_tensor("qt2", "tensor(y{}):{a:1}");
    MyIssues issues;
    prepare();
    EXPECT_EQ((std::vector<std::string>{
                  "SparseTensorWandQueryNode(my_tensor_field, qt1): Field tensor type (tensor(x{})) and query "
                  "tensor type (tensor<float>(x{})) differ",
                  "SparseTensorWandQueryNode(my_tensor_field, qt2): Field tensor type (tensor(x{})) and query "
                  "tensor type (tensor(y{})) differ",
                  // qt3 is not set at all
                  "Could not find query tensor for SparseTensorWandQueryNode(my_tensor_field, qt3)"}),
              issues.list);
    match("tensor(x{}):{a:1}");
    EXPECT_FALSE(query.get(0).evaluate());
    EXPECT_FALSE(query.get(1).evaluate());
    EXPECT_FALSE(query.get(2).evaluate());
}

class SparseTensorWandSearcherDenseFieldTest : public SparseTensorWandSearcherTest {
public:
    SparseTensorWandSearcherDenseFieldTest() : SparseTensorWandSearcherTest("tensor(x[2])") {}
};

TEST_F(SparseTensorWandSearcherDenseFieldTest, dense_field_is_not_supported) {
    query.add("qt", 100, 0.0);
    query.add("qt", 10, 0.0);
    set_query_tensor("qt", "tensor(x[2]):[1,2]");
    MyIssues issues;
    prepare();
    // Reported once for the field, not once per term
    EXPECT_EQ((std::vector<std::string>{"Tensor type for field 2 is 'tensor(x[2])', but expected it to be a "
                                        "sparse tensor with one mapped dimension"}),
              issues.list);
    match("tensor(x[2]):[3,4]");
    EXPECT_FALSE(query.get(0).evaluate());
    EXPECT_FALSE(query.get(1).evaluate());
}

TEST(SparseTensorWandFieldSearchSpecTest, sparse_tensor_wand_term_reconfigures_field_without_search_method) {
    using Searchmethod = vespa::config::search::vsm::VsmfieldsConfig::Fieldspec::Searchmethod;
    auto            node = make_node("qt", 10, 0.0);
    FieldSearchSpec none_spec(3, "my_tensor_field", Searchmethod::NONE, search::Normalizing::NONE, "", 0);
    none_spec.reconfig(*node);
    EXPECT_NE(nullptr, dynamic_cast<const SparseTensorWandFieldSearcher*>(&none_spec.searcher()));
    FieldSearchSpec string_spec(3, "my_string_field", Searchmethod::AUTOUTF8, search::Normalizing::NONE, "", 0);
    string_spec.reconfig(*node);
    EXPECT_EQ(nullptr, dynamic_cast<const SparseTensorWandFieldSearcher*>(&string_spec.searcher()));
}

GTEST_MAIN_RUN_ALL_TESTS()
