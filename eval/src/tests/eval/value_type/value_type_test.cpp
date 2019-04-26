// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <ostream>

using namespace vespalib::eval;

const size_t npos = ValueType::Dimension::npos;

TEST("require that ERROR value type can be created") {
    ValueType t = ValueType::error_type();
    EXPECT_TRUE(t.type() == ValueType::Type::ERROR);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

TEST("require that DOUBLE value type can be created") {
    ValueType t = ValueType::double_type();
    EXPECT_TRUE(t.type() == ValueType::Type::DOUBLE);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

TEST("require that TENSOR value type can be created") {
    ValueType t = ValueType::tensor_type({{"x", 10},{"y"}});
    EXPECT_TRUE(t.type() == ValueType::Type::TENSOR);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
}

TEST("require that TENSOR value type sorts dimensions") {
    ValueType t = ValueType::tensor_type({{"x", 10}, {"z", 30}, {"y"}});
    EXPECT_TRUE(t.type() == ValueType::Type::TENSOR);
    ASSERT_EQUAL(t.dimensions().size(), 3u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, npos);
    EXPECT_EQUAL(t.dimensions()[2].name, "z");
    EXPECT_EQUAL(t.dimensions()[2].size, 30u);
}

TEST("require that dimension names can be obtained") {
    EXPECT_EQUAL(ValueType::double_type().dimension_names(),
                 std::vector<vespalib::string>({}));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}, {"x", 30}}).dimension_names(),
                 std::vector<vespalib::string>({"x", "y"}));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}, {"x", 30}, {"z"}}).dimension_names(),
                 std::vector<vespalib::string>({"x", "y", "z"}));
}

TEST("require that dimension index can be obtained") {
    EXPECT_EQUAL(ValueType::error_type().dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(ValueType::double_type().dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(ValueType::tensor_type({}).dimension_index("x"), ValueType::Dimension::npos);
    auto my_type = ValueType::tensor_type({{"y", 10}, {"x"}, {"z", 5}});
    EXPECT_EQUAL(my_type.dimension_index("x"), 0u);
    EXPECT_EQUAL(my_type.dimension_index("y"), 1u);
    EXPECT_EQUAL(my_type.dimension_index("z"), 2u);
    EXPECT_EQUAL(my_type.dimension_index("w"), ValueType::Dimension::npos);
}

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
    TEST_DO(verify_predicates(ValueType::error_type(), true, false, false, false, false));
    TEST_DO(verify_predicates(ValueType::double_type(), false, true, false, false, false));
    TEST_DO(verify_predicates(ValueType::tensor_type({}), false, true, false, false, false));
    TEST_DO(verify_predicates(ValueType::tensor_type({{"x"}}), false, false, true, true, false));
    TEST_DO(verify_predicates(ValueType::tensor_type({{"x"},{"y"}}), false, false, true, true, false));
    TEST_DO(verify_predicates(ValueType::tensor_type({{"x", 5}}), false, false, true, false, true));
    TEST_DO(verify_predicates(ValueType::tensor_type({{"x", 5},{"y", 10}}), false, false, true, false, true));
    TEST_DO(verify_predicates(ValueType::tensor_type({{"x", 5}, {"y"}}), false, false, true, false, false));
}

TEST("require that dimension predicates work as expected") {
    ValueType::Dimension x("x");
    ValueType::Dimension y("y", 10);
    ValueType::Dimension z("z", 0);
    EXPECT_TRUE(x.is_mapped());
    EXPECT_TRUE(!x.is_indexed());
    EXPECT_TRUE(!x.is_bound());
    EXPECT_TRUE(!y.is_mapped());
    EXPECT_TRUE(y.is_indexed());
    EXPECT_TRUE(y.is_bound());
    EXPECT_TRUE(!z.is_mapped());
    EXPECT_TRUE(z.is_indexed());
    EXPECT_TRUE(!z.is_bound());
}

TEST("require that use of unbound dimensions result in error types") {
    EXPECT_TRUE(ValueType::tensor_type({{"x", 0}}).is_error());
}

TEST("require that duplicate dimension names result in error types") {
    EXPECT_TRUE(ValueType::tensor_type({{"x"}, {"x"}}).is_error());
}

TEST("require that removing dimensions from non-tensor types gives error type") {
    EXPECT_TRUE(ValueType::error_type().reduce({"x"}).is_error());
    EXPECT_TRUE(ValueType::double_type().reduce({"x"}).is_error());
}

TEST("require that dimensions can be removed from tensor value types") {
    ValueType type = ValueType::tensor_type({{"x", 10}, {"y", 20}, {"z", 30}});
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 20}, {"z", 30}}), type.reduce({"x"}));
    EXPECT_EQUAL(ValueType::tensor_type({{"x", 10}, {"z", 30}}), type.reduce({"y"}));
    EXPECT_EQUAL(ValueType::tensor_type({{"x", 10}, {"y", 20}}), type.reduce({"z"}));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 20}}),            type.reduce({"x", "z"}));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 20}}),            type.reduce({"z", "x"}));
}

