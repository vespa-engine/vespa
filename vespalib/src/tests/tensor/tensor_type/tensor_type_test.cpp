// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/eval/value_type.h>
#include <vespa/vespalib/tensor/tensor_type.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <ostream>

using namespace vespalib::tensor;
using vespalib::eval::ValueType;

TEST("require that INVALID tensor type can be created") {
    TensorType t = TensorType::invalid();
    EXPECT_TRUE(t.type() == TensorType::Type::INVALID);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

TEST("require that NUMBER tensor type can be created") {
    TensorType t = TensorType::number();
    EXPECT_TRUE(t.type() == TensorType::Type::NUMBER);
    EXPECT_EQUAL(t.dimensions().size(), 0u);
}

TEST("require that SPARSE tensor type can be created") {
    TensorType t = TensorType::sparse({"x", "y"});
    EXPECT_TRUE(t.type() == TensorType::Type::SPARSE);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
}

TEST("require that SPARSE tensor type sorts dimensions") {
    TensorType t = TensorType::sparse({"x", "z", "y"});
    EXPECT_TRUE(t.type() == TensorType::Type::SPARSE);
    ASSERT_EQUAL(t.dimensions().size(), 3u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[2].name, "z");
}

TEST("require that SPARSE tensor type use npos for dimension size") {
    TensorType t = TensorType::sparse({"x", "y"});
    EXPECT_TRUE(t.type() == TensorType::Type::SPARSE);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, TensorType::Dimension::npos);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, TensorType::Dimension::npos);
}

TEST("require that DENSE tensor type can be created") {
    TensorType t = TensorType::dense({{"x", 10}, {"y", 20}});
    EXPECT_TRUE(t.type() == TensorType::Type::DENSE);
    ASSERT_EQUAL(t.dimensions().size(), 2u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, 20u);
}

TEST("require that DENSE tensor type sorts dimensions") {
    TensorType t = TensorType::dense({{"x", 10}, {"z", 30}, {"y", 20}});
    EXPECT_TRUE(t.type() == TensorType::Type::DENSE);
    ASSERT_EQUAL(t.dimensions().size(), 3u);
    EXPECT_EQUAL(t.dimensions()[0].name, "x");
    EXPECT_EQUAL(t.dimensions()[0].size, 10u);
    EXPECT_EQUAL(t.dimensions()[1].name, "y");
    EXPECT_EQUAL(t.dimensions()[1].size, 20u);
    EXPECT_EQUAL(t.dimensions()[2].name, "z");
    EXPECT_EQUAL(t.dimensions()[2].size, 30u);
}

void verify_equal(const TensorType &a, const TensorType &b) {
    EXPECT_TRUE(a == b);
    EXPECT_TRUE(b == a);
    EXPECT_FALSE(a != b);
    EXPECT_FALSE(b != a);
}

void verify_not_equal(const TensorType &a, const TensorType &b) {
    EXPECT_TRUE(a != b);
    EXPECT_TRUE(b != a);
    EXPECT_FALSE(a == b);
    EXPECT_FALSE(b == a);
}

TEST("require that valid tensor types can be compared") {
    TEST_DO(verify_equal(TensorType::number(), TensorType::number()));
    TEST_DO(verify_not_equal(TensorType::number(), TensorType::sparse({})));
    TEST_DO(verify_not_equal(TensorType::number(), TensorType::dense({})));
    TEST_DO(verify_equal(TensorType::sparse({"x", "y"}), TensorType::sparse({"y", "x"})));
    TEST_DO(verify_not_equal(TensorType::sparse({"x", "y"}), TensorType::sparse({"x", "y", "z"})));
    TEST_DO(verify_not_equal(TensorType::sparse({}), TensorType::dense({})));
    TEST_DO(verify_equal(TensorType::dense({{"x", 10}, {"y", 20}}), TensorType::dense({{"y", 20}, {"x", 10}})));
    TEST_DO(verify_not_equal(TensorType::dense({{"x", 10}, {"y", 20}}), TensorType::dense({{"x", 10}, {"y", 10}})));
}

TEST("require that INVALID tensor type is not equal to any type") {
    TEST_DO(verify_not_equal(TensorType::invalid(), TensorType::invalid()));
    TEST_DO(verify_not_equal(TensorType::invalid(), TensorType::number()));
    TEST_DO(verify_not_equal(TensorType::invalid(), TensorType::sparse({})));
    TEST_DO(verify_not_equal(TensorType::invalid(), TensorType::dense({})));
}

