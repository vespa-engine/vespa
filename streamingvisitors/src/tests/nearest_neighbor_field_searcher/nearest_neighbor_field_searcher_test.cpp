// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/fieldpath.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/query/streaming/nearest_neighbor_query_node.h>
#include <vespa/searchlib/tensor/euclidean_distance.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vsm/searcher/mock_field_searcher_env.h>
#include <vespa/vsm/searcher/nearest_neighbor_field_searcher.h>

using namespace search::attribute::test;
using namespace search::attribute;
using namespace search::fef;
using namespace search::streaming;
using namespace search::tensor;
using namespace vespalib::eval;
using namespace vsm;

using document::FieldPath;
using document::FieldPathEntry;
using document::TensorDataType;
using document::TensorFieldValue;

struct MockQuery {
    std::vector<std::unique_ptr<NearestNeighborQueryNode>> nodes;
    QueryTermList term_list;
    MockQuery& add(const vespalib::string& query_tensor_name,
                   double distance_threshold) {
        std::unique_ptr<QueryNodeResultBase> base;
        auto node = std::make_unique<NearestNeighborQueryNode>(std::move(base), query_tensor_name, "my_tensor_field", 7, search::query::Weight(11), distance_threshold);
        nodes.push_back(std::move(node));
        term_list.push_back(nodes.back().get());
        return *this;
    }
    ~MockQuery() {}
    const NearestNeighborQueryNode& get(size_t idx) const {
        assert(idx < nodes.size());
        return *nodes[idx];
    }
    void reset() {
        for (auto term : term_list) {
            term->reset();
        }
    }
};

class NearestNeighborSearcherTest : public testing::Test {
public:
    vsm::test::MockFieldSearcherEnv env;
    ValueType tensor_type;
    TensorDataType data_type;
    SquaredEuclideanDistance dist_func;
    vsm::FieldIdT field_id;
    NearestNeighborFieldSearcher searcher;
    MockQuery query;

    NearestNeighborSearcherTest()
        : env(),
          tensor_type(ValueType::from_spec("tensor(x[2])")),
          data_type(tensor_type),
          dist_func(CellType::DOUBLE),
          field_id(2),
          searcher(field_id, DistanceMetric::Euclidean),
          query()
    {
        env.field_paths->resize(field_id + 1);
        (*env.field_paths)[field_id].push_back(std::make_unique<FieldPathEntry>(data_type, "my_tensor_field"));
    }
    void set_query_tensor(const vespalib::string& query_tensor_name,
                          const vespalib::string& spec_expr) {
        search::fef::indexproperties::type::QueryFeature::set(env.index_env.getProperties(), query_tensor_name, tensor_type.to_spec());
        auto tensor = SimpleValue::from_spec(TensorSpec::from_expr(spec_expr));
        vespalib::nbostream stream;
        vespalib::eval::encode_value(*tensor, stream);
        env.query_props.add(query_tensor_name, vespalib::stringref(stream.peek(), stream.size()));
    }
    void prepare() {
        env.prepare(searcher, query.term_list);
    }
    void match(const vespalib::string& spec_expr) {
        TensorFieldValue fv(data_type);
        auto tensor = SimpleValue::from_spec(TensorSpec::from_expr(spec_expr));
        fv = std::move(tensor);
        query.reset();
        searcher.onValue(fv);
    }
    void expect_match(double exp_square_distance, const NearestNeighborQueryNode& node) {
        double exp_raw_score = dist_func.to_rawscore(exp_square_distance);
        EXPECT_TRUE(node.evaluate());
        EXPECT_DOUBLE_EQ(exp_raw_score, node.get_raw_score().value());
    }
};

TEST_F(NearestNeighborSearcherTest, raw_score_calculated_with_distance_threshold)
{
    query.add("qt1", 3);
    set_query_tensor("qt1", "tensor(x[2]):[1,3]");
    prepare();

    match("tensor(x[2]):[1,5]");
    expect_match((5-3)*(5-3), query.get(0));

    match("tensor(x[2]):[1,6]");
    expect_match((6-3)*(6-3), query.get(0));

    match("tensor(x[2]):[1,7]");
    // This is not a match since ((7-3)*(7-3) = 16) is larger than the internal distance threshold of (3*3 = 9).
    EXPECT_FALSE(query.get(0).evaluate());
}

TEST_F(NearestNeighborSearcherTest, raw_score_calculated_for_two_query_operators)
{
    query.add("qt1", 3);
    query.add("qt2", 4);
    set_query_tensor("qt1", "tensor(x[2]):[1,3]");
    set_query_tensor("qt2", "tensor(x[2]):[1,4]");
    prepare();

    match("tensor(x[2]):[1,5]");
    expect_match((5-3)*(5-3), query.get(0));
    expect_match((5-4)*(5-4), query.get(1));

    match("tensor(x[2]):[1,7]");
    // This is not a match since ((7-3)*(7-3) = 16) is larger than the internal distance threshold of (3*3 = 9).
    EXPECT_FALSE(query.get(0).evaluate());
    expect_match((7-4)*(7-4), query.get(1));
}

TEST_F(NearestNeighborSearcherTest, distance_metric_from_string)
{
    using NNFS = NearestNeighborFieldSearcher;
    EXPECT_EQ(DistanceMetric::Euclidean,    NNFS::distance_metric_from_string("EUCLIDEAN"));
    EXPECT_EQ(DistanceMetric::Angular,      NNFS::distance_metric_from_string("ANGULAR"));
    EXPECT_EQ(DistanceMetric::GeoDegrees,   NNFS::distance_metric_from_string("GEODEGREES"));
    EXPECT_EQ(DistanceMetric::InnerProduct, NNFS::distance_metric_from_string("INNERPRODUCT"));
    EXPECT_EQ(DistanceMetric::Hamming,      NNFS::distance_metric_from_string("HAMMING"));
    EXPECT_EQ(DistanceMetric::Euclidean,    NNFS::distance_metric_from_string("not_available"));
}

GTEST_MAIN_RUN_ALL_TESTS()
