// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <ostream>

using namespace vespalib::eval;

const size_t npos = ValueType::Dimension::npos;

TEST("require that ANY value type can be created") {
    ValueType t = ValueType::any_type();
    EXPECT_TRUE(t.type() == ValueType::Type::ANY);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

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
    EXPECT_EQUAL(ValueType::any_type().dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(ValueType::double_type().dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQUAL(ValueType::tensor_type({}).dimension_index("x"), ValueType::Dimension::npos);
    auto my_type = ValueType::tensor_type({{"y", 10}, {"x"}, {"z", 0}});
    EXPECT_EQUAL(my_type.dimension_index("x"), 0u);
    EXPECT_EQUAL(my_type.dimension_index("y"), 1u);
    EXPECT_EQUAL(my_type.dimension_index("z"), 2u);
    EXPECT_EQUAL(my_type.dimension_index("w"), ValueType::Dimension::npos);
}

void verify_equal(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(a == b);
    EXPECT_TRUE(b == a);
    EXPECT_FALSE(a != b);
    EXPECT_FALSE(b != a);
}
    
void verify_not_equal(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(a != b);
    EXPECT_TRUE(b != a);
    EXPECT_FALSE(a == b);
    EXPECT_FALSE(b == a);
}

TEST("require that value types can be compared") {
    TEST_DO(verify_equal(ValueType::error_type(), ValueType::error_type()));
    TEST_DO(verify_not_equal(ValueType::error_type(), ValueType::any_type()));
    TEST_DO(verify_not_equal(ValueType::error_type(), ValueType::double_type()));
    TEST_DO(verify_not_equal(ValueType::error_type(), ValueType::tensor_type({})));
    TEST_DO(verify_equal(ValueType::any_type(), ValueType::any_type()));
    TEST_DO(verify_not_equal(ValueType::any_type(), ValueType::double_type()));
    TEST_DO(verify_not_equal(ValueType::any_type(), ValueType::tensor_type({})));
    TEST_DO(verify_equal(ValueType::double_type(), ValueType::double_type()));
    TEST_DO(verify_not_equal(ValueType::double_type(), ValueType::tensor_type({})));
    TEST_DO(verify_equal(ValueType::tensor_type({{"x"}, {"y"}}), ValueType::tensor_type({{"y"}, {"x"}})));
    TEST_DO(verify_not_equal(ValueType::tensor_type({{"x"}, {"y"}}), ValueType::tensor_type({{"x"}, {"y"}, {"z"}})));
    TEST_DO(verify_equal(ValueType::tensor_type({{"x", 10}, {"y", 20}}), ValueType::tensor_type({{"y", 20}, {"x", 10}})));
    TEST_DO(verify_not_equal(ValueType::tensor_type({{"x", 10}, {"y", 20}}), ValueType::tensor_type({{"x", 10}, {"y", 10}})));
    TEST_DO(verify_not_equal(ValueType::tensor_type({{"x", 10}}), ValueType::tensor_type({{"x"}})));
}

void verify_predicates(const ValueType &type,
                       bool expect_any, bool expect_error, bool expect_double, bool expect_tensor,
                       bool expect_maybe_tensor, bool expect_abstract, bool expect_unknown_dimensions)
{
    EXPECT_EQUAL(type.is_any(), expect_any);
    EXPECT_EQUAL(type.is_error(), expect_error);
    EXPECT_EQUAL(type.is_double(), expect_double);
    EXPECT_EQUAL(type.is_tensor(), expect_tensor);
    EXPECT_EQUAL(type.maybe_tensor(), expect_maybe_tensor);
    EXPECT_EQUAL(type.is_abstract(), expect_abstract);
    EXPECT_EQUAL(type.unknown_dimensions(), expect_unknown_dimensions);
}

TEST("require that type-related predicate functions work as expected") {
    TEST_DO(verify_predicates(ValueType::any_type(),
                              true, false, false, false,
                              true, true, true));
    TEST_DO(verify_predicates(ValueType::error_type(),
                              false, true, false, false,
                              false, false, false));
    TEST_DO(verify_predicates(ValueType::double_type(),
                              false, false, true, false,
                              false, false, false));
    TEST_DO(verify_predicates(ValueType::tensor_type({}),
                              false, false, false, true,
                              true, true, true));
    TEST_DO(verify_predicates(ValueType::tensor_type({{"x"}}),
                              false, false, false, true,
                              true, false, false));
    TEST_DO(verify_predicates(ValueType::tensor_type({{"x", 0}}),
                              false, false, false, true,
                              true, true, false));
}

TEST("require that dimension predicates work as expected") {
    ValueType type = ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 0}});
    ASSERT_EQUAL(3u, type.dimensions().size());
    EXPECT_TRUE(type.dimensions()[0].is_mapped());
    EXPECT_TRUE(!type.dimensions()[0].is_indexed());
    EXPECT_TRUE(!type.dimensions()[0].is_bound());
    EXPECT_TRUE(!type.dimensions()[1].is_mapped());
    EXPECT_TRUE(type.dimensions()[1].is_indexed());
    EXPECT_TRUE(type.dimensions()[1].is_bound());
    EXPECT_TRUE(!type.dimensions()[2].is_mapped());
    EXPECT_TRUE(type.dimensions()[2].is_indexed());
    EXPECT_TRUE(!type.dimensions()[2].is_bound());
}