TEST("require that removing an empty set of dimensions means removing them all") {
    EXPECT_EQUAL(ValueType::tensor_type({{"x", 10}, {"y", 20}, {"z", 30}}).reduce({}), ValueType::double_type());
}

TEST("require that removing non-existing dimensions gives error type") {
    EXPECT_TRUE(ValueType::tensor_type({{"y"}}).reduce({"x"}).is_error());
    EXPECT_TRUE(ValueType::tensor_type({{"y", 10}}).reduce({"x"}).is_error());
}

TEST("require that removing all dimensions gives double type") {
    ValueType type = ValueType::tensor_type({{"x", 10}, {"y", 20}, {"z", 30}});
    EXPECT_EQUAL(ValueType::double_type(), type.reduce({"x", "y", "z"}));
}

TEST("require that dimensions can be combined for value types") {
    ValueType tensor_type_xy  = ValueType::tensor_type({{"x"}, {"y"}});
    ValueType tensor_type_yz  = ValueType::tensor_type({{"y"}, {"z"}});
    ValueType tensor_type_xyz = ValueType::tensor_type({{"x"}, {"y"}, {"z"}});
    ValueType tensor_type_y   = ValueType::tensor_type({{"y"}});
    ValueType tensor_type_a10 = ValueType::tensor_type({{"a", 10}});
    ValueType tensor_type_a10xyz = ValueType::tensor_type({{"a", 10}, {"x"}, {"y"}, {"z"}});
    ValueType scalar = ValueType::double_type();
    EXPECT_EQUAL(ValueType::join(scalar, scalar), scalar);
    EXPECT_EQUAL(ValueType::join(tensor_type_xy, tensor_type_yz), tensor_type_xyz);
    EXPECT_EQUAL(ValueType::join(tensor_type_yz, tensor_type_xy), tensor_type_xyz);
    EXPECT_EQUAL(ValueType::join(tensor_type_y, tensor_type_y), tensor_type_y);
    EXPECT_EQUAL(ValueType::join(scalar, tensor_type_y), tensor_type_y);
    EXPECT_EQUAL(ValueType::join(tensor_type_a10, tensor_type_a10), tensor_type_a10);
    EXPECT_EQUAL(ValueType::join(tensor_type_a10, scalar), tensor_type_a10);
    EXPECT_EQUAL(ValueType::join(tensor_type_xyz, tensor_type_a10), tensor_type_a10xyz);
}

void verify_not_combinable(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(ValueType::join(a, b).is_error());
    EXPECT_TRUE(ValueType::join(b, a).is_error());
}

TEST("require that mapped and indexed dimensions are not combinable") {
    verify_not_combinable(ValueType::tensor_type({{"x", 10}}), ValueType::tensor_type({{"x"}}));
}

TEST("require that indexed dimensions of different sizes are not combinable") {
    verify_not_combinable(ValueType::tensor_type({{"x", 10}}), ValueType::tensor_type({{"x", 20}}));
}

TEST("require that error type combined with anything produces error type") {
    verify_not_combinable(ValueType::error_type(), ValueType::error_type());
    verify_not_combinable(ValueType::error_type(), ValueType::double_type());
    verify_not_combinable(ValueType::error_type(), ValueType::tensor_type({{"x"}}));
    verify_not_combinable(ValueType::error_type(), ValueType::tensor_type({{"x", 10}}));
}

TEST("require that value type can make spec") {
    EXPECT_EQUAL("error", ValueType::error_type().to_spec());
    EXPECT_EQUAL("double", ValueType::double_type().to_spec());
    EXPECT_EQUAL("double", ValueType::tensor_type({}).to_spec());
    EXPECT_EQUAL("tensor(x{})", ValueType::tensor_type({{"x"}}).to_spec());
    EXPECT_EQUAL("tensor(y[10])", ValueType::tensor_type({{"y", 10}}).to_spec());
    EXPECT_EQUAL("tensor(x{},y[10],z[5])", ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 5}}).to_spec());
}

TEST("require that value type spec can be parsed") {
    EXPECT_EQUAL(ValueType::double_type(), ValueType::from_spec("double"));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec("tensor"));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec("tensor()"));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}}), ValueType::from_spec("tensor(x{})"));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec("tensor(y[10])"));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 5}}), ValueType::from_spec("tensor(x{},y[10],z[5])"));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec("tensor<double>(y[10])"));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec("tensor<float>(y[10])"));
}

