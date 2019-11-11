// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <ostream>

using namespace vespalib::eval;

using CellType = ValueType::CellType;

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
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    EXPECT_TRUE(t.type() == ValueType::Type::ERROR);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

TEST("require that DOUBLE value type can be created") {
    ValueType t = ValueType::double_type();
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    EXPECT_TRUE(t.type() == ValueType::Type::DOUBLE);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

TEST("require that TENSOR value type can be created") {
    ValueType t = ValueType::tensor_type({{"x", 10},{"y"}});
    EXPECT_TRUE(t.type() == ValueType::Type::TENSOR);
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
}

TEST("require that float TENSOR value type can be created") {
    ValueType t = ValueType::tensor_type({{"x", 10},{"y"}}, CellType::FLOAT);
    EXPECT_TRUE(t.type() == ValueType::Type::TENSOR);
    EXPECT_TRUE(t.cell_type() == CellType::FLOAT);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
}

TEST("require that TENSOR value type sorts dimensions") {
    ValueType t = ValueType::tensor_type({{"x", 10}, {"z", 30}, {"y"}});
    EXPECT_TRUE(t.type() == ValueType::Type::TENSOR);
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    ASSERT_EQUAL(t.dimensions().size(), 3u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
    EXPECT_EQUAL(t.dimensions()[2].name, "z");
    EXPECT_EQUAL(t.dimensions()[2].size, 30u);
}

TEST("require that 'tensor<float>()' is normalized to 'double'") {
    ValueType t = ValueType::tensor_type({}, CellType::FLOAT);
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    EXPECT_TRUE(t.type() == ValueType::Type::DOUBLE);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

TEST("require that use of zero-size dimensions result in error types") {
    EXPECT_TRUE(ValueType::tensor_type({{"x", 0}}).is_error());
}

TEST("require that duplicate dimension names result in error types") {
    EXPECT_TRUE(ValueType::tensor_type({{"x"}, {"x"}}).is_error());
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
    TEST_DO(verify_not_equal(ValueType::error_type(), ValueType::tensor_type({{"x"}})));
    TEST_DO(verify_equal(ValueType::double_type(), ValueType::double_type()));
    TEST_DO(verify_equal(ValueType::double_type(), ValueType::tensor_type({})));
    TEST_DO(verify_not_equal(ValueType::double_type(), ValueType::tensor_type({{"x"}})));
    TEST_DO(verify_equal(ValueType::tensor_type({{"x"}, {"y"}}), ValueType::tensor_type({{"y"}, {"x"}})));
    TEST_DO(verify_not_equal(ValueType::tensor_type({{"x"}, {"y"}}), ValueType::tensor_type({{"x"}, {"y"}, {"z"}})));
    TEST_DO(verify_equal(ValueType::tensor_type({{"x", 10}, {"y", 20}}), ValueType::tensor_type({{"y", 20}, {"x", 10}})));
    TEST_DO(verify_not_equal(ValueType::tensor_type({{"x", 10}, {"y", 20}}), ValueType::tensor_type({{"x", 10}, {"y", 10}})));
    TEST_DO(verify_not_equal(ValueType::tensor_type({{"x", 10}}), ValueType::tensor_type({{"x"}})));
    TEST_DO(verify_equal(ValueType::tensor_type({{"x", 10}}, CellType::FLOAT), ValueType::tensor_type({{"x", 10}}, CellType::FLOAT)));
    TEST_DO(verify_not_equal(ValueType::tensor_type({{"x", 10}}, CellType::DOUBLE), ValueType::tensor_type({{"x", 10}}, CellType::FLOAT)));
}

//-----------------------------------------------------------------------------

TEST("require that value type can make spec") {
    EXPECT_EQUAL("error", ValueType::error_type().to_spec());
    EXPECT_EQUAL("double", ValueType::double_type().to_spec());
    EXPECT_EQUAL("double", ValueType::tensor_type({}).to_spec());
    EXPECT_EQUAL("double", ValueType::tensor_type({}, CellType::FLOAT).to_spec());
    EXPECT_EQUAL("tensor(x{})", ValueType::tensor_type({{"x"}}).to_spec());
    EXPECT_EQUAL("tensor(y[10])", ValueType::tensor_type({{"y", 10}}).to_spec());
    EXPECT_EQUAL("tensor(x{},y[10],z[5])", ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 5}}).to_spec());
    EXPECT_EQUAL("tensor<float>(x{})", ValueType::tensor_type({{"x"}}, CellType::FLOAT).to_spec());
    EXPECT_EQUAL("tensor<float>(y[10])", ValueType::tensor_type({{"y", 10}}, CellType::FLOAT).to_spec());
    EXPECT_EQUAL("tensor<float>(x{},y[10],z[5])", ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 5}}, CellType::FLOAT).to_spec());
}

//-----------------------------------------------------------------------------

TEST("require that value type spec can be parsed") {
    EXPECT_EQUAL(ValueType::double_type(), ValueType::from_spec("double"));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec("tensor()"));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}}), ValueType::from_spec("tensor(x{})"));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec("tensor(y[10])"));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 5}}), ValueType::from_spec("tensor(x{},y[10],z[5])"));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec("tensor<double>(y[10])"));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}, CellType::FLOAT), ValueType::from_spec("tensor<float>(y[10])"));
}