TEST("require that duplicate dimension names result in error types") {
    EXPECT_TRUE(ValueType::tensor_type({{"x"}, {"x"}}).is_error());
}

TEST("require that removing dimensions from non-abstract non-tensor types gives error type") {
    EXPECT_TRUE(ValueType::error_type().reduce({"x"}).is_error());
    EXPECT_TRUE(ValueType::double_type().reduce({"x"}).is_error());
}

TEST("require that removing dimensions from abstract maybe-tensor types gives any type") {
    EXPECT_TRUE(ValueType::any_type().reduce({"x"}).is_any());
    EXPECT_TRUE(ValueType::tensor_type({}).reduce({"x"}).is_any());
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

TEST("require that dimensions can be combined for tensor value types") {
    ValueType tensor_type_xy  = ValueType::tensor_type({{"x"}, {"y"}});
    ValueType tensor_type_yz  = ValueType::tensor_type({{"y"}, {"z"}});
    ValueType tensor_type_xyz = ValueType::tensor_type({{"x"}, {"y"}, {"z"}});
    ValueType tensor_type_y   = ValueType::tensor_type({{"y"}});
    EXPECT_EQUAL(ValueType::join(tensor_type_xy, tensor_type_yz), tensor_type_xyz);
    EXPECT_EQUAL(ValueType::join(tensor_type_yz, tensor_type_xy), tensor_type_xyz);
    EXPECT_EQUAL(ValueType::join(tensor_type_y, tensor_type_y), tensor_type_y);
}

TEST("require that indexed dimensions combine to the minimal dimension size") {
    ValueType tensor_0 = ValueType::tensor_type({{"x", 0}});
    ValueType tensor_10 = ValueType::tensor_type({{"x", 10}});
    ValueType tensor_20 = ValueType::tensor_type({{"x", 20}});
    EXPECT_EQUAL(ValueType::join(tensor_10, tensor_0), tensor_0);
    EXPECT_EQUAL(ValueType::join(tensor_10, tensor_10), tensor_10);
    EXPECT_EQUAL(ValueType::join(tensor_10, tensor_20), tensor_10);
}

void verify_combinable(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(!ValueType::join(a, b).is_error());
    EXPECT_TRUE(!ValueType::join(b, a).is_error());
    EXPECT_TRUE(!ValueType::join(a, b).is_any());
    EXPECT_TRUE(!ValueType::join(b, a).is_any());
}

void verify_not_combinable(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(ValueType::join(a, b).is_error());
    EXPECT_TRUE(ValueType::join(b, a).is_error());
}

void verify_maybe_combinable(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(ValueType::join(a, b).is_any());
    EXPECT_TRUE(ValueType::join(b, a).is_any());
}

TEST("require that mapped and indexed dimensions are not combinable") {
    verify_not_combinable(ValueType::tensor_type({{"x", 10}}), ValueType::tensor_type({{"x"}}));
}

TEST("require that dimension combining is only allowed (yes/no/maybe) for appropriate types") {
    std::vector<ValueType> types = { ValueType::any_type(), ValueType::error_type(), ValueType::double_type(),
                                     ValueType::tensor_type({}), ValueType::tensor_type({{"x"}}) };
    for (size_t a = 0; a < types.size(); ++a) {
        for (size_t b = a; b < types.size(); ++b) {
            TEST_STATE(vespalib::make_string("a='%s', b='%s'", types[a].to_spec().c_str(), types[b].to_spec().c_str()).c_str());
            if (types[a].is_error() || types[b].is_error()) {
                verify_not_combinable(types[a], types[b]);
            } else if (types[a].is_any() || types[b].is_any()) {
                verify_maybe_combinable(types[a], types[b]);
            } else if (types[a].is_double() || types[b].is_double()) {
                verify_combinable(types[a], types[b]);
            } else if (types[a].unknown_dimensions() || types[b].unknown_dimensions()) {
                verify_maybe_combinable(types[a], types[b]);
            } else {
                verify_combinable(types[a], types[b]);
            }
        }
    }
}

TEST("require that value type can make spec") {
    EXPECT_EQUAL("any", ValueType::any_type().to_spec());
    EXPECT_EQUAL("error", ValueType::error_type().to_spec());
    EXPECT_EQUAL("double", ValueType::double_type().to_spec());
    EXPECT_EQUAL("tensor", ValueType::tensor_type({}).to_spec());
    EXPECT_EQUAL("tensor(x{})", ValueType::tensor_type({{"x"}}).to_spec());
    EXPECT_EQUAL("tensor(y[10])", ValueType::tensor_type({{"y", 10}}).to_spec());
    EXPECT_EQUAL("tensor(z[])", ValueType::tensor_type({{"z", 0}}).to_spec());
    EXPECT_EQUAL("tensor(x{},y[10],z[])", ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 0}}).to_spec());
}