void verify_predicates(const TensorType &type, bool expect_valid, bool expect_number, bool expect_tensor) {
    EXPECT_EQUAL(type.is_valid(), expect_valid);
    EXPECT_EQUAL(type.is_number(), expect_number);
    EXPECT_EQUAL(type.is_tensor(), expect_tensor);
}

TEST("require that type-related predicate functions work as expected") {
    TEST_DO(verify_predicates(TensorType::invalid(),  false, false, false));
    TEST_DO(verify_predicates(TensorType::number(),   true,  true,  false));
    TEST_DO(verify_predicates(TensorType::sparse({}), true,  false, true));
    TEST_DO(verify_predicates(TensorType::dense({}),  true,  false, true));
}

TEST("require that duplicate dimension names result in invalid types") {
    EXPECT_TRUE(!TensorType::sparse({"x", "x"}).is_valid());
    EXPECT_TRUE(!TensorType::dense({{"x", 10}, {"x", 10}}).is_valid());
    EXPECT_TRUE(!TensorType::dense({{"x", 10}, {"x", 20}}).is_valid());
}

TEST("require that removing dimensions from non-tensor types gives invalid type") {
    EXPECT_TRUE(!TensorType::invalid().remove_dimensions({"x"}).is_valid());
    EXPECT_TRUE(!TensorType::number().remove_dimensions({"x"}).is_valid());
    EXPECT_TRUE(!TensorType::invalid().remove_dimensions({}).is_valid());
    EXPECT_TRUE(!TensorType::number().remove_dimensions({}).is_valid());
}

TEST("require that dimensions can be removed from sparse tensor types") {
    TensorType type = TensorType::sparse({"x", "y", "z"});
    EXPECT_EQUAL(TensorType::sparse({"y", "z"}), type.remove_dimensions({"x"}));
    EXPECT_EQUAL(TensorType::sparse({"x", "z"}), type.remove_dimensions({"y"}));
    EXPECT_EQUAL(TensorType::sparse({"x", "y"}), type.remove_dimensions({"z"}));
    EXPECT_EQUAL(TensorType::sparse({"y"}),      type.remove_dimensions({"x", "z"}));
    EXPECT_EQUAL(TensorType::sparse({"y"}),      type.remove_dimensions({"z", "x"}));    
}

TEST("require that dimensions can be removed from dense tensor types") {
    TensorType type = TensorType::dense({{"x", 10}, {"y", 20}, {"z", 30}});
    EXPECT_EQUAL(TensorType::dense({{"y", 20}, {"z", 30}}), type.remove_dimensions({"x"}));
    EXPECT_EQUAL(TensorType::dense({{"x", 10}, {"z", 30}}), type.remove_dimensions({"y"}));
    EXPECT_EQUAL(TensorType::dense({{"x", 10}, {"y", 20}}), type.remove_dimensions({"z"}));
    EXPECT_EQUAL(TensorType::dense({{"y", 20}}),            type.remove_dimensions({"x", "z"}));
    EXPECT_EQUAL(TensorType::dense({{"y", 20}}),            type.remove_dimensions({"z", "x"}));
}

TEST("require that removing non-existing dimensions gives invalid type") {
    EXPECT_TRUE(!TensorType::sparse({"y"}).remove_dimensions({"x"}).is_valid());
    EXPECT_TRUE(!TensorType::dense({{"y", 10}}).remove_dimensions({"x"}).is_valid());
}

TEST("require that dimensions can be combined for sparse tensor types") {
    TensorType sparse     = TensorType::sparse({});
    TensorType sparse_xy  = TensorType::sparse({"x", "y"});
    TensorType sparse_yz  = TensorType::sparse({"y", "z"});
    TensorType sparse_xyz = TensorType::sparse({"x", "y", "z"});
    TensorType sparse_y   = TensorType::sparse({"y"});
    EXPECT_EQUAL(sparse_xy.add_dimensions_from(sparse_yz), sparse_xyz);
    EXPECT_EQUAL(sparse_yz.add_dimensions_from(sparse_xy), sparse_xyz);
    EXPECT_EQUAL(sparse_xy.keep_dimensions_in(sparse_yz), sparse_y);
    EXPECT_EQUAL(sparse_yz.keep_dimensions_in(sparse_xy), sparse_y);
    EXPECT_EQUAL(sparse_y.add_dimensions_from(sparse_y), sparse_y);
    EXPECT_EQUAL(sparse_y.keep_dimensions_in(sparse_y), sparse_y);
    EXPECT_EQUAL(sparse.add_dimensions_from(sparse), sparse);
    EXPECT_EQUAL(sparse.keep_dimensions_in(sparse), sparse);
}