TEST("require that value type spec can be parsed with extra whitespace") {
    EXPECT_EQUAL(ValueType::double_type(), ValueType::from_spec(" double "));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec(" tensor "));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec(" tensor ( ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}}), ValueType::from_spec(" tensor ( x { } ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec(" tensor ( y [ 10 ] ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 5}}),
                 ValueType::from_spec(" tensor ( x { } , y [ 10 ] , z [ 5 ] ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec(" tensor < double > ( y [ 10 ] ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec(" tensor < float > ( y [ 10 ] ) "));
}

TEST("require that malformed value type spec is parsed as error") {
    EXPECT_TRUE(ValueType::from_spec("").is_error());
    EXPECT_TRUE(ValueType::from_spec("  ").is_error());
    EXPECT_TRUE(ValueType::from_spec("error").is_error());
    EXPECT_TRUE(ValueType::from_spec("any").is_error());
    EXPECT_TRUE(ValueType::from_spec("tensor tensor").is_error());
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
      type(value_type::parse_spec(pos, end, after))
{ }
ParseResult::~ParseResult() { }

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

TEST("require that tensor dimensions can be renamed") {
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{})").rename({"x"}, {"y"}),
                 ValueType::from_spec("tensor(y{})"));
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{},y[5])").rename({"x","y"}, {"y","x"}),
                 ValueType::from_spec("tensor(y{},x[5])"));
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{})").rename({"x"}, {"x"}),
                 ValueType::from_spec("tensor(x{})"));
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{})").rename({}, {}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::double_type().rename({}, {}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{},y{})").rename({"x"}, {"y","z"}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{},y{})").rename({"x","y"}, {"z"}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::double_type().rename({"a"}, {"b"}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::error_type().rename({"a"}, {"b"}), ValueType::error_type());
}

TEST("require that types can be concatenated") {
    ValueType error    = ValueType::error_type();
    ValueType scalar   = ValueType::double_type();
    ValueType vx_2     = ValueType::from_spec("tensor(x[2])");
    ValueType vx_m     = ValueType::from_spec("tensor(x{})");
    ValueType vx_3     = ValueType::from_spec("tensor(x[3])");
    ValueType vx_5     = ValueType::from_spec("tensor(x[5])");
    ValueType vy_7     = ValueType::from_spec("tensor(y[7])");
    ValueType mxy_22   = ValueType::from_spec("tensor(x[2],y[2])");
    ValueType mxy_52   = ValueType::from_spec("tensor(x[5],y[2])");
    ValueType mxy_29   = ValueType::from_spec("tensor(x[2],y[9])");
    ValueType cxyz_572 = ValueType::from_spec("tensor(x[5],y[7],z[2])");
    ValueType cxyz_m72 = ValueType::from_spec("tensor(x{},y[7],z[2])");

    EXPECT_EQUAL(ValueType::concat(error,  vx_2,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_2,   error,  "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_m,   vx_2,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_2,   vx_m,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_m,   vx_m,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_m,   scalar, "x"), error);
    EXPECT_EQUAL(ValueType::concat(scalar, vx_m,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_2,   vx_3,   "y"), error);
    EXPECT_EQUAL(ValueType::concat(vy_7,   vx_m,   "z"), cxyz_m72);
    EXPECT_EQUAL(ValueType::concat(scalar, scalar, "x"), vx_2);
    EXPECT_EQUAL(ValueType::concat(vx_2,   scalar, "x"), vx_3);
    EXPECT_EQUAL(ValueType::concat(scalar, vx_2,   "x"), vx_3);
    EXPECT_EQUAL(ValueType::concat(vx_2,   vx_3,   "x"), vx_5);
    EXPECT_EQUAL(ValueType::concat(scalar, vx_2,   "y"), mxy_22);
    EXPECT_EQUAL(ValueType::concat(vx_2, scalar,   "y"), mxy_22);
    EXPECT_EQUAL(ValueType::concat(vx_2,   vx_2,   "y"), mxy_22);
    EXPECT_EQUAL(ValueType::concat(mxy_22, vx_3,   "x"), mxy_52);
    EXPECT_EQUAL(ValueType::concat(vx_3,   mxy_22, "x"), mxy_52);
    EXPECT_EQUAL(ValueType::concat(mxy_22, vy_7,   "y"), mxy_29);
    EXPECT_EQUAL(ValueType::concat(vy_7,   mxy_22, "y"), mxy_29);
    EXPECT_EQUAL(ValueType::concat(vx_5,   vy_7,   "z"), cxyz_572);
}

TEST_MAIN() { TEST_RUN_ALL(); }