TEST("require that value type spec can be parsed") {
    EXPECT_EQUAL(ValueType::any_type(), ValueType::from_spec("any"));
    EXPECT_EQUAL(ValueType::double_type(), ValueType::from_spec("double"));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec("tensor"));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec("tensor()"));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}}), ValueType::from_spec("tensor(x{})"));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec("tensor(y[10])"));
    EXPECT_EQUAL(ValueType::tensor_type({{"z", 0}}), ValueType::from_spec("tensor(z[])"));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 0}}), ValueType::from_spec("tensor(x{},y[10],z[])"));
}

TEST("require that value type spec can be parsed with extra whitespace") {
    EXPECT_EQUAL(ValueType::any_type(), ValueType::from_spec(" any "));
    EXPECT_EQUAL(ValueType::double_type(), ValueType::from_spec(" double "));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec(" tensor "));
    EXPECT_EQUAL(ValueType::tensor_type({}), ValueType::from_spec(" tensor ( ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}}), ValueType::from_spec(" tensor ( x { } ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 10}}), ValueType::from_spec(" tensor ( y [ 10 ] ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"z", 0}}), ValueType::from_spec(" tensor ( z [ ] ) "));
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}, {"y", 10}, {"z", 0}}),
                 ValueType::from_spec(" tensor ( x { } , y [ 10 ] , z [ ] ) "));
}

TEST("require that malformed value type spec is parsed as error") {
    EXPECT_TRUE(ValueType::from_spec("").is_error());
    EXPECT_TRUE(ValueType::from_spec("  ").is_error());
    EXPECT_TRUE(ValueType::from_spec("error").is_error());
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
    ParseResult result("tensor(a[]) , ");
    EXPECT_EQUAL(result.type, ValueType::tensor_type({{"a", 0}}));
    ASSERT_TRUE(result.after_inside());
    EXPECT_EQUAL(*result.after, ',');
}

