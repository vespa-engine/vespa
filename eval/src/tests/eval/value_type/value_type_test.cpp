// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/eval/eval/int8float.h>
#include <vespa/vespalib/util/bfloat16.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <ostream>

using vespalib::BFloat16;
using namespace vespalib::eval;

const size_t npos = ValueType::Dimension::npos;

ValueType type(const vespalib::string &type_str) {
    ValueType ret = ValueType::from_spec(type_str);
    ASSERT_TRUE(!ret.is_error() || (type_str == "error"));
    return ret;
}

std::vector<vespalib::string> str_list(const std::vector<vespalib::string> &list) {
    return list;
}

//-----------------------------------------------------------------------------

TEST("require that ERROR value type can be created") {
    ValueType t = ValueType::error_type();
    EXPECT_TRUE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

TEST("require that DOUBLE value type can be created") {
    ValueType t = ValueType::double_type();
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

TEST("require that TENSOR value type can be created") {
    ValueType t = ValueType::make_type(CellType::DOUBLE, {{"x", 10},{"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
}

TEST("require that float TENSOR value type can be created") {
    ValueType t = ValueType::make_type(CellType::FLOAT, {{"x", 10},{"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::FLOAT);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
}

TEST("require that bfloat16 TENSOR value type can be created") {
    ValueType t = ValueType::make_type(CellType::BFLOAT16, {{"x", 10},{"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::BFLOAT16);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
}

TEST("require that int8 TENSOR value type can be created") {
    ValueType t = ValueType::make_type(CellType::INT8, {{"x", 10},{"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::INT8);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
}

TEST("require that TENSOR value type sorts dimensions") {
    ValueType t = ValueType::make_type(CellType::DOUBLE, {{"x", 10}, {"z", 30}, {"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    ASSERT_EQUAL(t.dimensions().size(), 3u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
    EXPECT_EQUAL(t.dimensions()[2].name, "z");
    EXPECT_EQUAL(t.dimensions()[2].size, 30u);
}

TEST("require that non-double scalar values are not allowed") {
    EXPECT_TRUE(ValueType::make_type(CellType::FLOAT, {}).is_error());
    EXPECT_TRUE(ValueType::make_type(CellType::BFLOAT16, {}).is_error());
    EXPECT_TRUE(ValueType::make_type(CellType::INT8, {}).is_error());
}

TEST("require that use of zero-size dimensions result in error types") {
    EXPECT_TRUE(ValueType::make_type(CellType::DOUBLE, {{"x", 0}}).is_error());
}

TEST("require that duplicate dimension names result in error types") {
    EXPECT_TRUE(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"x"}}).is_error());
}

//-----------------------------------------------------------------------------

void verify_equal(const ValueType &a, const ValueType &b) {
    EXPECT_EQUAL(a, b);
    EXPECT_EQUAL(b, a);
    EXPECT_FALSE(a != b);
    EXPECT_FALSE(b != a);
    EXPECT_EQUAL(a, ValueType::either(a, b));
    EXPECT_EQUAL(a, ValueType::either(b, a));
}

void verify_not_equal(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(a != b);
    EXPECT_TRUE(b != a);
    EXPECT_FALSE(a == b);
    EXPECT_FALSE(b == a);
    EXPECT_TRUE(ValueType::either(a, b).is_error());
    EXPECT_TRUE(ValueType::either(b, a).is_error());
}

TEST("require that value types can be compared") {
    TEST_DO(verify_equal(ValueType::error_type(), ValueType::error_type()));
    TEST_DO(verify_not_equal(ValueType::error_type(), ValueType::double_type()));
    TEST_DO(verify_not_equal(ValueType::error_type(), ValueType::make_type(CellType::DOUBLE, {{"x"}})));
    TEST_DO(verify_equal(ValueType::double_type(), ValueType::double_type()));
    TEST_DO(verify_equal(ValueType::double_type(), ValueType::make_type(CellType::DOUBLE, {})));
    TEST_DO(verify_not_equal(ValueType::double_type(), ValueType::make_type(CellType::DOUBLE, {{"x"}})));
    TEST_DO(verify_equal(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y"}}), ValueType::make_type(CellType::DOUBLE, {{"y"}, {"x"}})));
    TEST_DO(verify_not_equal(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y"}}), ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y"}, {"z"}})));
    TEST_DO(verify_equal(ValueType::make_type(CellType::DOUBLE, {{"x", 10}, {"y", 20}}), ValueType::make_type(CellType::DOUBLE, {{"y", 20}, {"x", 10}})));
    TEST_DO(verify_not_equal(ValueType::make_type(CellType::DOUBLE, {{"x", 10}, {"y", 20}}), ValueType::make_type(CellType::DOUBLE, {{"x", 10}, {"y", 10}})));
    TEST_DO(verify_not_equal(ValueType::make_type(CellType::DOUBLE, {{"x", 10}}), ValueType::make_type(CellType::DOUBLE, {{"x"}})));
    TEST_DO(verify_equal(ValueType::make_type(CellType::FLOAT, {{"x", 10}}), ValueType::make_type(CellType::FLOAT, {{"x", 10}})));
    TEST_DO(verify_equal(ValueType::make_type(CellType::BFLOAT16, {{"x", 10}}), ValueType::make_type(CellType::BFLOAT16, {{"x", 10}})));
    TEST_DO(verify_equal(ValueType::make_type(CellType::INT8, {{"x", 10}}), ValueType::make_type(CellType::INT8, {{"x", 10}})));
    TEST_DO(verify_not_equal(ValueType::make_type(CellType::DOUBLE, {{"x", 10}}), ValueType::make_type(CellType::FLOAT, {{"x", 10}})));
    TEST_DO(verify_not_equal(ValueType::make_type(CellType::FLOAT, {{"x", 10}}), ValueType::make_type(CellType::BFLOAT16, {{"x", 10}})));
    TEST_DO(verify_not_equal(ValueType::make_type(CellType::FLOAT, {{"x", 10}}), ValueType::make_type(CellType::INT8, {{"x", 10}})));
    TEST_DO(verify_not_equal(ValueType::make_type(CellType::BFLOAT16, {{"x", 10}}), ValueType::make_type(CellType::INT8, {{"x", 10}})));
}

//-----------------------------------------------------------------------------

TEST("require that value type can make spec") {
    EXPECT_EQUAL("error", ValueType::error_type().to_spec());
    EXPECT_EQUAL("double", ValueType::double_type().to_spec());
    EXPECT_EQUAL("error", ValueType::make_type(CellType::FLOAT, {}).to_spec());
    EXPECT_EQUAL("error", ValueType::make_type(CellType::BFLOAT16, {}).to_spec());
    EXPECT_EQUAL("error", ValueType::make_type(CellType::INT8, {}).to_spec());
    EXPECT_EQUAL("double", ValueType::make_type(CellType::DOUBLE, {}).to_spec());
    EXPECT_EQUAL("tensor(x{})", ValueType::make_type(CellType::DOUBLE, {{"x"}}).to_spec());
    EXPECT_EQUAL("tensor(y[10])", ValueType::make_type(CellType::DOUBLE, {{"y", 10}}).to_spec());
    EXPECT_EQUAL("tensor(x{},y[10],z[5])", ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y", 10}, {"z", 5}}).to_spec());
    EXPECT_EQUAL("tensor<float>(x{})", ValueType::make_type(CellType::FLOAT, {{"x"}}).to_spec());
    EXPECT_EQUAL("tensor<float>(y[10])", ValueType::make_type(CellType::FLOAT, {{"y", 10}}).to_spec());
    EXPECT_EQUAL("tensor<float>(x{},y[10],z[5])", ValueType::make_type(CellType::FLOAT, {{"x"}, {"y", 10}, {"z", 5}}).to_spec());
    EXPECT_EQUAL("tensor<bfloat16>(x{})", ValueType::make_type(CellType::BFLOAT16, {{"x"}}).to_spec());
    EXPECT_EQUAL("tensor<bfloat16>(y[10])", ValueType::make_type(CellType::BFLOAT16, {{"y", 10}}).to_spec());
    EXPECT_EQUAL("tensor<bfloat16>(x{},y[10],z[5])", ValueType::make_type(CellType::BFLOAT16, {{"x"}, {"y", 10}, {"z", 5}}).to_spec());
    EXPECT_EQUAL("tensor<int8>(x{})", ValueType::make_type(CellType::INT8, {{"x"}}).to_spec());
    EXPECT_EQUAL("tensor<int8>(y[10])", ValueType::make_type(CellType::INT8, {{"y", 10}}).to_spec());
    EXPECT_EQUAL("tensor<int8>(x{},y[10],z[5])", ValueType::make_type(CellType::INT8, {{"x"}, {"y", 10}, {"z", 5}}).to_spec());
}

//-----------------------------------------------------------------------------

TEST("require that value type spec can be parsed") {
    EXPECT_EQUAL(ValueType::double_type(), type("double"));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {}), type("tensor()"));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {}), type("tensor<double>()"));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {{"x"}}), type("tensor(x{})"));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {{"y", 10}}), type("tensor(y[10])"));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y", 10}, {"z", 5}}), type("tensor(x{},y[10],z[5])"));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {{"y", 10}}), type("tensor<double>(y[10])"));
    EXPECT_EQUAL(ValueType::make_type(CellType::FLOAT, {{"y", 10}}), type("tensor<float>(y[10])"));
    EXPECT_EQUAL(ValueType::make_type(CellType::BFLOAT16, {{"y", 10}}), type("tensor<bfloat16>(y[10])"));
    EXPECT_EQUAL(ValueType::make_type(CellType::INT8, {{"y", 10}}), type("tensor<int8>(y[10])"));
}

TEST("require that value type spec can be parsed with extra whitespace") {
    EXPECT_EQUAL(ValueType::double_type(), type(" double "));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {}), type(" tensor ( ) "));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {}), type(" tensor < double > ( ) "));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {{"x"}}), type(" tensor ( x { } ) "));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {{"y", 10}}), type(" tensor ( y [ 10 ] ) "));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y", 10}, {"z", 5}}),
                 type(" tensor ( x { } , y [ 10 ] , z [ 5 ] ) "));
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {{"y", 10}}), type(" tensor < double > ( y [ 10 ] ) "));
    EXPECT_EQUAL(ValueType::make_type(CellType::FLOAT, {{"y", 10}}), type(" tensor < float > ( y [ 10 ] ) "));
}

