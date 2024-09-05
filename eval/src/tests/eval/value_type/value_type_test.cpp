// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/eval/eval/int8float.h>
#include <vespa/vespalib/util/bfloat16.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cassert>
#include <ostream>

using vespalib::BFloat16;
using namespace vespalib::eval;

const size_t npos = ValueType::Dimension::npos;

ValueType type(const std::string &type_str) {
    ValueType ret = ValueType::from_spec(type_str);
    assert(!ret.is_error() || (type_str == "error"));
    return ret;
}

std::vector<std::string> str_list(const std::vector<std::string> &list) {
    return list;
}

//-----------------------------------------------------------------------------

TEST(ValueTypeTest, require_that_error_value_type_can_be_created)
{
    ValueType t = ValueType::error_type();
    EXPECT_TRUE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    EXPECT_EQ(t.dimensions().size(), 0u);
}

TEST(ValueTypeTest, require_that_double_value_type_can_be_created)
{
    ValueType t = ValueType::double_type();
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    EXPECT_EQ(t.dimensions().size(), 0u);
}

TEST(ValueTypeTest, require_that_TENSOR_value_type_can_be_created)
{
    ValueType t = ValueType::make_type(CellType::DOUBLE, {{"x", 10},{"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    ASSERT_EQ(t.dimensions().size(), 2u);
    EXPECT_EQ(t.dimensions()[0].name, "x");
    EXPECT_EQ(t.dimensions()[0].size, 10u);
    EXPECT_EQ(t.dimensions()[1].name, "y");
    EXPECT_EQ(t.dimensions()[1].size, npos);
}

TEST(ValueTypeTest, require_that_float_TENSOR_value_type_can_be_created)
{
    ValueType t = ValueType::make_type(CellType::FLOAT, {{"x", 10},{"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::FLOAT);
    ASSERT_EQ(t.dimensions().size(), 2u);
    EXPECT_EQ(t.dimensions()[0].name, "x");
    EXPECT_EQ(t.dimensions()[0].size, 10u);
    EXPECT_EQ(t.dimensions()[1].name, "y");
    EXPECT_EQ(t.dimensions()[1].size, npos);
}

TEST(ValueTypeTest, require_that_bfloat16_TENSOR_value_type_can_be_created)
{
    ValueType t = ValueType::make_type(CellType::BFLOAT16, {{"x", 10},{"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::BFLOAT16);
    ASSERT_EQ(t.dimensions().size(), 2u);
    EXPECT_EQ(t.dimensions()[0].name, "x");
    EXPECT_EQ(t.dimensions()[0].size, 10u);
    EXPECT_EQ(t.dimensions()[1].name, "y");
    EXPECT_EQ(t.dimensions()[1].size, npos);
}

TEST(ValueTypeTest, require_that_int8_TENSOR_value_type_can_be_created)
{
    ValueType t = ValueType::make_type(CellType::INT8, {{"x", 10},{"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::INT8);
    ASSERT_EQ(t.dimensions().size(), 2u);
    EXPECT_EQ(t.dimensions()[0].name, "x");
    EXPECT_EQ(t.dimensions()[0].size, 10u);
    EXPECT_EQ(t.dimensions()[1].name, "y");
    EXPECT_EQ(t.dimensions()[1].size, npos);
}

TEST(ValueTypeTest, require_that_TENSOR_value_type_sorts_dimensions)
{
    ValueType t = ValueType::make_type(CellType::DOUBLE, {{"x", 10}, {"z", 30}, {"y"}});
    EXPECT_FALSE(t.is_error());
    EXPECT_TRUE(t.cell_type() == CellType::DOUBLE);
    ASSERT_EQ(t.dimensions().size(), 3u);
    EXPECT_EQ(t.dimensions()[0].name, "x");
    EXPECT_EQ(t.dimensions()[0].size, 10u);
    EXPECT_EQ(t.dimensions()[1].name, "y");
    EXPECT_EQ(t.dimensions()[1].size, npos);
    EXPECT_EQ(t.dimensions()[2].name, "z");
    EXPECT_EQ(t.dimensions()[2].size, 30u);
}

TEST(ValueTypeTest, require_that_non_double_scalar_values_are_not_allowed)
{
    EXPECT_TRUE(ValueType::make_type(CellType::FLOAT, {}).is_error());
    EXPECT_TRUE(ValueType::make_type(CellType::BFLOAT16, {}).is_error());
    EXPECT_TRUE(ValueType::make_type(CellType::INT8, {}).is_error());
}

TEST(ValueTypeTest, require_that_use_of_zero_size_dimensions_result_in_error_types)
{
    EXPECT_TRUE(ValueType::make_type(CellType::DOUBLE, {{"x", 0}}).is_error());
}

TEST(ValueTypeTest, require_that_duplicate_dimension_names_result_in_error_types)
{
    EXPECT_TRUE(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"x"}}).is_error());
}

//-----------------------------------------------------------------------------

void verify_equal(const ValueType &a, const ValueType &b) {
    SCOPED_TRACE(a.to_spec() + "," + b.to_spec());
    EXPECT_EQ(a, b);
    EXPECT_EQ(b, a);
    EXPECT_FALSE(a != b);
    EXPECT_FALSE(b != a);
    EXPECT_EQ(a, ValueType::either(a, b));
    EXPECT_EQ(a, ValueType::either(b, a));
}

void verify_not_equal(const ValueType &a, const ValueType &b) {
    SCOPED_TRACE(a.to_spec() + "," + b.to_spec());
    EXPECT_TRUE(a != b);
    EXPECT_TRUE(b != a);
    EXPECT_FALSE(a == b);
    EXPECT_FALSE(b == a);
    EXPECT_TRUE(ValueType::either(a, b).is_error());
    EXPECT_TRUE(ValueType::either(b, a).is_error());
}

TEST(ValueTypeTest, require_that_value_types_can_be_compared)
{
    verify_equal(ValueType::error_type(), ValueType::error_type());
    verify_not_equal(ValueType::error_type(), ValueType::double_type());
    verify_not_equal(ValueType::error_type(), ValueType::make_type(CellType::DOUBLE, {{"x"}}));
    verify_equal(ValueType::double_type(), ValueType::double_type());
    verify_equal(ValueType::double_type(), ValueType::make_type(CellType::DOUBLE, {}));
    verify_not_equal(ValueType::double_type(), ValueType::make_type(CellType::DOUBLE, {{"x"}}));
    verify_equal(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y"}}), ValueType::make_type(CellType::DOUBLE, {{"y"}, {"x"}}));
    verify_not_equal(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y"}}), ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y"}, {"z"}}));
    verify_equal(ValueType::make_type(CellType::DOUBLE, {{"x", 10}, {"y", 20}}), ValueType::make_type(CellType::DOUBLE, {{"y", 20}, {"x", 10}}));
    verify_not_equal(ValueType::make_type(CellType::DOUBLE, {{"x", 10}, {"y", 20}}), ValueType::make_type(CellType::DOUBLE, {{"x", 10}, {"y", 10}}));
    verify_not_equal(ValueType::make_type(CellType::DOUBLE, {{"x", 10}}), ValueType::make_type(CellType::DOUBLE, {{"x"}}));
    verify_equal(ValueType::make_type(CellType::FLOAT, {{"x", 10}}), ValueType::make_type(CellType::FLOAT, {{"x", 10}}));
    verify_equal(ValueType::make_type(CellType::BFLOAT16, {{"x", 10}}), ValueType::make_type(CellType::BFLOAT16, {{"x", 10}}));
    verify_equal(ValueType::make_type(CellType::INT8, {{"x", 10}}), ValueType::make_type(CellType::INT8, {{"x", 10}}));
    verify_not_equal(ValueType::make_type(CellType::DOUBLE, {{"x", 10}}), ValueType::make_type(CellType::FLOAT, {{"x", 10}}));
    verify_not_equal(ValueType::make_type(CellType::FLOAT, {{"x", 10}}), ValueType::make_type(CellType::BFLOAT16, {{"x", 10}}));
    verify_not_equal(ValueType::make_type(CellType::FLOAT, {{"x", 10}}), ValueType::make_type(CellType::INT8, {{"x", 10}}));
    verify_not_equal(ValueType::make_type(CellType::BFLOAT16, {{"x", 10}}), ValueType::make_type(CellType::INT8, {{"x", 10}}));
}

//-----------------------------------------------------------------------------

TEST(ValueTypeTest, require_that_value_type_can_make_spec)
{
    EXPECT_EQ("error", ValueType::error_type().to_spec());
    EXPECT_EQ("double", ValueType::double_type().to_spec());
    EXPECT_EQ("error", ValueType::make_type(CellType::FLOAT, {}).to_spec());
    EXPECT_EQ("error", ValueType::make_type(CellType::BFLOAT16, {}).to_spec());
    EXPECT_EQ("error", ValueType::make_type(CellType::INT8, {}).to_spec());
    EXPECT_EQ("double", ValueType::make_type(CellType::DOUBLE, {}).to_spec());
    EXPECT_EQ("tensor(x{})", ValueType::make_type(CellType::DOUBLE, {{"x"}}).to_spec());
    EXPECT_EQ("tensor(y[10])", ValueType::make_type(CellType::DOUBLE, {{"y", 10}}).to_spec());
    EXPECT_EQ("tensor(x{},y[10],z[5])", ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y", 10}, {"z", 5}}).to_spec());
    EXPECT_EQ("tensor<float>(x{})", ValueType::make_type(CellType::FLOAT, {{"x"}}).to_spec());
    EXPECT_EQ("tensor<float>(y[10])", ValueType::make_type(CellType::FLOAT, {{"y", 10}}).to_spec());
    EXPECT_EQ("tensor<float>(x{},y[10],z[5])", ValueType::make_type(CellType::FLOAT, {{"x"}, {"y", 10}, {"z", 5}}).to_spec());
    EXPECT_EQ("tensor<bfloat16>(x{})", ValueType::make_type(CellType::BFLOAT16, {{"x"}}).to_spec());
    EXPECT_EQ("tensor<bfloat16>(y[10])", ValueType::make_type(CellType::BFLOAT16, {{"y", 10}}).to_spec());
    EXPECT_EQ("tensor<bfloat16>(x{},y[10],z[5])", ValueType::make_type(CellType::BFLOAT16, {{"x"}, {"y", 10}, {"z", 5}}).to_spec());
    EXPECT_EQ("tensor<int8>(x{})", ValueType::make_type(CellType::INT8, {{"x"}}).to_spec());
    EXPECT_EQ("tensor<int8>(y[10])", ValueType::make_type(CellType::INT8, {{"y", 10}}).to_spec());
    EXPECT_EQ("tensor<int8>(x{},y[10],z[5])", ValueType::make_type(CellType::INT8, {{"x"}, {"y", 10}, {"z", 5}}).to_spec());
}

//-----------------------------------------------------------------------------

TEST(ValueTypeTest, require_that_value_type_spec_can_be_parsed)
{
    EXPECT_EQ(ValueType::double_type(), type("double"));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {}), type("tensor()"));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {}), type("tensor<double>()"));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {{"x"}}), type("tensor(x{})"));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {{"y", 10}}), type("tensor(y[10])"));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y", 10}, {"z", 5}}), type("tensor(x{},y[10],z[5])"));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {{"y", 10}}), type("tensor<double>(y[10])"));
    EXPECT_EQ(ValueType::make_type(CellType::FLOAT, {{"y", 10}}), type("tensor<float>(y[10])"));
    EXPECT_EQ(ValueType::make_type(CellType::BFLOAT16, {{"y", 10}}), type("tensor<bfloat16>(y[10])"));
    EXPECT_EQ(ValueType::make_type(CellType::INT8, {{"y", 10}}), type("tensor<int8>(y[10])"));
}