TEST("require that we can parse an abstract tensor type from a partial string") {
    ParseResult result("tensor , ");
    EXPECT_EQUAL(result.type, ValueType::tensor_type({}));
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

TEST("require that a sparse type must be a tensor with dimensions that all are mapped") {
    EXPECT_TRUE(ValueType::from_spec("tensor(x{})").is_sparse());
    EXPECT_TRUE(ValueType::from_spec("tensor(x{},y{})").is_sparse());
    EXPECT_FALSE(ValueType::from_spec("tensor()").is_sparse());
    EXPECT_FALSE(ValueType::from_spec("tensor(x[])").is_sparse());
    EXPECT_FALSE(ValueType::from_spec("tensor(x{},y[])").is_sparse());
    EXPECT_FALSE(ValueType::from_spec("double").is_sparse());
    EXPECT_FALSE(ValueType::from_spec("any").is_sparse());
    EXPECT_FALSE(ValueType::from_spec("error").is_sparse());
}

TEST("require that a dense type must be a tensor with dimensions that all are indexed") {
    EXPECT_TRUE(ValueType::from_spec("tensor(x[])").is_dense());
    EXPECT_TRUE(ValueType::from_spec("tensor(x[],y[])").is_dense());
    EXPECT_FALSE(ValueType::from_spec("tensor()").is_dense());
    EXPECT_FALSE(ValueType::from_spec("tensor(x{})").is_dense());
    EXPECT_FALSE(ValueType::from_spec("tensor(x[],y{})").is_dense());
    EXPECT_FALSE(ValueType::from_spec("double").is_dense());
    EXPECT_FALSE(ValueType::from_spec("any").is_dense());
    EXPECT_FALSE(ValueType::from_spec("error").is_dense());
}

TEST("require that tensor dimensions can be renamed") {
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{})").rename({"x"}, {"y"}),
                 ValueType::from_spec("tensor(y{})"));
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{},y[])").rename({"x","y"}, {"y","x"}),
                 ValueType::from_spec("tensor(y{},x[])"));
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{})").rename({"x"}, {"x"}),
                 ValueType::from_spec("tensor(x{})"));
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{})").rename({}, {}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::double_type().rename({}, {}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{},y{})").rename({"x"}, {"y","z"}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::from_spec("tensor(x{},y{})").rename({"x","y"}, {"z"}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::tensor_type({}).rename({"x"}, {"y"}), ValueType::any_type());
    EXPECT_EQUAL(ValueType::any_type().rename({"x"}, {"y"}), ValueType::any_type());
    EXPECT_EQUAL(ValueType::double_type().rename({"a"}, {"b"}), ValueType::error_type());
    EXPECT_EQUAL(ValueType::error_type().rename({"a"}, {"b"}), ValueType::error_type());
}