TEST("require that the unsorted dimension list can be obtained when parsing type spec") {
    std::vector<ValueType::Dimension> unsorted;
    auto type = ValueType::from_spec("tensor(y[10],z[5],x{})", unsorted);
    EXPECT_EQUAL(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y", 10}, {"z", 5}}), type);
    ASSERT_EQUAL(unsorted.size(), 3u);
    EXPECT_EQUAL(unsorted[0].name, "y");
    EXPECT_EQUAL(unsorted[0].size, 10u);
    EXPECT_EQUAL(unsorted[1].name, "z");
    EXPECT_EQUAL(unsorted[1].size, 5u);
    EXPECT_EQUAL(unsorted[2].name, "x");
    EXPECT_EQUAL(unsorted[2].size, npos);
}

TEST("require that the unsorted dimension list can be obtained also when the type spec is invalid") {
    std::vector<ValueType::Dimension> unsorted;
    auto type = ValueType::from_spec("tensor(x[10],x[5])...", unsorted);
    EXPECT_TRUE(type.is_error());
    ASSERT_EQUAL(unsorted.size(), 2u);
    EXPECT_EQUAL(unsorted[0].name, "x");
    EXPECT_EQUAL(unsorted[0].size, 10u);
    EXPECT_EQUAL(unsorted[1].name, "x");
    EXPECT_EQUAL(unsorted[1].size, 5u);
}

TEST("require that the unsorted dimension list can not be obtained if the parse itself fails") {
    std::vector<ValueType::Dimension> unsorted;
    auto type = ValueType::from_spec("tensor(x[10],x[5]", unsorted);
    EXPECT_TRUE(type.is_error());
    EXPECT_EQUAL(unsorted.size(), 0u);
}

TEST("require that malformed value type spec is parsed as error") {
    EXPECT_TRUE(ValueType::from_spec("").is_error());
    EXPECT_TRUE(ValueType::from_spec("  ").is_error());
    EXPECT_TRUE(ValueType::from_spec("error").is_error());
    EXPECT_TRUE(ValueType::from_spec("any").is_error());
    EXPECT_TRUE(ValueType::from_spec("float").is_error());
    EXPECT_TRUE(ValueType::from_spec("bfloat16").is_error());
    EXPECT_TRUE(ValueType::from_spec("int8").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor<double>").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor() tensor()").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{10})").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{},)").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(,x{})").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{},,y{})").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{} y{})").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{}").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{}),").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x[10)").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x[foo])").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x,y)").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{},x{})").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{},x[10])").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{},x[])").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor(z[])").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor<float>()").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor<bfloat16>()").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor<int8>()").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor<int7>(x[10])").is_error());
}