TEST(ValueTypeTest, require_that_value_type_spec_can_be_parsed_with_extra_whitespace)
{
    EXPECT_EQ(ValueType::double_type(), type(" double "));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {}), type(" tensor ( ) "));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {}), type(" tensor < double > ( ) "));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {{"x"}}), type(" tensor ( x { } ) "));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {{"y", 10}}), type(" tensor ( y [ 10 ] ) "));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y", 10}, {"z", 5}}),
                 type(" tensor ( x { } , y [ 10 ] , z [ 5 ] ) "));
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {{"y", 10}}), type(" tensor < double > ( y [ 10 ] ) "));
    EXPECT_EQ(ValueType::make_type(CellType::FLOAT, {{"y", 10}}), type(" tensor < float > ( y [ 10 ] ) "));
}

TEST(ValueTypeTest, require_that_the_unsorted_dimension_list_can_be_obtained_when_parsing_type_spec)
{
    std::vector<ValueType::Dimension> unsorted;
    auto type = ValueType::from_spec("tensor(y[10],z[5],x{})", unsorted);
    EXPECT_EQ(ValueType::make_type(CellType::DOUBLE, {{"x"}, {"y", 10}, {"z", 5}}), type);
    ASSERT_EQ(unsorted.size(), 3u);
    EXPECT_EQ(unsorted[0].name, "y");
    EXPECT_EQ(unsorted[0].size, 10u);
    EXPECT_EQ(unsorted[1].name, "z");
    EXPECT_EQ(unsorted[1].size, 5u);
    EXPECT_EQ(unsorted[2].name, "x");
    EXPECT_EQ(unsorted[2].size, npos);
}