TEST("require that types can be concatenated") {
    ValueType error    = ValueType::error_type();
    ValueType any      = ValueType::any_type();
    ValueType tensor   = ValueType::tensor_type({});
    ValueType scalar   = ValueType::double_type();
    ValueType vx_2     = ValueType::from_spec("tensor(x[2])");
    ValueType vx_m     = ValueType::from_spec("tensor(x{})");
    ValueType vx_3     = ValueType::from_spec("tensor(x[3])");
    ValueType vx_5     = ValueType::from_spec("tensor(x[5])");
    ValueType vx_any   = ValueType::from_spec("tensor(x[])");
    ValueType vy_7     = ValueType::from_spec("tensor(y[7])");
    ValueType mxy_22   = ValueType::from_spec("tensor(x[2],y[2])");
    ValueType mxy_52   = ValueType::from_spec("tensor(x[5],y[2])");
    ValueType mxy_29   = ValueType::from_spec("tensor(x[2],y[9])");
    ValueType cxyz_572 = ValueType::from_spec("tensor(x[5],y[7],z[2])");
    ValueType cxyz_m72 = ValueType::from_spec("tensor(x{},y[7],z[2])");

    EXPECT_EQUAL(ValueType::concat(error,  vx_2,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_2,   error,  "x"), error);
    EXPECT_EQUAL(ValueType::concat(error,  any,    "x"), error);
    EXPECT_EQUAL(ValueType::concat(any,    error,  "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_m,   vx_2,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_2,   vx_m,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_m,   vx_m,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vx_m,   scalar, "x"), error);
    EXPECT_EQUAL(ValueType::concat(scalar, vx_m,   "x"), error);
    EXPECT_EQUAL(ValueType::concat(vy_7,   vx_m,   "z"), cxyz_m72);
    EXPECT_EQUAL(ValueType::concat(tensor, vx_2,   "x"), any);
    EXPECT_EQUAL(ValueType::concat(vx_2,   tensor, "x"), any);
    EXPECT_EQUAL(ValueType::concat(any,    vx_2,   "x"), any);
    EXPECT_EQUAL(ValueType::concat(vx_2,   any,    "x"), any);
    EXPECT_EQUAL(ValueType::concat(any,    tensor, "x"), any);
    EXPECT_EQUAL(ValueType::concat(tensor, any,    "x"), any);
    EXPECT_EQUAL(ValueType::concat(scalar, scalar, "x"), vx_2);
    EXPECT_EQUAL(ValueType::concat(vx_2,   scalar, "x"), vx_3);
    EXPECT_EQUAL(ValueType::concat(scalar, vx_2,   "x"), vx_3);
    EXPECT_EQUAL(ValueType::concat(vx_2,   vx_3,   "x"), vx_5);
    EXPECT_EQUAL(ValueType::concat(vx_2,   vx_any, "x"), vx_any);
    EXPECT_EQUAL(ValueType::concat(vx_any, vx_2,   "x"), vx_any);
    EXPECT_EQUAL(ValueType::concat(scalar, vx_2,   "y"), mxy_22);
    EXPECT_EQUAL(ValueType::concat(vx_2, scalar,   "y"), mxy_22);
    EXPECT_EQUAL(ValueType::concat(vx_2,   vx_3,   "y"), mxy_22);
    EXPECT_EQUAL(ValueType::concat(vx_3,   vx_2,   "y"), mxy_22);
    EXPECT_EQUAL(ValueType::concat(mxy_22, vx_3,   "x"), mxy_52);
    EXPECT_EQUAL(ValueType::concat(vx_3,   mxy_22, "x"), mxy_52);
    EXPECT_EQUAL(ValueType::concat(mxy_22, vy_7,   "y"), mxy_29);
    EXPECT_EQUAL(ValueType::concat(vy_7,   mxy_22, "y"), mxy_29);
    EXPECT_EQUAL(ValueType::concat(vx_5,   vy_7,   "z"), cxyz_572);
}

TEST("require that 'either' gives appropriate type") {
    ValueType error    = ValueType::error_type();
    ValueType any      = ValueType::any_type();
    ValueType tensor   = ValueType::tensor_type({});
    ValueType scalar   = ValueType::double_type();
    ValueType vx_2     = ValueType::from_spec("tensor(x[2])");
    ValueType vx_m     = ValueType::from_spec("tensor(x{})");
    ValueType vx_3     = ValueType::from_spec("tensor(x[3])");
    ValueType vx_any   = ValueType::from_spec("tensor(x[])");
    ValueType vy_2     = ValueType::from_spec("tensor(y[2])");
    ValueType mxy_22   = ValueType::from_spec("tensor(x[2],y[2])");
    ValueType mxy_23   = ValueType::from_spec("tensor(x[2],y[3])");
    ValueType mxy_32   = ValueType::from_spec("tensor(x[3],y[2])");
    ValueType mxy_any2 = ValueType::from_spec("tensor(x[],y[2])");
    ValueType mxy_2any = ValueType::from_spec("tensor(x[2],y[])");

    EXPECT_EQUAL(ValueType::either(vx_2, error), error);
    EXPECT_EQUAL(ValueType::either(error, vx_2), error);
    EXPECT_EQUAL(ValueType::either(vx_2, vx_2), vx_2);
    EXPECT_EQUAL(ValueType::either(vx_2, scalar), any);
    EXPECT_EQUAL(ValueType::either(scalar, vx_2), any);
    EXPECT_EQUAL(ValueType::either(vx_2, mxy_22), tensor);
    EXPECT_EQUAL(ValueType::either(tensor, vx_2), tensor);
    EXPECT_EQUAL(ValueType::either(vx_2, vy_2), tensor);
    EXPECT_EQUAL(ValueType::either(vx_2, vx_m), tensor);
    EXPECT_EQUAL(ValueType::either(vx_2, vx_3), vx_any);
    EXPECT_EQUAL(ValueType::either(mxy_22, mxy_23), mxy_2any);
    EXPECT_EQUAL(ValueType::either(mxy_32, mxy_22), mxy_any2);
}

TEST_MAIN() { TEST_RUN_ALL(); }