struct ParseResult {
    vespalib::string spec;
    const char *pos;
    const char *end;
    const char *after;
    ValueType type;
    ParseResult(const vespalib::string &spec_in);
    ~ParseResult();
    bool after_inside() const { return ((after > pos) && (after < end)); }
};
ParseResult::ParseResult(const vespalib::string &spec_in)
    : spec(spec_in),
      pos(spec.data()),
      end(pos + spec.size()),
      after(nullptr),
      type(value_type::parse_spec(pos, end, after)) {}
ParseResult::~ParseResult() = default;

TEST("require that we can parse a partial string into a type with the low-level API") {
    ParseResult result("tensor(a[5]) , ");
    EXPECT_EQUAL(result.type, ValueType::make_type(CellType::DOUBLE, {{"a", 5}}));
    ASSERT_TRUE(result.after_inside());
    EXPECT_EQUAL(*result.after, ',');
}

TEST("require that 'error' is the valid representation of the error type") {
    ParseResult valid(" error ");
    ParseResult invalid(" fubar ");
    EXPECT_EQUAL(valid.type, ValueType::error_type());
    EXPECT_TRUE(valid.after == valid.end); // parse ok
    EXPECT_EQUAL(invalid.type, ValueType::error_type());
    EXPECT_TRUE(invalid.after == nullptr); // parse not ok
}

//-----------------------------------------------------------------------------

TEST("require that value types preserve cell type") {
    EXPECT_TRUE(type("tensor(x[10])").cell_type() == CellType::DOUBLE);
    EXPECT_TRUE(type("tensor<double>(x[10])").cell_type() == CellType::DOUBLE);
    EXPECT_TRUE(type("tensor<float>(x[10])").cell_type() == CellType::FLOAT);
    EXPECT_TRUE(type("tensor<bfloat16>(x[10])").cell_type() == CellType::BFLOAT16);
    EXPECT_TRUE(type("tensor<int8>(x[10])").cell_type() == CellType::INT8);
}

TEST("require that dimension names can be obtained") {
    EXPECT_EQUAL(type("double").dimension_names(), str_list({}));
    EXPECT_EQUAL(type("tensor(y[30],x[10])").dimension_names(), str_list({"x", "y"}));
    EXPECT_EQUAL(type("tensor<float>(y[10],x[30],z{})").dimension_names(), str_list({"x", "y", "z"}));
    EXPECT_EQUAL(type("tensor<bfloat16>(y[10],x[30],z{})").dimension_names(), str_list({"x", "y", "z"}));
    EXPECT_EQUAL(type("tensor<int8>(y[10],x[30],z{})").dimension_names(), str_list({"x", "y", "z"}));
}

TEST("require that nontrivial indexed dimensions can be obtained") {
    auto my_check = [](const auto &list)
                    {
                        ASSERT_EQUAL(list.size(), 1u);
                        EXPECT_EQUAL(list[0].name, "x");
                        EXPECT_EQUAL(list[0].size, 10u);
                    };
    EXPECT_TRUE(type("double").nontrivial_indexed_dimensions().empty());
    TEST_DO(my_check(type("tensor(x[10],y{})").nontrivial_indexed_dimensions()));
    TEST_DO(my_check(type("tensor(a[1],b[1],x[10],y{},z[1])").nontrivial_indexed_dimensions()));
}

TEST("require that mapped dimensions can be obtained") {
    auto my_check = [](const auto &list)
                    {
                        ASSERT_EQUAL(list.size(), 1u);
                        EXPECT_EQUAL(list[0].name, "x");
                        EXPECT_TRUE(list[0].is_mapped());
                    };
    EXPECT_TRUE(type("double").mapped_dimensions().empty());
    TEST_DO(my_check(type("tensor(x{},y[10])").mapped_dimensions()));
    TEST_DO(my_check(type("tensor(a[1],b[1],x{},y[10],z[1])").mapped_dimensions()));
}

