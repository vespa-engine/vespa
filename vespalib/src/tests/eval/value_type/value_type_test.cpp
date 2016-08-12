// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/eval/value_type.h>
#include <vespa/vespalib/eval/value_type_spec.h>
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
    EXPECT_TRUE(ValueType::error_type().remove_dimensions({"x"}).is_error());
    EXPECT_TRUE(ValueType::double_type().remove_dimensions({"x"}).is_error());
}

TEST("require that removing dimensions from abstract maybe-tensor types gives any type") {
    EXPECT_TRUE(ValueType::any_type().remove_dimensions({"x"}).is_any());
    EXPECT_TRUE(ValueType::tensor_type({}).remove_dimensions({"x"}).is_any());
}

TEST("require that dimensions can be removed from tensor value types") {
    ValueType type = ValueType::tensor_type({{"x", 10}, {"y", 20}, {"z", 30}});
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 20}, {"z", 30}}), type.remove_dimensions({"x"}));
    EXPECT_EQUAL(ValueType::tensor_type({{"x", 10}, {"z", 30}}), type.remove_dimensions({"y"}));
    EXPECT_EQUAL(ValueType::tensor_type({{"x", 10}, {"y", 20}}), type.remove_dimensions({"z"}));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 20}}),            type.remove_dimensions({"x", "z"}));
    EXPECT_EQUAL(ValueType::tensor_type({{"y", 20}}),            type.remove_dimensions({"z", "x"}));
}

TEST("require that removing an empty set of dimensions is not allowed") {
    EXPECT_TRUE(ValueType::tensor_type({{"x", 10}, {"y", 20}, {"z", 30}}).remove_dimensions({}).is_error());
}

TEST("require that removing non-existing dimensions gives error type") {
    EXPECT_TRUE(ValueType::tensor_type({{"y"}}).remove_dimensions({"x"}).is_error());
    EXPECT_TRUE(ValueType::tensor_type({{"y", 10}}).remove_dimensions({"x"}).is_error());
}

TEST("require that removing all dimensions gives double type") {
    ValueType type = ValueType::tensor_type({{"x", 10}, {"y", 20}, {"z", 30}});
    EXPECT_EQUAL(ValueType::double_type(), type.remove_dimensions({"x", "y", "z"}));
}

TEST("require that dimensions can be combined for tensor value types") {
    ValueType tensor_type_xy  = ValueType::tensor_type({{"x"}, {"y"}});
    ValueType tensor_type_yz  = ValueType::tensor_type({{"y"}, {"z"}});
    ValueType tensor_type_xyz = ValueType::tensor_type({{"x"}, {"y"}, {"z"}});
    ValueType tensor_type_y   = ValueType::tensor_type({{"y"}});
    EXPECT_EQUAL(tensor_type_xy.add_dimensions_from(tensor_type_yz), tensor_type_xyz);
    EXPECT_EQUAL(tensor_type_yz.add_dimensions_from(tensor_type_xy), tensor_type_xyz);
    EXPECT_EQUAL(tensor_type_xy.keep_dimensions_in(tensor_type_yz), tensor_type_y);
    EXPECT_EQUAL(tensor_type_yz.keep_dimensions_in(tensor_type_xy), tensor_type_y);
    EXPECT_EQUAL(tensor_type_y.add_dimensions_from(tensor_type_y), tensor_type_y);
    EXPECT_EQUAL(tensor_type_y.keep_dimensions_in(tensor_type_y), tensor_type_y);
}

TEST("require that indexed dimensions combine to the minimal dimension size") {
    ValueType tensor_0 = ValueType::tensor_type({{"x", 0}});
    ValueType tensor_10 = ValueType::tensor_type({{"x", 10}});
    ValueType tensor_20 = ValueType::tensor_type({{"x", 20}});
    EXPECT_EQUAL(tensor_10.add_dimensions_from(tensor_0), tensor_0);
    EXPECT_EQUAL(tensor_10.add_dimensions_from(tensor_10), tensor_10);
    EXPECT_EQUAL(tensor_10.add_dimensions_from(tensor_20), tensor_10);
    EXPECT_EQUAL(tensor_10.keep_dimensions_in(tensor_0), tensor_0);
    EXPECT_EQUAL(tensor_10.keep_dimensions_in(tensor_10), tensor_10);
    EXPECT_EQUAL(tensor_10.keep_dimensions_in(tensor_20), tensor_10);
}

void verify_combinable(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(!a.add_dimensions_from(b).is_error());
    EXPECT_TRUE(!b.add_dimensions_from(a).is_error());
    EXPECT_TRUE(!a.keep_dimensions_in(b).is_error());
    EXPECT_TRUE(!b.keep_dimensions_in(a).is_error());
}

void verify_not_combinable(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(a.add_dimensions_from(b).is_error());
    EXPECT_TRUE(b.add_dimensions_from(a).is_error());
    EXPECT_TRUE(a.keep_dimensions_in(b).is_error());
    EXPECT_TRUE(b.keep_dimensions_in(a).is_error());
}

void verify_maybe_combinable(const ValueType &a, const ValueType &b) {
    EXPECT_TRUE(a.add_dimensions_from(b).is_any());
    EXPECT_TRUE(b.add_dimensions_from(a).is_any());
    EXPECT_TRUE(a.keep_dimensions_in(b).is_any());
    EXPECT_TRUE(b.keep_dimensions_in(a).is_any());
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
            if (types[a].is_tensor() && types[b].is_tensor()) {
                verify_combinable(types[a], types[b]);
            } else if (types[a].maybe_tensor() && types[b].maybe_tensor()) {
                verify_maybe_combinable(types[a], types[b]);
            } else {
                verify_not_combinable(types[a], types[b]);
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
    ParseResult(const vespalib::string &spec_in)
        : spec(spec_in),
          pos(spec.data()),
          end(pos + spec.size()),
          after(nullptr),
          type(value_type::parse_spec(pos, end, after)) {}
    bool after_inside() const { return ((after > pos) && (after < end)); }
};

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

TEST_MAIN() { TEST_RUN_ALL(); }