TEST(ValueTypeTest, require_that_the_unsorted_dimension_list_can_be_obtained_also_when_the_type_spec_is_invalid)
{
    std::vector<ValueType::Dimension> unsorted;
    auto type = ValueType::from_spec("tensor(x[10],x[5])...", unsorted);
    EXPECT_TRUE(type.is_error());
    ASSERT_EQ(unsorted.size(), 2u);
    EXPECT_EQ(unsorted[0].name, "x");
    EXPECT_EQ(unsorted[0].size, 10u);
    EXPECT_EQ(unsorted[1].name, "x");
    EXPECT_EQ(unsorted[1].size, 5u);
}

TEST(ValueTypeTest, require_that_the_unsorted_dimension_list_can_not_be_obtained_if_the_parse_itself_fails)
{
    std::vector<ValueType::Dimension> unsorted;
    auto type = ValueType::from_spec("tensor(x[10],x[5]", unsorted);
    EXPECT_TRUE(type.is_error());
    EXPECT_EQ(unsorted.size(), 0u);
}

TEST(ValueTypeTest, require_that_malformed_value_type_spec_is_parsed_as_error)
{
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
    std::string spec;
    const char *pos;
    const char *end;
    const char *after;
    ValueType type;
    ParseResult(const std::string &spec_in);
    ~ParseResult();
    bool after_inside() const { return ((after > pos) && (after < end)); }
};

ParseResult::ParseResult(const std::string &spec_in)
    : spec(spec_in),
      pos(spec.data()),
      end(pos + spec.size()),
      after(nullptr),
      type(value_type::parse_spec(pos, end, after)) {}
ParseResult::~ParseResult() = default;

TEST(ValueTypeTest, require_that_we_can_parse_a_partial_string_into_a_type_with_the_low_level_API)
{
    ParseResult result("tensor(a[5]) , ");
    EXPECT_EQ(result.type, ValueType::make_type(CellType::DOUBLE, {{"a", 5}}));
    ASSERT_TRUE(result.after_inside());
    EXPECT_EQ(*result.after, ',');
}

TEST(ValueTypeTest, require_that_error_is_the_valid_representation_of_the_error_type)
{
    ParseResult valid(" error ");
    ParseResult invalid(" fubar ");
    EXPECT_EQ(valid.type, ValueType::error_type());
    EXPECT_TRUE(valid.after == valid.end); // parse ok
    EXPECT_EQ(invalid.type, ValueType::error_type());
    EXPECT_TRUE(invalid.after == nullptr); // parse not ok
}

//-----------------------------------------------------------------------------

TEST(ValueTypeTest, require_that_value_types_preserve_cell_type)
{
    EXPECT_TRUE(type("tensor(x[10])").cell_type() == CellType::DOUBLE);
    EXPECT_TRUE(type("tensor<double>(x[10])").cell_type() == CellType::DOUBLE);
    EXPECT_TRUE(type("tensor<float>(x[10])").cell_type() == CellType::FLOAT);
    EXPECT_TRUE(type("tensor<bfloat16>(x[10])").cell_type() == CellType::BFLOAT16);
    EXPECT_TRUE(type("tensor<int8>(x[10])").cell_type() == CellType::INT8);
}

TEST(ValueTypeTest, require_that_dimension_names_can_be_obtained)
{
    EXPECT_EQ(type("double").dimension_names(), str_list({}));
    EXPECT_EQ(type("tensor(y[30],x[10])").dimension_names(), str_list({"x", "y"}));
    EXPECT_EQ(type("tensor<float>(y[10],x[30],z{})").dimension_names(), str_list({"x", "y", "z"}));
    EXPECT_EQ(type("tensor<bfloat16>(y[10],x[30],z{})").dimension_names(), str_list({"x", "y", "z"}));
    EXPECT_EQ(type("tensor<int8>(y[10],x[30],z{})").dimension_names(), str_list({"x", "y", "z"}));
}

TEST(ValueTypeTest, require_that_nontrivial_indexed_dimensions_can_be_obtained)
{
    auto my_check = [](const auto &list)
                    {
                        ASSERT_EQ(list.size(), 1u);
                        EXPECT_EQ(list[0].name, "x");
                        EXPECT_EQ(list[0].size, 10u);
                    };
    EXPECT_TRUE(type("double").nontrivial_indexed_dimensions().empty());
    my_check(type("tensor(x[10],y{})").nontrivial_indexed_dimensions());
    my_check(type("tensor(a[1],b[1],x[10],y{},z[1])").nontrivial_indexed_dimensions());
}

TEST(ValueTypeTest, require_that_indexed_dimensions_can_be_obtained)
{
    auto my_check = [](const auto &list, size_t exp_size)
    {
        ASSERT_EQ(list.size(), 1u);
        EXPECT_EQ(list[0].name, "x");
        EXPECT_EQ(list[0].size, exp_size);
    };
    EXPECT_TRUE(type("double").indexed_dimensions().empty());
    my_check(type("tensor(x[10],y{})").indexed_dimensions(), 10);
    my_check(type("tensor(y{},x[1])").indexed_dimensions(), 1);
}

TEST(ValueTypeTest, require_that_mapped_dimensions_can_be_obtained)
{
    auto my_check = [](const auto &list)
                    {
                        ASSERT_EQ(list.size(), 1u);
                        EXPECT_EQ(list[0].name, "x");
                        EXPECT_TRUE(list[0].is_mapped());
                    };
    EXPECT_TRUE(type("double").mapped_dimensions().empty());
    my_check(type("tensor(x{},y[10])").mapped_dimensions());
    my_check(type("tensor(a[1],b[1],x{},y[10],z[1])").mapped_dimensions());
}

TEST(ValueTypeTest, require_that_mapped_dimensions_can_be_stripped)
{
    EXPECT_EQ(type("error").strip_mapped_dimensions(), type("error"));
    EXPECT_EQ(type("double").strip_mapped_dimensions(), type("double"));
    EXPECT_EQ(type("tensor<float>(x{})").strip_mapped_dimensions(), type("double"));
    EXPECT_EQ(type("tensor<float>(x[10])").strip_mapped_dimensions(), type("tensor<float>(x[10])"));
    EXPECT_EQ(type("tensor<float>(a[1],b{},c[2],d{},e[3],f{})").strip_mapped_dimensions(), type("tensor<float>(a[1],c[2],e[3])"));
}