TEST("require that dimension index can be obtained") {
    EXPECT_EQUAL(type("error").dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(type("double").dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(type("tensor()").dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(type("tensor(y[10],x{},z[5])").dimension_index("x"), 0u);
    EXPECT_EQUAL(type("tensor<float>(y[10],x{},z[5])").dimension_index("y"), 1u);
    EXPECT_EQUAL(type("tensor<bfloat16>(y[10],x{},z[5])").dimension_index("y"), 1u);
    EXPECT_EQUAL(type("tensor<int8>(y[10],x{},z[5])").dimension_index("y"), 1u);
    EXPECT_EQUAL(type("tensor(y[10],x{},z[5])").dimension_index("z"), 2u);
    EXPECT_EQUAL(type("tensor(y[10],x{},z[5])").dimension_index("w"), ValueType::Dimension::npos);
}

void verify_predicates(const ValueType &type,
                       bool expect_error, bool expect_double, bool expect_tensor,
                       bool expect_sparse, bool expect_dense, bool expect_mixed)
{
    EXPECT_EQUAL(type.is_error(), expect_error);
    EXPECT_EQUAL(type.is_double(), expect_double);
    EXPECT_EQUAL(type.has_dimensions(), expect_tensor);
    EXPECT_EQUAL(type.is_sparse(), expect_sparse);
    EXPECT_EQUAL(type.is_dense(), expect_dense);
    EXPECT_EQUAL(type.is_mixed(), expect_mixed);
}

TEST("require that type-related predicate functions work as expected") {
    TEST_DO(verify_predicates(type("error"), true, false, false, false, false, false));
    TEST_DO(verify_predicates(type("double"), false, true, false, false, false, false));
    TEST_DO(verify_predicates(type("tensor()"), false, true, false, false, false, false));
    TEST_DO(verify_predicates(type("tensor(x{})"), false, false, true, true, false, false));
    TEST_DO(verify_predicates(type("tensor(x{},y{})"), false, false, true, true, false, false));
    TEST_DO(verify_predicates(type("tensor(x[5])"), false, false, true, false, true, false));
    TEST_DO(verify_predicates(type("tensor(x[5],y[10])"), false, false, true, false, true, false));
    TEST_DO(verify_predicates(type("tensor(x[5],y{})"), false, false, true, false, false, true));
    TEST_DO(verify_predicates(type("tensor<float>(x{})"), false, false, true, true, false, false));
    TEST_DO(verify_predicates(type("tensor<float>(x[5])"), false, false, true, false, true, false));
    TEST_DO(verify_predicates(type("tensor<float>(x[5],y{})"), false, false, true, false, false, true));
    TEST_DO(verify_predicates(type("tensor<bfloat16>(x{})"), false, false, true, true, false, false));
    TEST_DO(verify_predicates(type("tensor<bfloat16>(x[5])"), false, false, true, false, true, false));
    TEST_DO(verify_predicates(type("tensor<bfloat16>(x[5],y{})"), false, false, true, false, false, true));
    TEST_DO(verify_predicates(type("tensor<int8>(x{})"), false, false, true, true, false, false));
    TEST_DO(verify_predicates(type("tensor<int8>(x[5])"), false, false, true, false, true, false));
    TEST_DO(verify_predicates(type("tensor<int8>(x[5],y{})"), false, false, true, false, false, true));
}

TEST("require that mapped and indexed dimensions can be counted") {
    EXPECT_EQUAL(type("double").count_mapped_dimensions(), 0u);
    EXPECT_EQUAL(type("double").count_indexed_dimensions(), 0u);
    EXPECT_EQUAL(type("tensor(x[5],y[5])").count_mapped_dimensions(), 0u);
    EXPECT_EQUAL(type("tensor(x[5],y[5])").count_indexed_dimensions(), 2u);
    EXPECT_EQUAL(type("tensor(x{},y[5])").count_mapped_dimensions(), 1u);
    EXPECT_EQUAL(type("tensor(x{},y[5])").count_indexed_dimensions(), 1u);
    EXPECT_EQUAL(type("tensor(x[1],y{})").count_mapped_dimensions(), 1u);
    EXPECT_EQUAL(type("tensor(x[1],y{})").count_indexed_dimensions(), 1u);
    EXPECT_EQUAL(type("tensor(x{},y{})").count_mapped_dimensions(), 2u);
    EXPECT_EQUAL(type("tensor(x{},y{})").count_indexed_dimensions(), 0u);
}

TEST("require that dense subspace size calculation works as expected") {
    EXPECT_EQUAL(type("error").dense_subspace_size(), 1u);
    EXPECT_EQUAL(type("double").dense_subspace_size(), 1u);
    EXPECT_EQUAL(type("tensor()").dense_subspace_size(), 1u);
    EXPECT_EQUAL(type("tensor(x{})").dense_subspace_size(), 1u);
    EXPECT_EQUAL(type("tensor(x{},y{})").dense_subspace_size(), 1u);
    EXPECT_EQUAL(type("tensor(x[5])").dense_subspace_size(), 5u);
    EXPECT_EQUAL(type("tensor(x[5],y[10])").dense_subspace_size(), 50u);
    EXPECT_EQUAL(type("tensor(x[5],y{})").dense_subspace_size(), 5u);
    EXPECT_EQUAL(type("tensor<float>(x{})").dense_subspace_size(), 1u);
    EXPECT_EQUAL(type("tensor<float>(x[5])").dense_subspace_size(), 5u);
    EXPECT_EQUAL(type("tensor<float>(x[5],y{})").dense_subspace_size(), 5u);
    EXPECT_EQUAL(type("tensor<bfloat16>(x{})").dense_subspace_size(), 1u);
    EXPECT_EQUAL(type("tensor<bfloat16>(x[5])").dense_subspace_size(), 5u);
    EXPECT_EQUAL(type("tensor<bfloat16>(x[5],y{})").dense_subspace_size(), 5u);
    EXPECT_EQUAL(type("tensor<int8>(x{})").dense_subspace_size(), 1u);
    EXPECT_EQUAL(type("tensor<int8>(x[5])").dense_subspace_size(), 5u);
    EXPECT_EQUAL(type("tensor<int8>(x[5],y{})").dense_subspace_size(), 5u);
}

TEST("require that dimension predicates work as expected") {
    ValueType::Dimension x("x");
    ValueType::Dimension y("y", 10);
    ValueType::Dimension z("z", 0);
    EXPECT_TRUE(x.is_mapped());
    EXPECT_TRUE(!x.is_indexed());
    EXPECT_TRUE(!y.is_mapped());
    EXPECT_TRUE(y.is_indexed());
    EXPECT_TRUE(!z.is_mapped());
    EXPECT_TRUE(z.is_indexed());
}

TEST("require that value type map decays cell type") {
    EXPECT_EQUAL(type("tensor(x[10])").map(), type("tensor(x[10])"));
    EXPECT_EQUAL(type("tensor<float>(x[10])").map(), type("tensor<float>(x[10])"));
    EXPECT_EQUAL(type("tensor<bfloat16>(x[10])").map(), type("tensor<float>(x[10])"));
    EXPECT_EQUAL(type("tensor<int8>(x[10])").map(), type("tensor<float>(x[10])"));
}

TEST("require that reducing dimensions from non-tensor types gives error type") {
    EXPECT_TRUE(type("error").reduce({"x"}).is_error());
    EXPECT_TRUE(type("double").reduce({"x"}).is_error());
}

TEST("require that a scalar value can be fully reduced to a scalar value") {
    EXPECT_EQUAL(type("double").reduce({}), type("double"));
}

TEST("require that tensor value types can be reduced") {
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({"x"}), type("tensor(y[20],z[30])"));
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({"y"}), type("tensor(x[10],z[30])"));
    EXPECT_EQUAL(type("tensor<float>(x[10],y[20],z[30])").reduce({"z"}), type("tensor<float>(x[10],y[20])"));
    EXPECT_EQUAL(type("tensor<bfloat16>(x[10],y[20],z[30])").reduce({"z"}), type("tensor<float>(x[10],y[20])"));
    EXPECT_EQUAL(type("tensor<int8>(x[10],y[20],z[30])").reduce({"z"}), type("tensor<float>(x[10],y[20])"));
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({"x", "z"}), type("tensor(y[20])"));
    EXPECT_EQUAL(type("tensor<float>(x[10],y[20],z[30])").reduce({"z", "x"}), type("tensor<float>(y[20])"));
    EXPECT_EQUAL(type("tensor<bfloat16>(x[10],y[20],z[30])").reduce({"z", "x"}), type("tensor<float>(y[20])"));
    EXPECT_EQUAL(type("tensor<int8>(x[10],y[20],z[30])").reduce({"z", "x"}), type("tensor<float>(y[20])"));
}