TEST("require that dimensions can be combined for dense tensor types") {
    TensorType dense     = TensorType::dense({});
    TensorType dense_xy  = TensorType::dense({{"x", 10}, {"y", 10}});
    TensorType dense_yz  = TensorType::dense({{"y", 10}, {"z", 10}});
    TensorType dense_xyz = TensorType::dense({{"x", 10}, {"y", 10}, {"z", 10}});
    TensorType dense_y   = TensorType::dense({{"y", 10}});
    EXPECT_EQUAL(dense_xy.add_dimensions_from(dense_yz), dense_xyz);
    EXPECT_EQUAL(dense_yz.add_dimensions_from(dense_xy), dense_xyz);
    EXPECT_EQUAL(dense_xy.keep_dimensions_in(dense_yz), dense_y);
    EXPECT_EQUAL(dense_yz.keep_dimensions_in(dense_xy), dense_y);
    EXPECT_EQUAL(dense_y.add_dimensions_from(dense_y), dense_y);
    EXPECT_EQUAL(dense_y.keep_dimensions_in(dense_y), dense_y);
    EXPECT_EQUAL(dense.add_dimensions_from(dense), dense);
    EXPECT_EQUAL(dense.keep_dimensions_in(dense), dense);
}

void verify_combinable(const TensorType &a, const TensorType &b) {
    EXPECT_TRUE(a.add_dimensions_from(b).is_valid());
    EXPECT_TRUE(b.add_dimensions_from(a).is_valid());
    EXPECT_TRUE(a.keep_dimensions_in(b).is_valid());
    EXPECT_TRUE(b.keep_dimensions_in(a).is_valid());
}

void verify_not_combinable(const TensorType &a, const TensorType &b) {
    EXPECT_TRUE(!a.add_dimensions_from(b).is_valid());
    EXPECT_TRUE(!b.add_dimensions_from(a).is_valid());
    EXPECT_TRUE(!a.keep_dimensions_in(b).is_valid());
    EXPECT_TRUE(!b.keep_dimensions_in(a).is_valid());
}

TEST("require that dimensions need to have the same size to be combinable") {
    verify_combinable(TensorType::dense({{"x", 10}}), TensorType::dense({{"x", 10}}));
    verify_not_combinable(TensorType::dense({{"x", 10}}), TensorType::dense({{"x", 20}}));
}

TEST("require that dimension combining only works for equal tensor types") {
    std::vector<TensorType> types = {TensorType::invalid(), TensorType::number(),
                                     TensorType::sparse({}), TensorType::dense({})};
    for (size_t a = 0; a < types.size(); ++a) {
        for (size_t b = a; b < types.size(); ++b) {
            TEST_STATE(vespalib::make_string("a=%zu, b=%zu", a, b).c_str());
            if ((a == b) && types[a].is_tensor()) {
                verify_combinable(types[a], types[b]);
            } else {
                verify_not_combinable(types[a], types[b]);
            }
        }
    }
}

TEST("require that sparse tensor type can make spec") {
    TensorType sparse     = TensorType::sparse({});
    TensorType sparse_xy  = TensorType::sparse({"x", "y"});
    TensorType sparse_yz  = TensorType::sparse({"y", "z"});
    TensorType sparse_xyz = TensorType::sparse({"x", "y", "z"});
    TensorType sparse_y   = TensorType::sparse({"y"});
    EXPECT_EQUAL("tensor()", sparse.toSpec());
    EXPECT_EQUAL("tensor(x{},y{})", sparse_xy.toSpec());
    EXPECT_EQUAL("tensor(y{},z{})", sparse_yz.toSpec());
    EXPECT_EQUAL("tensor(x{},y{},z{})", sparse_xyz.toSpec());
    EXPECT_EQUAL("tensor(y{})", sparse_y.toSpec());
}