TEST(ValueTypeTest, require_that_indexed_dimensions_can_be_stripped)
{
    EXPECT_EQ(type("error").strip_indexed_dimensions(), type("error"));
    EXPECT_EQ(type("double").strip_indexed_dimensions(), type("double"));
    EXPECT_EQ(type("tensor<float>(x{})").strip_indexed_dimensions(), type("tensor<float>(x{})"));
    EXPECT_EQ(type("tensor<float>(x[10])").strip_indexed_dimensions(), type("double"));
    EXPECT_EQ(type("tensor<float>(a[1],b{},c[2],d{},e[3],f{})").strip_indexed_dimensions(), type("tensor<float>(b{},d{},f{})"));
}

TEST(ValueTypeTest, require_that_value_types_can_be_wrapped_inside_each_other)
{
    EXPECT_EQ(type("error").wrap(type("error")), type("error"));
    EXPECT_EQ(type("double").wrap(type("error")), type("error"));
    EXPECT_EQ(type("error").wrap(type("double")), type("error"));
    EXPECT_EQ(type("double").wrap(type("double")), type("double"));
    EXPECT_EQ(type("tensor<int8>(x{})").wrap(type("tensor<int8>(y[10])")), type("tensor<int8>(x{},y[10])"));
    EXPECT_EQ(type("tensor<int8>(a{},c{})").wrap(type("tensor<int8>(b[10],d[5])")), type("tensor<int8>(a{},b[10],c{},d[5])"));
    EXPECT_EQ(type("tensor<int8>(x{})").wrap(type("tensor<int8>(x[10])")), type("error")); // dimension name conflict
    EXPECT_EQ(type("tensor<int8>(x{},z[2])").wrap(type("tensor<int8>(y[10])")), type("error")); // outer cannot have indexed dimensions
    EXPECT_EQ(type("tensor<int8>(x{})").wrap(type("tensor<int8>(y[10],z{})")), type("error")); // inner cannot have mapped dimensions
    EXPECT_EQ(type("double").wrap(type("tensor<int8>(y[10])")), type("tensor<int8>(y[10])")); // NB: no decay
    EXPECT_EQ(type("tensor<int8>(x{})").wrap(type("double")), type("tensor<float>(x{})")); // NB: decay
}