TEST("require that reducing an empty set of dimensions means reducing them all") {
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({}), type("double"));
    EXPECT_EQUAL(type("tensor<float>(x[10],y[20],z[30])").reduce({}), type("double"));
    EXPECT_EQUAL(type("tensor<bfloat16>(x[10],y[20],z[30])").reduce({}), type("double"));
    EXPECT_EQUAL(type("tensor<int8>(x[10],y[20],z[30])").reduce({}), type("double"));
}

TEST("require that reducing non-existing dimensions gives error type") {
    EXPECT_TRUE(type("tensor(y{})").reduce({"x"}).is_error());
    EXPECT_TRUE(type("tensor<float>(y[10])").reduce({"x"}).is_error());
}

TEST("require that reducing all dimensions gives double type") {
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
    EXPECT_EQUAL(type("tensor<float>(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
    EXPECT_EQUAL(type("tensor<bfloat16>(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
    EXPECT_EQUAL(type("tensor<int8>(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
}

void verify_join(const ValueType &a, const ValueType b, const ValueType &res) {
    EXPECT_EQUAL(ValueType::join(a, b), res);
    EXPECT_EQUAL(ValueType::join(b, a), res);
}

TEST("require that dimensions can be combined for value types") {
    TEST_DO(verify_join(type("double"), type("double"), type("double")));
    TEST_DO(verify_join(type("tensor(x{},y{})"), type("tensor(y{},z{})"), type("tensor(x{},y{},z{})")));
    TEST_DO(verify_join(type("tensor(y{})"), type("tensor(y{})"), type("tensor(y{})")));
    TEST_DO(verify_join(type("tensor(y{})"), type("double"), type("tensor(y{})")));
    TEST_DO(verify_join(type("tensor(a[10])"), type("tensor(a[10])"), type("tensor(a[10])")));
    TEST_DO(verify_join(type("tensor(a[10])"), type("double"), type("tensor(a[10])")));
    TEST_DO(verify_join(type("tensor(a[10])"), type("tensor(x{},y{},z{})"), type("tensor(a[10],x{},y{},z{})")));
}

TEST("require that cell type is handled correctly for join") {
    TEST_DO(verify_join(type("tensor(x{})"), type("tensor(y{})"),           type("tensor(x{},y{})")));
    TEST_DO(verify_join(type("tensor(x{})"), type("tensor<float>(y{})"),    type("tensor(x{},y{})")));
    TEST_DO(verify_join(type("tensor(x{})"), type("tensor<bfloat16>(y{})"), type("tensor(x{},y{})")));
    TEST_DO(verify_join(type("tensor(x{})"), type("tensor<int8>(y{})"),     type("tensor(x{},y{})")));
    TEST_DO(verify_join(type("tensor<float>(x{})"), type("tensor<float>(y{})"),    type("tensor<float>(x{},y{})")));
    TEST_DO(verify_join(type("tensor<float>(x{})"), type("tensor<bfloat16>(y{})"), type("tensor<float>(x{},y{})")));
    TEST_DO(verify_join(type("tensor<float>(x{})"), type("tensor<int8>(y{})"),     type("tensor<float>(x{},y{})")));
    TEST_DO(verify_join(type("tensor<bfloat16>(x{})"), type("tensor<bfloat16>(y{})"), type("tensor<float>(x{},y{})")));
    TEST_DO(verify_join(type("tensor<bfloat16>(x{})"), type("tensor<int8>(y{})"),     type("tensor<float>(x{},y{})")));
    TEST_DO(verify_join(type("tensor<int8>(x{})"), type("tensor<int8>(y{})"), type("tensor<float>(x{},y{})")));
    TEST_DO(verify_join(type("tensor(x{})"), type("double"), type("tensor(x{})")));
    TEST_DO(verify_join(type("tensor<float>(x{})"), type("double"), type("tensor<float>(x{})")));
    TEST_DO(verify_join(type("tensor<bfloat16>(x{})"), type("double"), type("tensor<float>(x{})")));
    TEST_DO(verify_join(type("tensor<int8>(x{})"), type("double"), type("tensor<float>(x{})")));
}

void verify_not_joinable(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(ValueType::join(a, b).is_error());
    EXPECT_TRUE(ValueType::join(b, a).is_error());
}

TEST("require that mapped and indexed dimensions are not joinable") {
    verify_not_joinable(type("tensor(x[10])"), type("tensor(x{})"));
}

TEST("require that indexed dimensions of different sizes are not joinable") {
    verify_not_joinable(type("tensor(x[10])"), type("tensor(x[20])"));
}

TEST("require that error type combined with anything produces error type") {
    verify_not_joinable(type("error"), type("error"));
    verify_not_joinable(type("error"), type("double"));
    verify_not_joinable(type("error"), type("tensor(x{})"));
    verify_not_joinable(type("error"), type("tensor(x[10])"));
}

TEST("require that tensor dimensions can be renamed") {
    EXPECT_EQUAL(type("tensor(x{})").rename({"x"}, {"y"}), type("tensor(y{})"));
    EXPECT_EQUAL(type("tensor(x{},y[5])").rename({"x","y"}, {"y","x"}), type("tensor(y{},x[5])"));
    EXPECT_EQUAL(type("tensor(x{})").rename({"x"}, {"x"}), type("tensor(x{})"));
    EXPECT_EQUAL(type("tensor(x{})").rename({}, {}), type("error"));
    EXPECT_EQUAL(type("double").rename({}, {}), type("error"));
    EXPECT_EQUAL(type("tensor(x{},y{})").rename({"x"}, {"y","z"}), type("error"));
    EXPECT_EQUAL(type("tensor(x{},y{})").rename({"x","y"}, {"z"}), type("error"));
    EXPECT_EQUAL(type("double").rename({"a"}, {"b"}), type("error"));
    EXPECT_EQUAL(type("error").rename({"a"}, {"b"}), type("error"));
}

TEST("require that dimension rename preserves cell type") {
    EXPECT_EQUAL(type("tensor(x{})").rename({"x"}, {"y"}), type("tensor(y{})"));
    EXPECT_EQUAL(type("tensor<float>(x{})").rename({"x"}, {"y"}), type("tensor<float>(y{})"));
    EXPECT_EQUAL(type("tensor<bfloat16>(x{})").rename({"x"}, {"y"}), type("tensor<bfloat16>(y{})"));
    EXPECT_EQUAL(type("tensor<int8>(x{})").rename({"x"}, {"y"}), type("tensor<int8>(y{})"));
}

void verify_merge(const ValueType &a, const ValueType &b, const ValueType &res) {
    EXPECT_EQUAL(ValueType::merge(a, b), res);
    EXPECT_EQUAL(ValueType::merge(b, a), res);
}

TEST("require that similar types can be merged") {
    TEST_DO(verify_merge(type("error"), type("error"), type("error")));
    TEST_DO(verify_merge(type("double"), type("double"), type("double")));
    TEST_DO(verify_merge(type("tensor(x[5])"), type("tensor(x[5])"),           type("tensor(x[5])")));
    TEST_DO(verify_merge(type("tensor(x[5])"), type("tensor<float>(x[5])"),    type("tensor(x[5])")));
    TEST_DO(verify_merge(type("tensor(x[5])"), type("tensor<bfloat16>(x[5])"), type("tensor(x[5])")));
    TEST_DO(verify_merge(type("tensor(x[5])"), type("tensor<int8>(x[5])"),     type("tensor(x[5])")));
    TEST_DO(verify_merge(type("tensor<float>(x[5])"), type("tensor<float>(x[5])"),    type("tensor<float>(x[5])")));
    TEST_DO(verify_merge(type("tensor<float>(x[5])"), type("tensor<bfloat16>(x[5])"), type("tensor<float>(x[5])")));
    TEST_DO(verify_merge(type("tensor<float>(x[5])"), type("tensor<int8>(x[5])"),     type("tensor<float>(x[5])")));
    TEST_DO(verify_merge(type("tensor<bfloat16>(x[5])"), type("tensor<bfloat16>(x[5])"), type("tensor<float>(x[5])")));
    TEST_DO(verify_merge(type("tensor<bfloat16>(x[5])"), type("tensor<int8>(x[5])"),     type("tensor<float>(x[5])")));
    TEST_DO(verify_merge(type("tensor<int8>(x[5])"), type("tensor<int8>(x[5])"),     type("tensor<float>(x[5])")));
    TEST_DO(verify_merge(type("tensor(x{})"), type("tensor(x{})"), type("tensor(x{})")));
}

TEST("require that diverging types can not be merged") {
    EXPECT_EQUAL(ValueType::merge(type("error"), type("double")), type("error"));
    EXPECT_EQUAL(ValueType::merge(type("double"), type("error")), type("error"));
    EXPECT_EQUAL(ValueType::merge(type("tensor(x[5])"), type("double")), type("error"));
    EXPECT_EQUAL(ValueType::merge(type("double"), type("tensor(x[5])")), type("error"));
    EXPECT_EQUAL(ValueType::merge(type("tensor(x[5])"), type("tensor(x[3])")), type("error"));
    EXPECT_EQUAL(ValueType::merge(type("tensor(x{})"), type("tensor(y{})")), type("error"));
}

void verify_concat(const ValueType &a, const ValueType &b, const vespalib::string &dim, const ValueType &res) {
    EXPECT_EQUAL(ValueType::concat(a, b, dim), res);
    EXPECT_EQUAL(ValueType::concat(b, a, dim), res);
}

TEST("require that types can be concatenated") {
    TEST_DO(verify_concat(type("error"),             type("tensor(x[2])"), "x", type("error")));
    TEST_DO(verify_concat(type("tensor(x{})"),       type("tensor(x[2])"), "x", type("error")));
    TEST_DO(verify_concat(type("tensor(x{})"),       type("tensor(x{})"),  "x", type("error")));
    TEST_DO(verify_concat(type("tensor(x{})"),       type("double"),       "x", type("error")));
    TEST_DO(verify_concat(type("tensor(x[3])"),      type("tensor(x[2])"), "y", type("error")));
    TEST_DO(verify_concat(type("tensor(y[7])"),      type("tensor(x{})"),  "z", type("tensor(x{},y[7],z[2])")));
    TEST_DO(verify_concat(type("double"),            type("double"),       "x", type("tensor(x[2])")));
    TEST_DO(verify_concat(type("tensor(x[2])"),      type("double"),       "x", type("tensor(x[3])")));
    TEST_DO(verify_concat(type("tensor(x[3])"),      type("tensor(x[2])"), "x", type("tensor(x[5])")));
    TEST_DO(verify_concat(type("tensor(x[2])"),      type("double"),       "y", type("tensor(x[2],y[2])")));
    TEST_DO(verify_concat(type("tensor(x[2])"),      type("tensor(x[2])"), "y", type("tensor(x[2],y[2])")));
    TEST_DO(verify_concat(type("tensor(x[2],y[2])"), type("tensor(x[3])"), "x", type("tensor(x[5],y[2])")));
    TEST_DO(verify_concat(type("tensor(x[2],y[2])"), type("tensor(y[7])"), "y", type("tensor(x[2],y[9])")));
    TEST_DO(verify_concat(type("tensor(x[5])"),      type("tensor(y[7])"), "z", type("tensor(x[5],y[7],z[2])")));
}

TEST("require that cell type is handled correctly for concat") {
    TEST_DO(verify_concat(type("tensor(x[3])"), type("tensor(x[2])"), "x",           type("tensor(x[5])")));
    TEST_DO(verify_concat(type("tensor(x[3])"), type("tensor<float>(x[2])"), "x",    type("tensor(x[5])")));
    TEST_DO(verify_concat(type("tensor(x[3])"), type("tensor<bfloat16>(x[2])"), "x", type("tensor(x[5])")));
    TEST_DO(verify_concat(type("tensor(x[3])"), type("tensor<int8>(x[2])"), "x",     type("tensor(x[5])")));
    TEST_DO(verify_concat(type("tensor<float>(x[3])"), type("tensor<float>(x[2])"), "x",    type("tensor<float>(x[5])")));
    TEST_DO(verify_concat(type("tensor<float>(x[3])"), type("tensor<bfloat16>(x[2])"), "x", type("tensor<float>(x[5])")));
    TEST_DO(verify_concat(type("tensor<float>(x[3])"), type("tensor<int8>(x[2])"), "x",     type("tensor<float>(x[5])")));
    TEST_DO(verify_concat(type("tensor<bfloat16>(x[3])"), type("tensor<bfloat16>(x[2])"), "x", type("tensor<bfloat16>(x[5])")));
    TEST_DO(verify_concat(type("tensor<bfloat16>(x[3])"), type("tensor<int8>(x[2])"), "x",     type("tensor<float>(x[5])")));
    TEST_DO(verify_concat(type("tensor<int8>(x[3])"), type("tensor<int8>(x[2])"), "x",     type("tensor<int8>(x[5])")));
}

TEST("require that concat with number preserves cell type") {
    TEST_DO(verify_concat(type("tensor(x[3])"), type("double"), "x", type("tensor(x[4])")));
    TEST_DO(verify_concat(type("tensor<float>(x[3])"), type("double"), "x", type("tensor<float>(x[4])")));
    TEST_DO(verify_concat(type("tensor<bfloat16>(x[3])"), type("double"), "x", type("tensor<bfloat16>(x[4])")));
    TEST_DO(verify_concat(type("tensor<int8>(x[3])"), type("double"), "x", type("tensor<int8>(x[4])")));
}

void verify_cell_cast(const ValueType &type) {
    for (CellType cell_type: CellTypeUtils::list_types()) {
        auto res_type = type.cell_cast(cell_type);
        if (type.is_error()) {
            EXPECT_TRUE(res_type.is_error());
            EXPECT_EQUAL(res_type, type);
        } else if (type.is_double()) {
            if (cell_type == CellType::DOUBLE) {
                EXPECT_TRUE(res_type.is_double());
            } else {
                EXPECT_TRUE(res_type.is_error());
            }
        } else {
            EXPECT_FALSE(res_type.is_error());
            EXPECT_EQUAL(int(res_type.cell_type()), int(cell_type));
            EXPECT_TRUE(res_type.dimensions() == type.dimensions());
        }
    }
}

TEST("require that value type cell cast works correctly") {
    TEST_DO(verify_cell_cast(type("error")));
    TEST_DO(verify_cell_cast(type("double")));
    TEST_DO(verify_cell_cast(type("tensor<double>(x[10])")));
    TEST_DO(verify_cell_cast(type("tensor<float>(x[10])")));
    TEST_DO(verify_cell_cast(type("tensor<bfloat16>(x[10])")));
    TEST_DO(verify_cell_cast(type("tensor<int8>(x[10])")));
    TEST_DO(verify_cell_cast(type("tensor<double>(x{})")));
    TEST_DO(verify_cell_cast(type("tensor<float>(x{})")));
    TEST_DO(verify_cell_cast(type("tensor<bfloat16>(x{})")));
    TEST_DO(verify_cell_cast(type("tensor<int8>(x{})")));
    TEST_DO(verify_cell_cast(type("tensor<double>(x{},y[5])")));
    TEST_DO(verify_cell_cast(type("tensor<float>(x{},y[5])")));
    TEST_DO(verify_cell_cast(type("tensor<bfloat16>(x{},y[5])")));
    TEST_DO(verify_cell_cast(type("tensor<int8>(x{},y[5])")));
}

TEST("require that actual cell type can be converted to cell type name") {
    EXPECT_EQUAL(value_type::cell_type_to_name(CellType::FLOAT), "float");
    EXPECT_EQUAL(value_type::cell_type_to_name(CellType::DOUBLE), "double");
}

TEST("require that cell type name can be converted to actual cell type") {
    EXPECT_EQUAL(int(value_type::cell_type_from_name("float").value()), int(CellType::FLOAT));
    EXPECT_EQUAL(int(value_type::cell_type_from_name("double").value()), int(CellType::DOUBLE));
    EXPECT_FALSE(value_type::cell_type_from_name("int7").has_value());
}

TEST("require that cell type name recognition is strict") {
    EXPECT_FALSE(value_type::cell_type_from_name("Float").has_value());
    EXPECT_FALSE(value_type::cell_type_from_name(" float").has_value());
    EXPECT_FALSE(value_type::cell_type_from_name("float ").has_value());
    EXPECT_FALSE(value_type::cell_type_from_name("f").has_value());
    EXPECT_FALSE(value_type::cell_type_from_name("").has_value());
}

TEST("require that map type inference works as expected") {
    EXPECT_EQUAL(type("error").map(), type("error"));
    EXPECT_EQUAL(type("double").map(), type("double"));
    EXPECT_EQUAL(type("tensor(x[10])").map(), type("tensor(x[10])"));
    EXPECT_EQUAL(type("tensor<float>(x{})").map(), type("tensor<float>(x{})"));
}

TEST("require that peek type inference works as expected") {
    auto input1 = type("tensor(a[2],b{},c[3],d{},e[5])");
    auto input2 = type("tensor<float>(a[2],b{},c[3],d{},e[5])");
    EXPECT_EQUAL(type("error").peek({}), type("error"));
    EXPECT_EQUAL(type("double").peek({}), type("error"));
    EXPECT_EQUAL(input1.peek({}), type("error"));
    EXPECT_EQUAL(input1.peek({"x"}), type("error"));
    EXPECT_EQUAL(input1.peek({"a", "c", "e"}), type("tensor(b{},d{})"));
    EXPECT_EQUAL(input2.peek({"b", "d"}), type("tensor<float>(a[2],c[3],e[5])"));
    EXPECT_EQUAL(input1.peek({"a", "b", "c", "d", "e"}), type("double"));
    EXPECT_EQUAL(input2.peek({"a", "b", "c", "d", "e"}), type("double"));
}

TEST("require that non-scalar peek preserves cell type") {
    EXPECT_EQUAL(type("tensor(x[3],y[5])").peek({"x"}), type("tensor(y[5])"));
    EXPECT_EQUAL(type("tensor<float>(x[3],y[5])").peek({"x"}), type("tensor<float>(y[5])"));
    EXPECT_EQUAL(type("tensor<bfloat16>(x[3],y[5])").peek({"x"}), type("tensor<bfloat16>(y[5])"));
    EXPECT_EQUAL(type("tensor<int8>(x[3],y[5])").peek({"x"}), type("tensor<int8>(y[5])"));
}

TEST("require that scalar peek is always double") {
    EXPECT_EQUAL(type("tensor(x[3],y[5])").peek({"x", "y"}), type("double"));
    EXPECT_EQUAL(type("tensor<float>(x[3],y[5])").peek({"x", "y"}), type("double"));
    EXPECT_EQUAL(type("tensor<bfloat16>(x[3],y[5])").peek({"x", "y"}), type("double"));
    EXPECT_EQUAL(type("tensor<int8>(x[3],y[5])").peek({"x", "y"}), type("double"));
}

TEST("require that cell alignment can be obtained") {
    EXPECT_EQUAL(CellTypeUtils::alignment(CellType::DOUBLE), alignof(double));
    EXPECT_EQUAL(CellTypeUtils::alignment(CellType::FLOAT), alignof(float));
    EXPECT_EQUAL(CellTypeUtils::alignment(CellType::BFLOAT16), alignof(BFloat16));
    EXPECT_EQUAL(CellTypeUtils::alignment(CellType::INT8), alignof(Int8Float));
}

TEST("require that cell array size can be calculated") {
    EXPECT_EQUAL(CellTypeUtils::mem_size(CellType::DOUBLE, 37), 37 * sizeof(double));
    EXPECT_EQUAL(CellTypeUtils::mem_size(CellType::FLOAT, 37), 37 * sizeof(float));
    EXPECT_EQUAL(CellTypeUtils::mem_size(CellType::BFLOAT16, 37), 37 * sizeof(BFloat16));
    EXPECT_EQUAL(CellTypeUtils::mem_size(CellType::INT8, 37), 37 * sizeof(Int8Float));
}

TEST("require that all cell types can be listed") {
    std::vector<CellType> expect = { CellType::DOUBLE, CellType::FLOAT, CellType::BFLOAT16, CellType::INT8 };
    std::vector<CellType> expect_stable;
    std::vector<CellType> expect_unstable;
    auto list = CellTypeUtils::list_types();
    ASSERT_EQUAL(list.size(), expect.size());
    for (size_t i = 0; i < list.size(); ++i) {
        EXPECT_TRUE(list[i] == expect[i]);
        CellMeta cm(expect[i], false);
        if (cm.decay().eq(cm)) {
            expect_stable.push_back(cm.cell_type);
        } else {
            expect_unstable.push_back(cm.cell_type);
        }
    }
    EXPECT_TRUE(expect_stable == CellTypeUtils::list_stable_types());
    EXPECT_TRUE(expect_unstable == CellTypeUtils::list_unstable_types());
}

TEST_MAIN() { TEST_RUN_ALL(); }