TEST("require that value type spec can be parsed with extra whitespace") {
    EXPECT_EQUAL(ValueType::double_type(), ValueType::from_spec(" double "));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec(" tensor ( ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}}), ValueType::from_spec(" tensor ( x { } ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec(" tensor ( y [ 10 ] ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 5}}),
                 ValueType::from_spec(" tensor ( x { } , y [ 10 ] , z [ 5 ] ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec(" tensor < double > ( y [ 10 ] ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}, CellType::FLOAT), ValueType::from_spec(" tensor < float > ( y [ 10 ] ) "));
}

TEST("require that the unsorted dimension list can be obtained when parsing type spec") {
    std::vector<ValueType::Dimension> unsorted;
    auto type = ValueType::from_spec("tensor(y[10],z[5],x{})", unsorted);
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 5}}), type);
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
    EXPECT_TRUE(ValueType::from_spec("tensor<float16>(x[10])").is_error());
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
    EXPECT_EQUAL(result.type, ValueType::tensor_type({{"a", 5}}));
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
}

TEST("require that dimension names can be obtained") {
    EXPECT_EQUAL(type("double").dimension_names(), str_list({}));
    EXPECT_EQUAL(type("tensor(y[30],x[10])").dimension_names(), str_list({"x", "y"}));
    EXPECT_EQUAL(type("tensor<float>(y[10],x[30],z{})").dimension_names(), str_list({"x", "y", "z"}));
}

TEST("require that dimension index can be obtained") {
    EXPECT_EQUAL(type("error").dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(type("double").dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(type("tensor()").dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(type("tensor(y[10],x{},z[5])").dimension_index("x"), 0u);
    EXPECT_EQUAL(type("tensor<float>(y[10],x{},z[5])").dimension_index("y"), 1u);
    EXPECT_EQUAL(type("tensor(y[10],x{},z[5])").dimension_index("z"), 2u);
    EXPECT_EQUAL(type("tensor(y[10],x{},z[5])").dimension_index("w"), ValueType::Dimension::npos);
}

void verify_predicates(const ValueType &type,
                       bool expect_error, bool expect_double, bool expect_tensor,
                       bool expect_sparse, bool expect_dense)
{
    EXPECT_EQUAL(type.is_error(), expect_error);
    EXPECT_EQUAL(type.is_double(), expect_double);
    EXPECT_EQUAL(type.is_tensor(), expect_tensor);
    EXPECT_EQUAL(type.is_sparse(), expect_sparse);
    EXPECT_EQUAL(type.is_dense(), expect_dense);
}

TEST("require that type-related predicate functions work as expected") {
    TEST_DO(verify_predicates(type("error"), true, false, false, false, false));
    TEST_DO(verify_predicates(type("double"), false, true, false, false, false));
    TEST_DO(verify_predicates(type("tensor()"), false, true, false, false, false));
    TEST_DO(verify_predicates(type("tensor(x{})"), false, false, true, true, false));
    TEST_DO(verify_predicates(type("tensor(x{},y{})"), false, false, true, true, false));
    TEST_DO(verify_predicates(type("tensor(x[5])"), false, false, true, false, true));
    TEST_DO(verify_predicates(type("tensor(x[5],y[10])"), false, false, true, false, true));
    TEST_DO(verify_predicates(type("tensor(x[5],y{})"), false, false, true, false, false));
    TEST_DO(verify_predicates(type("tensor<float>(x{})"), false, false, true, true, false));
    TEST_DO(verify_predicates(type("tensor<float>(x[5])"), false, false, true, false, true));
    TEST_DO(verify_predicates(type("tensor<float>(x[5],y{})"), false, false, true, false, false));
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

TEST("require that removing dimensions from non-tensor types gives error type") {
    EXPECT_TRUE(type("error").reduce({"x"}).is_error());
    EXPECT_TRUE(type("double").reduce({"x"}).is_error());
}

TEST("require that dimensions can be removed from tensor value types") {
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({"x"}), type("tensor(y[20],z[30])"));
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({"y"}), type("tensor(x[10],z[30])"));
    EXPECT_EQUAL(type("tensor<float>(x[10],y[20],z[30])").reduce({"z"}), type("tensor<float>(x[10],y[20])"));
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({"x", "z"}), type("tensor(y[20])"));
    EXPECT_EQUAL(type("tensor<float>(x[10],y[20],z[30])").reduce({"z", "x"}), type("tensor<float>(y[20])"));
}

TEST("require that removing an empty set of dimensions means removing them all") {
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({}), type("double"));
    EXPECT_EQUAL(type("tensor<float>(x[10],y[20],z[30])").reduce({}), type("double"));
}

TEST("require that removing non-existing dimensions gives error type") {
    EXPECT_TRUE(type("tensor(y{})").reduce({"x"}).is_error());
    EXPECT_TRUE(type("tensor<float>(y[10])").reduce({"x"}).is_error());
}

TEST("require that removing all dimensions gives double type") {
    EXPECT_EQUAL(type("tensor(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
    EXPECT_EQUAL(type("tensor<float>(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
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
    TEST_DO(verify_join(type("tensor(x{})"), type("tensor<float>(y{})"), type("tensor(x{},y{})")));
    TEST_DO(verify_join(type("tensor<float>(x{})"), type("tensor<float>(y{})"), type("tensor<float>(x{},y{})")));
    TEST_DO(verify_join(type("tensor<float>(x{})"), type("double"), type("tensor<float>(x{})")));
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

void verify_concat(const ValueType &a, const ValueType b, const vespalib::string &dim, const ValueType &res) {
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
    TEST_DO(verify_concat(type("tensor<float>(x[3])"), type("tensor(x[2])"), "x", type("tensor(x[5])")));
    TEST_DO(verify_concat(type("tensor<float>(x[3])"), type("tensor<float>(x[2])"), "x", type("tensor<float>(x[5])")));
    TEST_DO(verify_concat(type("tensor<float>(x[3])"), type("double"), "x", type("tensor<float>(x[4])")));
}

TEST_MAIN() { TEST_RUN_ALL(); }