TEST(ValueTypeTest, require_that_dimension_index_can_be_obtained)
{
    EXPECT_EQ(type("error").dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQ(type("double").dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQ(type("tensor()").dimension_index("x"), ValueType::Dimension::npos);
    EXPECT_EQ(type("tensor(y[10],x{},z[5])").dimension_index("x"), 0u);
    EXPECT_EQ(type("tensor<float>(y[10],x{},z[5])").dimension_index("y"), 1u);
    EXPECT_EQ(type("tensor<bfloat16>(y[10],x{},z[5])").dimension_index("y"), 1u);
    EXPECT_EQ(type("tensor<int8>(y[10],x{},z[5])").dimension_index("y"), 1u);
    EXPECT_EQ(type("tensor(y[10],x{},z[5])").dimension_index("z"), 2u);
    EXPECT_EQ(type("tensor(y[10],x{},z[5])").dimension_index("w"), ValueType::Dimension::npos);
}

TEST(ValueTypeTest, require_that_dimension_stride_can_be_calculated)
{
    EXPECT_EQ(type("error").stride_of("x"), 0u);
    EXPECT_EQ(type("double").stride_of("x"), 0u);
    EXPECT_EQ(type("tensor()").stride_of("x"), 0u);
    EXPECT_EQ(type("tensor(x{})").stride_of("x"), 0u);
    EXPECT_EQ(type("tensor(x[10])").stride_of("x"), 1u);
    EXPECT_EQ(type("tensor(x[10])").stride_of("y"), 0u);
    EXPECT_EQ(type("tensor(x[10],y[5])").stride_of("x"), 5u);
    EXPECT_EQ(type("tensor(x[10],y[5],z[3])").stride_of("x"), 15u);
    EXPECT_EQ(type("tensor(x[10],y[5],z[3])").stride_of("y"), 3u);
    EXPECT_EQ(type("tensor(x[10],y[5],z[3])").stride_of("z"), 1u);
    EXPECT_EQ(type("tensor(x[10],y{},z[3])").stride_of("x"), 3u);
}

void verify_predicates(const ValueType &type,
                       bool expect_error, bool expect_double, bool expect_tensor,
                       bool expect_sparse, bool expect_dense, bool expect_mixed)
{
    SCOPED_TRACE(type.to_spec());
    EXPECT_EQ(type.is_error(), expect_error);
    EXPECT_EQ(type.is_double(), expect_double);
    EXPECT_EQ(type.has_dimensions(), expect_tensor);
    EXPECT_EQ(type.is_sparse(), expect_sparse);
    EXPECT_EQ(type.is_dense(), expect_dense);
    EXPECT_EQ(type.is_mixed(), expect_mixed);
}

TEST(ValueTypeTest, require_that_type_related_predicate_functions_work_as_expected)
{
    verify_predicates(type("error"), true, false, false, false, false, false);
    verify_predicates(type("double"), false, true, false, false, false, false);
    verify_predicates(type("tensor()"), false, true, false, false, false, false);
    verify_predicates(type("tensor(x{})"), false, false, true, true, false, false);
    verify_predicates(type("tensor(x{},y{})"), false, false, true, true, false, false);
    verify_predicates(type("tensor(x[5])"), false, false, true, false, true, false);
    verify_predicates(type("tensor(x[5],y[10])"), false, false, true, false, true, false);
    verify_predicates(type("tensor(x[5],y{})"), false, false, true, false, false, true);
    verify_predicates(type("tensor<float>(x{})"), false, false, true, true, false, false);
    verify_predicates(type("tensor<float>(x[5])"), false, false, true, false, true, false);
    verify_predicates(type("tensor<float>(x[5],y{})"), false, false, true, false, false, true);
    verify_predicates(type("tensor<bfloat16>(x{})"), false, false, true, true, false, false);
    verify_predicates(type("tensor<bfloat16>(x[5])"), false, false, true, false, true, false);
    verify_predicates(type("tensor<bfloat16>(x[5],y{})"), false, false, true, false, false, true);
    verify_predicates(type("tensor<int8>(x{})"), false, false, true, true, false, false);
    verify_predicates(type("tensor<int8>(x[5])"), false, false, true, false, true, false);
    verify_predicates(type("tensor<int8>(x[5],y{})"), false, false, true, false, false, true);
}

TEST(ValueTypeTest, require_that_mapped_and_indexed_dimensions_can_be_counted)
{
    EXPECT_EQ(type("double").count_mapped_dimensions(), 0u);
    EXPECT_EQ(type("double").count_indexed_dimensions(), 0u);
    EXPECT_EQ(type("tensor(x[5],y[5])").count_mapped_dimensions(), 0u);
    EXPECT_EQ(type("tensor(x[5],y[5])").count_indexed_dimensions(), 2u);
    EXPECT_EQ(type("tensor(x{},y[5])").count_mapped_dimensions(), 1u);
    EXPECT_EQ(type("tensor(x{},y[5])").count_indexed_dimensions(), 1u);
    EXPECT_EQ(type("tensor(x[1],y{})").count_mapped_dimensions(), 1u);
    EXPECT_EQ(type("tensor(x[1],y{})").count_indexed_dimensions(), 1u);
    EXPECT_EQ(type("tensor(x{},y{})").count_mapped_dimensions(), 2u);
    EXPECT_EQ(type("tensor(x{},y{})").count_indexed_dimensions(), 0u);
}

TEST(ValueTypeTest, require_that_dense_subspace_size_calculation_works_as_expected)
{
    EXPECT_EQ(type("error").dense_subspace_size(), 1u);
    EXPECT_EQ(type("double").dense_subspace_size(), 1u);
    EXPECT_EQ(type("tensor()").dense_subspace_size(), 1u);
    EXPECT_EQ(type("tensor(x{})").dense_subspace_size(), 1u);
    EXPECT_EQ(type("tensor(x{},y{})").dense_subspace_size(), 1u);
    EXPECT_EQ(type("tensor(x[5])").dense_subspace_size(), 5u);
    EXPECT_EQ(type("tensor(x[5],y[10])").dense_subspace_size(), 50u);
    EXPECT_EQ(type("tensor(x[5],y{})").dense_subspace_size(), 5u);
    EXPECT_EQ(type("tensor<float>(x{})").dense_subspace_size(), 1u);
    EXPECT_EQ(type("tensor<float>(x[5])").dense_subspace_size(), 5u);
    EXPECT_EQ(type("tensor<float>(x[5],y{})").dense_subspace_size(), 5u);
    EXPECT_EQ(type("tensor<bfloat16>(x{})").dense_subspace_size(), 1u);
    EXPECT_EQ(type("tensor<bfloat16>(x[5])").dense_subspace_size(), 5u);
    EXPECT_EQ(type("tensor<bfloat16>(x[5],y{})").dense_subspace_size(), 5u);
    EXPECT_EQ(type("tensor<int8>(x{})").dense_subspace_size(), 1u);
    EXPECT_EQ(type("tensor<int8>(x[5])").dense_subspace_size(), 5u);
    EXPECT_EQ(type("tensor<int8>(x[5],y{})").dense_subspace_size(), 5u);
}

TEST(ValueTypeTest, require_that_dimension_predicates_work_as_expected)
{
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

TEST(ValueTypeTest, require_that_value_type_map_decays_cell_type)
{
    EXPECT_EQ(type("tensor(x[10])").map(), type("tensor(x[10])"));
    EXPECT_EQ(type("tensor<float>(x[10])").map(), type("tensor<float>(x[10])"));
    EXPECT_EQ(type("tensor<bfloat16>(x[10])").map(), type("tensor<float>(x[10])"));
    EXPECT_EQ(type("tensor<int8>(x[10])").map(), type("tensor<float>(x[10])"));
}

TEST(ValueTypeTest, require_that_reducing_dimensions_from_non_tensor_types_gives_error_type)
{
    EXPECT_TRUE(type("error").reduce({"x"}).is_error());
    EXPECT_TRUE(type("double").reduce({"x"}).is_error());
}

TEST(ValueTypeTest, require_that_a_scalar_value_can_be_fully_reduced_to_a_scalar_value)
{
    EXPECT_EQ(type("double").reduce({}), type("double"));
}

TEST(ValueTypeTest, require_that_tensor_value_types_can_be_reduced)
{
    EXPECT_EQ(type("tensor(x[10],y[20],z[30])").reduce({"x"}), type("tensor(y[20],z[30])"));
    EXPECT_EQ(type("tensor(x[10],y[20],z[30])").reduce({"y"}), type("tensor(x[10],z[30])"));
    EXPECT_EQ(type("tensor<float>(x[10],y[20],z[30])").reduce({"z"}), type("tensor<float>(x[10],y[20])"));
    EXPECT_EQ(type("tensor<bfloat16>(x[10],y[20],z[30])").reduce({"z"}), type("tensor<float>(x[10],y[20])"));
    EXPECT_EQ(type("tensor<int8>(x[10],y[20],z[30])").reduce({"z"}), type("tensor<float>(x[10],y[20])"));
    EXPECT_EQ(type("tensor(x[10],y[20],z[30])").reduce({"x", "z"}), type("tensor(y[20])"));
    EXPECT_EQ(type("tensor<float>(x[10],y[20],z[30])").reduce({"z", "x"}), type("tensor<float>(y[20])"));
    EXPECT_EQ(type("tensor<bfloat16>(x[10],y[20],z[30])").reduce({"z", "x"}), type("tensor<float>(y[20])"));
    EXPECT_EQ(type("tensor<int8>(x[10],y[20],z[30])").reduce({"z", "x"}), type("tensor<float>(y[20])"));
}

TEST(ValueTypeTest, require_that_reducing_an_empty_set_of_dimensions_means_reducing_them_all)
{
    EXPECT_EQ(type("tensor(x[10],y[20],z[30])").reduce({}), type("double"));
    EXPECT_EQ(type("tensor<float>(x[10],y[20],z[30])").reduce({}), type("double"));
    EXPECT_EQ(type("tensor<bfloat16>(x[10],y[20],z[30])").reduce({}), type("double"));
    EXPECT_EQ(type("tensor<int8>(x[10],y[20],z[30])").reduce({}), type("double"));
}

TEST(ValueTypeTest, require_that_reducing_non_existing_dimensions_gives_error_type)
{
    EXPECT_TRUE(type("tensor(y{})").reduce({"x"}).is_error());
    EXPECT_TRUE(type("tensor<float>(y[10])").reduce({"x"}).is_error());
}

TEST(ValueTypeTest, require_that_reducing_all_dimensions_gives_double_type)
{
    EXPECT_EQ(type("tensor(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
    EXPECT_EQ(type("tensor<float>(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
    EXPECT_EQ(type("tensor<bfloat16>(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
    EXPECT_EQ(type("tensor<int8>(x[10],y[20],z[30])").reduce({"x", "y", "z"}), type("double"));
}

void verify_join(const ValueType &a, const ValueType b, const ValueType &res) {
    SCOPED_TRACE(a.to_spec() + "," + b.to_spec());
    EXPECT_EQ(ValueType::join(a, b), res);
    EXPECT_EQ(ValueType::join(b, a), res);
}

TEST(ValueTypeTest, require_that_dimensions_can_be_combined_for_value_types)
{
    verify_join(type("double"), type("double"), type("double"));
    verify_join(type("tensor(x{},y{})"), type("tensor(y{},z{})"), type("tensor(x{},y{},z{})"));
    verify_join(type("tensor(y{})"), type("tensor(y{})"), type("tensor(y{})"));
    verify_join(type("tensor(y{})"), type("double"), type("tensor(y{})"));
    verify_join(type("tensor(a[10])"), type("tensor(a[10])"), type("tensor(a[10])"));
    verify_join(type("tensor(a[10])"), type("double"), type("tensor(a[10])"));
    verify_join(type("tensor(a[10])"), type("tensor(x{},y{},z{})"), type("tensor(a[10],x{},y{},z{})"));
}

TEST(ValueTypeTest, require_that_cell_type_is_handled_correctly_for_join)
{
    verify_join(type("tensor(x{})"), type("tensor(y{})"),           type("tensor(x{},y{})"));
    verify_join(type("tensor(x{})"), type("tensor<float>(y{})"),    type("tensor(x{},y{})"));
    verify_join(type("tensor(x{})"), type("tensor<bfloat16>(y{})"), type("tensor(x{},y{})"));
    verify_join(type("tensor(x{})"), type("tensor<int8>(y{})"),     type("tensor(x{},y{})"));
    verify_join(type("tensor<float>(x{})"), type("tensor<float>(y{})"),    type("tensor<float>(x{},y{})"));
    verify_join(type("tensor<float>(x{})"), type("tensor<bfloat16>(y{})"), type("tensor<float>(x{},y{})"));
    verify_join(type("tensor<float>(x{})"), type("tensor<int8>(y{})"),     type("tensor<float>(x{},y{})"));
    verify_join(type("tensor<bfloat16>(x{})"), type("tensor<bfloat16>(y{})"), type("tensor<float>(x{},y{})"));
    verify_join(type("tensor<bfloat16>(x{})"), type("tensor<int8>(y{})"),     type("tensor<float>(x{},y{})"));
    verify_join(type("tensor<int8>(x{})"), type("tensor<int8>(y{})"), type("tensor<float>(x{},y{})"));
    verify_join(type("tensor(x{})"), type("double"), type("tensor(x{})"));
    verify_join(type("tensor<float>(x{})"), type("double"), type("tensor<float>(x{})"));
    verify_join(type("tensor<bfloat16>(x{})"), type("double"), type("tensor<float>(x{})"));
    verify_join(type("tensor<int8>(x{})"), type("double"), type("tensor<float>(x{})"));
}

void verify_not_joinable(const ValueType &a, const ValueType &b) {
    SCOPED_TRACE(a.to_spec() + "," + b.to_spec());
    EXPECT_TRUE(ValueType::join(a, b).is_error());
    EXPECT_TRUE(ValueType::join(b, a).is_error());
}

TEST(ValueTypeTest, require_that_mapped_and_indexed_dimensions_are_not_joinable)
{
    verify_not_joinable(type("tensor(x[10])"), type("tensor(x{})"));
}

TEST(ValueTypeTest, require_that_indexed_dimensions_of_different_sizes_are_not_joinable)
{
    verify_not_joinable(type("tensor(x[10])"), type("tensor(x[20])"));
}

TEST(ValueTypeTest, require_that_error_type_combined_with_anything_produces_error_type)
{
    verify_not_joinable(type("error"), type("error"));
    verify_not_joinable(type("error"), type("double"));
    verify_not_joinable(type("error"), type("tensor(x{})"));
    verify_not_joinable(type("error"), type("tensor(x[10])"));
}

TEST(ValueTypeTest, require_that_tensor_dimensions_can_be_renamed)
{
    EXPECT_EQ(type("tensor(x{})").rename({"x"}, {"y"}), type("tensor(y{})"));
    EXPECT_EQ(type("tensor(x{},y[5])").rename({"x","y"}, {"y","x"}), type("tensor(y{},x[5])"));
    EXPECT_EQ(type("tensor(x{})").rename({"x"}, {"x"}), type("tensor(x{})"));
    EXPECT_EQ(type("tensor(x{})").rename({}, {}), type("error"));
    EXPECT_EQ(type("double").rename({}, {}), type("error"));
    EXPECT_EQ(type("tensor(x{},y{})").rename({"x"}, {"y","z"}), type("error"));
    EXPECT_EQ(type("tensor(x{},y{})").rename({"x","y"}, {"z"}), type("error"));
    EXPECT_EQ(type("double").rename({"a"}, {"b"}), type("error"));
    EXPECT_EQ(type("error").rename({"a"}, {"b"}), type("error"));
}

TEST(ValueTypeTest, require_that_dimension_rename_preserves_cell_type)
{
    EXPECT_EQ(type("tensor(x{})").rename({"x"}, {"y"}), type("tensor(y{})"));
    EXPECT_EQ(type("tensor<float>(x{})").rename({"x"}, {"y"}), type("tensor<float>(y{})"));
    EXPECT_EQ(type("tensor<bfloat16>(x{})").rename({"x"}, {"y"}), type("tensor<bfloat16>(y{})"));
    EXPECT_EQ(type("tensor<int8>(x{})").rename({"x"}, {"y"}), type("tensor<int8>(y{})"));
}

void verify_merge(const ValueType &a, const ValueType &b, const ValueType &res) {
    SCOPED_TRACE(a.to_spec() + "," + b.to_spec());
    EXPECT_EQ(ValueType::merge(a, b), res);
    EXPECT_EQ(ValueType::merge(b, a), res);
}

TEST(ValueTypeTest, require_that_similar_types_can_be_merged)
{
    verify_merge(type("error"), type("error"), type("error"));
    verify_merge(type("double"), type("double"), type("double"));
    verify_merge(type("tensor(x[5])"), type("tensor(x[5])"),           type("tensor(x[5])"));
    verify_merge(type("tensor(x[5])"), type("tensor<float>(x[5])"),    type("tensor(x[5])"));
    verify_merge(type("tensor(x[5])"), type("tensor<bfloat16>(x[5])"), type("tensor(x[5])"));
    verify_merge(type("tensor(x[5])"), type("tensor<int8>(x[5])"),     type("tensor(x[5])"));
    verify_merge(type("tensor<float>(x[5])"), type("tensor<float>(x[5])"),    type("tensor<float>(x[5])"));
    verify_merge(type("tensor<float>(x[5])"), type("tensor<bfloat16>(x[5])"), type("tensor<float>(x[5])"));
    verify_merge(type("tensor<float>(x[5])"), type("tensor<int8>(x[5])"),     type("tensor<float>(x[5])"));
    verify_merge(type("tensor<bfloat16>(x[5])"), type("tensor<bfloat16>(x[5])"), type("tensor<float>(x[5])"));
    verify_merge(type("tensor<bfloat16>(x[5])"), type("tensor<int8>(x[5])"),     type("tensor<float>(x[5])"));
    verify_merge(type("tensor<int8>(x[5])"), type("tensor<int8>(x[5])"),     type("tensor<float>(x[5])"));
    verify_merge(type("tensor(x{})"), type("tensor(x{})"), type("tensor(x{})"));
}

TEST(ValueTypeTest, require_that_diverging_types_can_not_be_merged)
{
    EXPECT_EQ(ValueType::merge(type("error"), type("double")), type("error"));
    EXPECT_EQ(ValueType::merge(type("double"), type("error")), type("error"));
    EXPECT_EQ(ValueType::merge(type("tensor(x[5])"), type("double")), type("error"));
    EXPECT_EQ(ValueType::merge(type("double"), type("tensor(x[5])")), type("error"));
    EXPECT_EQ(ValueType::merge(type("tensor(x[5])"), type("tensor(x[3])")), type("error"));
    EXPECT_EQ(ValueType::merge(type("tensor(x{})"), type("tensor(y{})")), type("error"));
}

void verify_concat(const ValueType &a, const ValueType &b, const std::string &dim, const ValueType &res) {
    SCOPED_TRACE(a.to_spec() + "," + b.to_spec());
    EXPECT_EQ(ValueType::concat(a, b, dim), res);
    EXPECT_EQ(ValueType::concat(b, a, dim), res);
}

TEST(ValueTypeTest, require_that_types_can_be_concatenated)
{
    verify_concat(type("error"),             type("tensor(x[2])"), "x", type("error"));
    verify_concat(type("tensor(x{})"),       type("tensor(x[2])"), "x", type("error"));
    verify_concat(type("tensor(x{})"),       type("tensor(x{})"),  "x", type("error"));
    verify_concat(type("tensor(x{})"),       type("double"),       "x", type("error"));
    verify_concat(type("tensor(x[3])"),      type("tensor(x[2])"), "y", type("error"));
    verify_concat(type("tensor(y[7])"),      type("tensor(x{})"),  "z", type("tensor(x{},y[7],z[2])"));
    verify_concat(type("double"),            type("double"),       "x", type("tensor(x[2])"));
    verify_concat(type("tensor(x[2])"),      type("double"),       "x", type("tensor(x[3])"));
    verify_concat(type("tensor(x[3])"),      type("tensor(x[2])"), "x", type("tensor(x[5])"));
    verify_concat(type("tensor(x[2])"),      type("double"),       "y", type("tensor(x[2],y[2])"));
    verify_concat(type("tensor(x[2])"),      type("tensor(x[2])"), "y", type("tensor(x[2],y[2])"));
    verify_concat(type("tensor(x[2],y[2])"), type("tensor(x[3])"), "x", type("tensor(x[5],y[2])"));
    verify_concat(type("tensor(x[2],y[2])"), type("tensor(y[7])"), "y", type("tensor(x[2],y[9])"));
    verify_concat(type("tensor(x[5])"),      type("tensor(y[7])"), "z", type("tensor(x[5],y[7],z[2])"));
}

TEST(ValueTypeTest, require_that_cell_type_is_handled_correctly_for_concat)
{
    verify_concat(type("tensor(x[3])"), type("tensor(x[2])"), "x",           type("tensor(x[5])"));
    verify_concat(type("tensor(x[3])"), type("tensor<float>(x[2])"), "x",    type("tensor(x[5])"));
    verify_concat(type("tensor(x[3])"), type("tensor<bfloat16>(x[2])"), "x", type("tensor(x[5])"));
    verify_concat(type("tensor(x[3])"), type("tensor<int8>(x[2])"), "x",     type("tensor(x[5])"));
    verify_concat(type("tensor<float>(x[3])"), type("tensor<float>(x[2])"), "x",    type("tensor<float>(x[5])"));
    verify_concat(type("tensor<float>(x[3])"), type("tensor<bfloat16>(x[2])"), "x", type("tensor<float>(x[5])"));
    verify_concat(type("tensor<float>(x[3])"), type("tensor<int8>(x[2])"), "x",     type("tensor<float>(x[5])"));
    verify_concat(type("tensor<bfloat16>(x[3])"), type("tensor<bfloat16>(x[2])"), "x", type("tensor<bfloat16>(x[5])"));
    verify_concat(type("tensor<bfloat16>(x[3])"), type("tensor<int8>(x[2])"), "x",     type("tensor<float>(x[5])"));
    verify_concat(type("tensor<int8>(x[3])"), type("tensor<int8>(x[2])"), "x",     type("tensor<int8>(x[5])"));
}

TEST(ValueTypeTest, require_that_concat_with_number_preserves_cell_type)
{
    verify_concat(type("tensor(x[3])"), type("double"), "x", type("tensor(x[4])"));
    verify_concat(type("tensor<float>(x[3])"), type("double"), "x", type("tensor<float>(x[4])"));
    verify_concat(type("tensor<bfloat16>(x[3])"), type("double"), "x", type("tensor<bfloat16>(x[4])"));
    verify_concat(type("tensor<int8>(x[3])"), type("double"), "x", type("tensor<int8>(x[4])"));
}

void verify_cell_cast(const ValueType &type) {
    SCOPED_TRACE(type.to_spec());
    for (CellType cell_type: CellTypeUtils::list_types()) {
        auto res_type = type.cell_cast(cell_type);
        if (type.is_error()) {
            EXPECT_TRUE(res_type.is_error());
            EXPECT_EQ(res_type, type);
        } else if (type.is_double()) {
            if (cell_type == CellType::DOUBLE) {
                EXPECT_TRUE(res_type.is_double());
            } else {
                EXPECT_TRUE(res_type.is_error());
            }
        } else {
            EXPECT_FALSE(res_type.is_error());
            EXPECT_EQ(int(res_type.cell_type()), int(cell_type));
            EXPECT_TRUE(res_type.dimensions() == type.dimensions());
        }
    }
}

TEST(ValueTypeTest, require_that_value_type_cell_cast_works_correctly)
{
    verify_cell_cast(type("error"));
    verify_cell_cast(type("double"));
    verify_cell_cast(type("tensor<double>(x[10])"));
    verify_cell_cast(type("tensor<float>(x[10])"));
    verify_cell_cast(type("tensor<bfloat16>(x[10])"));
    verify_cell_cast(type("tensor<int8>(x[10])"));
    verify_cell_cast(type("tensor<double>(x{})"));
    verify_cell_cast(type("tensor<float>(x{})"));
    verify_cell_cast(type("tensor<bfloat16>(x{})"));
    verify_cell_cast(type("tensor<int8>(x{})"));
    verify_cell_cast(type("tensor<double>(x{},y[5])"));
    verify_cell_cast(type("tensor<float>(x{},y[5])"));
    verify_cell_cast(type("tensor<bfloat16>(x{},y[5])"));
    verify_cell_cast(type("tensor<int8>(x{},y[5])"));
}

TEST(ValueTypeTest, require_that_actual_cell_type_can_be_converted_to_cell_type_name)
{
    EXPECT_EQ(value_type::cell_type_to_name(CellType::FLOAT), "float");
    EXPECT_EQ(value_type::cell_type_to_name(CellType::DOUBLE), "double");
}

TEST(ValueTypeTest, require_that_cell_type_name_can_be_converted_to_actual_cell_type)
{
    EXPECT_EQ(int(value_type::cell_type_from_name("float").value()), int(CellType::FLOAT));
    EXPECT_EQ(int(value_type::cell_type_from_name("double").value()), int(CellType::DOUBLE));
    EXPECT_FALSE(value_type::cell_type_from_name("int7").has_value());
}

TEST(ValueTypeTest, require_that_cell_type_name_recognition_is_strict)
{
    EXPECT_FALSE(value_type::cell_type_from_name("Float").has_value());
    EXPECT_FALSE(value_type::cell_type_from_name(" float").has_value());
    EXPECT_FALSE(value_type::cell_type_from_name("float ").has_value());
    EXPECT_FALSE(value_type::cell_type_from_name("f").has_value());
    EXPECT_FALSE(value_type::cell_type_from_name("").has_value());
}

TEST(ValueTypeTest, require_that_map_type_inference_works_as_expected)
{
    EXPECT_EQ(type("error").map(), type("error"));
    EXPECT_EQ(type("double").map(), type("double"));
    EXPECT_EQ(type("tensor(x[10])").map(), type("tensor(x[10])"));
    EXPECT_EQ(type("tensor<float>(x{})").map(), type("tensor<float>(x{})"));
}

TEST(ValueTypeTest, require_that_peek_type_inference_works_as_expected)
{
    auto input1 = type("tensor(a[2],b{},c[3],d{},e[5])");
    auto input2 = type("tensor<float>(a[2],b{},c[3],d{},e[5])");
    EXPECT_EQ(type("error").peek({}), type("error"));
    EXPECT_EQ(type("double").peek({}), type("error"));
    EXPECT_EQ(input1.peek({}), type("error"));
    EXPECT_EQ(input1.peek({"x"}), type("error"));
    EXPECT_EQ(input1.peek({"a", "c", "e"}), type("tensor(b{},d{})"));
    EXPECT_EQ(input2.peek({"b", "d"}), type("tensor<float>(a[2],c[3],e[5])"));
    EXPECT_EQ(input1.peek({"a", "b", "c", "d", "e"}), type("double"));
    EXPECT_EQ(input2.peek({"a", "b", "c", "d", "e"}), type("double"));
}

TEST(ValueTypeTest, require_that_non_scalar_peek_preserves_cell_type)
{
    EXPECT_EQ(type("tensor(x[3],y[5])").peek({"x"}), type("tensor(y[5])"));
    EXPECT_EQ(type("tensor<float>(x[3],y[5])").peek({"x"}), type("tensor<float>(y[5])"));
    EXPECT_EQ(type("tensor<bfloat16>(x[3],y[5])").peek({"x"}), type("tensor<bfloat16>(y[5])"));
    EXPECT_EQ(type("tensor<int8>(x[3],y[5])").peek({"x"}), type("tensor<int8>(y[5])"));
}

TEST(ValueTypeTest, require_that_scalar_peek_is_always_double)
{
    EXPECT_EQ(type("tensor(x[3],y[5])").peek({"x", "y"}), type("double"));
    EXPECT_EQ(type("tensor<float>(x[3],y[5])").peek({"x", "y"}), type("double"));
    EXPECT_EQ(type("tensor<bfloat16>(x[3],y[5])").peek({"x", "y"}), type("double"));
    EXPECT_EQ(type("tensor<int8>(x[3],y[5])").peek({"x", "y"}), type("double"));
}

TEST(ValueTypeTest, require_that_cell_alignment_can_be_obtained)
{
    EXPECT_EQ(CellTypeUtils::alignment(CellType::DOUBLE), alignof(double));
    EXPECT_EQ(CellTypeUtils::alignment(CellType::FLOAT), alignof(float));
    EXPECT_EQ(CellTypeUtils::alignment(CellType::BFLOAT16), alignof(BFloat16));
    EXPECT_EQ(CellTypeUtils::alignment(CellType::INT8), alignof(Int8Float));
}

TEST(ValueTypeTest, require_that_cell_array_size_can_be_calculated)
{
    EXPECT_EQ(CellTypeUtils::mem_size(CellType::DOUBLE, 37), 37 * sizeof(double));
    EXPECT_EQ(CellTypeUtils::mem_size(CellType::FLOAT, 37), 37 * sizeof(float));
    EXPECT_EQ(CellTypeUtils::mem_size(CellType::BFLOAT16, 37), 37 * sizeof(BFloat16));
    EXPECT_EQ(CellTypeUtils::mem_size(CellType::INT8, 37), 37 * sizeof(Int8Float));
}

TEST(ValueTypeTest, require_that_all_cell_types_can_be_listed)
{
    std::vector<CellType> expect = { CellType::DOUBLE, CellType::FLOAT, CellType::BFLOAT16, CellType::INT8 };
    std::vector<CellType> expect_stable;
    std::vector<CellType> expect_unstable;
    auto list = CellTypeUtils::list_types();
    ASSERT_EQ(list.size(), expect.size());
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

GTEST_MAIN_RUN_ALL_TESTS()