TEST("require that dense tensor type can make spec") {
    TensorType dense     = TensorType::dense({});
    TensorType dense_xy  = TensorType::dense({{"x", 10}, {"y", 10}});
    TensorType dense_yz  = TensorType::dense({{"y", 10}, {"z", 10}});
    TensorType dense_xyz = TensorType::dense({{"x", 10}, {"y", 10}, {"z", 10}});
    TensorType dense_y   = TensorType::dense({{"y", 10}});
    EXPECT_EQUAL("tensor()", dense.toSpec());
    EXPECT_EQUAL("tensor(x[10],y[10])", dense_xy.toSpec());
    EXPECT_EQUAL("tensor(y[10],z[10])", dense_yz.toSpec());
    EXPECT_EQUAL("tensor(x[10],y[10],z[10])", dense_xyz.toSpec());
    EXPECT_EQUAL("tensor(y[10])", dense_y.toSpec());
}

TEST("require that sparse tensor type spec can be parsed") {
    TensorType sparse_xy  = TensorType::sparse({"x", "y"});
    TensorType sparse_yz  = TensorType::sparse({"y", "z"});
    TensorType sparse_xyz = TensorType::sparse({"x", "y", "z"});
    TensorType sparse_y   = TensorType::sparse({"y"});
    EXPECT_EQUAL(sparse_xy, TensorType::fromSpec("tensor(x{},y{})"));
    EXPECT_EQUAL(sparse_xy,
                 TensorType::fromSpec("  tensor ( x { } , y { }  )"));
    EXPECT_EQUAL(sparse_yz, TensorType::fromSpec("tensor(y{},z{})"));
    EXPECT_EQUAL(sparse_xyz, TensorType::fromSpec("tensor(x{},y{},z{})"));
    EXPECT_EQUAL(sparse_xyz, TensorType::fromSpec("tensor(z{},y{},x{})"));
    EXPECT_EQUAL(sparse_y, TensorType::fromSpec("tensor(y{})"));
}

TEST("require that dense tensor type spec can be parsed") {
    TensorType dense     = TensorType::dense({});
    TensorType dense_xy  = TensorType::dense({{"x", 10}, {"y", 10}});
    TensorType dense_yz  = TensorType::dense({{"y", 10}, {"z", 10}});
    TensorType dense_xyz = TensorType::dense({{"x", 10}, {"y", 10}, {"z", 10}});
    TensorType dense_y   = TensorType::dense({{"y", 10}});
    EXPECT_EQUAL(dense, TensorType::fromSpec("tensor()"));
    EXPECT_EQUAL(dense_xy, TensorType::fromSpec("tensor(x[10],y[10])"));
    EXPECT_EQUAL(dense_xy,
                 TensorType::fromSpec("  tensor ( x [ 10 ] , y [ 10 ]  ) "));
    EXPECT_EQUAL(dense_yz, TensorType::fromSpec("tensor(y[10],z[10])"));
    EXPECT_EQUAL(dense_xyz, TensorType::fromSpec("tensor(x[10],y[10],z[10])"));
    EXPECT_EQUAL(dense_xyz, TensorType::fromSpec("tensor(z[10],y[10],x[10])"));
    EXPECT_EQUAL(dense_y, TensorType::fromSpec("tensor(y[10])"));
}

TEST("require that tensor type can be converted to value type") {
    EXPECT_TRUE(TensorType::invalid().as_value_type().is_error());
    EXPECT_TRUE(TensorType::number().as_value_type().is_double());
    EXPECT_EQUAL(ValueType::tensor_type({{"x"}, {"y"}, {"z"}}),
                 TensorType::sparse({"x", "y", "z"}).as_value_type());
    EXPECT_EQUAL(ValueType::tensor_type({{"x", 10}, {"y", 20}, {"z", 30}}),
                 TensorType::dense({{"x", 10}, {"y", 20}, {"z", 30}}).as_value_type());
    EXPECT_EQUAL(ValueType::double_type(), TensorType::sparse({}).as_value_type());
    EXPECT_EQUAL(ValueType::double_type(), TensorType::dense({}).as_value_type());
}

TEST("require that invalid tensor type spec is parsed as invalid") {
    TensorType::Type invalid   = TensorType::Type::INVALID;
    EXPECT_TRUE(invalid == TensorType::fromSpec("tansor(y{})").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(y{10})").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(y{}").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(y{}),").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(x{},y[10])").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tansor(y[10])").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(y[])").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(y[10]").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(y[10]),").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(x[10],y{})").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("invalid").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("number").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("dense").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("sparse").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("densetensor").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("sparsetensor").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("  ").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(y{},y{})").type());
    EXPECT_TRUE(invalid == TensorType::fromSpec("tensor(y[10],y[10])").type());
}

TEST_MAIN() { TEST_RUN_ALL(); }
