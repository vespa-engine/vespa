// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <iostream>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/data/memory.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory &factory = SimpleValueBuilderFactory::get();

std::vector<Layout> layouts = {
    {},
    {x(3)},
    {x(3),y(5)},
    {x(3),y(5),z(7)},
    float_cells({x(3),y(5),z(7)}),
    {x({"a","b","c"})},
    {x({"a","b","c"}),y({"foo","bar"})},
    {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}),
    {x(3),y({"foo", "bar"}),z(7)},
    {x({"a","b","c"}),y(5),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y(5),z({"i","j","k","l"})})
};


TEST(ValueCodecTest, simple_values_can_be_converted_from_and_to_tensor_spec) {
    for (const auto &layout: layouts) {
        TensorSpec expect = spec(layout, N());
        std::unique_ptr<Value> value = value_from_spec(expect, factory);
        TensorSpec actual = spec_from_value(*value);
        EXPECT_EQ(actual, expect);
    }
}

TEST(ValueCodecTest, simple_values_can_be_built_using_tensor_spec) {
    TensorSpec spec("tensor(w{},x[2],y{},z[2])");
    spec.add({{"w", "xxx"}, {"x", 0}, {"y", "xxx"}, {"z", 0}}, 1.0)
        .add({{"w", "xxx"}, {"x", 0}, {"y", "yyy"}, {"z", 1}}, 2.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "xxx"}, {"z", 0}}, 3.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "yyy"}, {"z", 1}}, 4.0);
    Value::UP tensor = value_from_spec(spec, factory);
    TensorSpec full_spec("tensor(w{},x[2],y{},z[2])");
    full_spec
        .add({{"w", "xxx"}, {"x", 0}, {"y", "xxx"}, {"z", 0}}, 1.0)
        .add({{"w", "xxx"}, {"x", 0}, {"y", "xxx"}, {"z", 1}}, 0.0)
        .add({{"w", "xxx"}, {"x", 0}, {"y", "yyy"}, {"z", 0}}, 0.0)
        .add({{"w", "xxx"}, {"x", 0}, {"y", "yyy"}, {"z", 1}}, 2.0)
        .add({{"w", "xxx"}, {"x", 1}, {"y", "xxx"}, {"z", 0}}, 0.0)
        .add({{"w", "xxx"}, {"x", 1}, {"y", "xxx"}, {"z", 1}}, 0.0)
        .add({{"w", "xxx"}, {"x", 1}, {"y", "yyy"}, {"z", 0}}, 0.0)
        .add({{"w", "xxx"}, {"x", 1}, {"y", "yyy"}, {"z", 1}}, 0.0)
        .add({{"w", "yyy"}, {"x", 0}, {"y", "xxx"}, {"z", 0}}, 0.0)
        .add({{"w", "yyy"}, {"x", 0}, {"y", "xxx"}, {"z", 1}}, 0.0)
        .add({{"w", "yyy"}, {"x", 0}, {"y", "yyy"}, {"z", 0}}, 0.0)
        .add({{"w", "yyy"}, {"x", 0}, {"y", "yyy"}, {"z", 1}}, 0.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "xxx"}, {"z", 0}}, 3.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "xxx"}, {"z", 1}}, 0.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "yyy"}, {"z", 0}}, 0.0)
        .add({{"w", "yyy"}, {"x", 1}, {"y", "yyy"}, {"z", 1}}, 4.0);
    Value::UP full_tensor = value_from_spec(full_spec, factory);
    EXPECT_EQUAL(full_spec, spec_from_value(*tensor));
    EXPECT_EQUAL(full_spec, spec_from_value(*full_tensor));
};

//-----------------------------------------------------------------------------

vespalib::string make_type_spec(bool use_float, const vespalib::string &dims) {
    vespalib::string type_spec = "tensor";
    if (use_float) {
        type_spec.append("<float>");
    }
    type_spec.append(dims);
    return type_spec;
}

struct TensorExample {
    virtual ~TensorExample();
    virtual TensorSpec make_spec(bool use_float) const = 0;
    virtual std::unique_ptr<Value> make_tensor(bool use_float) const = 0;
    virtual void encode_default(nbostream &dst) const = 0;
    virtual void encode_with_double(nbostream &dst) const = 0;
    virtual void encode_with_float(nbostream &dst) const = 0;
    void verify_encode_decode(bool is_dense) const {
        nbostream expect_default;
        nbostream expect_double;
        nbostream expect_float;
        encode_default(expect_default);
        encode_with_double(expect_double);
        encode_with_float(expect_float);
        nbostream data_double;
        nbostream data_float;
        encode_value(*make_tensor(false), data_double);
        encode_value(*make_tensor(true), data_float);
        if (is_dense) {
            EXPECT_EQ(Memory(data_double.peek(), data_double.size()),
                      Memory(expect_default.peek(), expect_default.size()));
            EXPECT_EQ(Memory(data_float.peek(), data_float.size()),
                      Memory(expect_float.peek(), expect_float.size()));
        } else {
            EXPECT_EQ(spec_from_value(*decode_value(data_double, factory)), make_spec(false));
            EXPECT_EQ(spec_from_value(*decode_value(data_float, factory)), make_spec(true));
        }
        EXPECT_EQ(spec_from_value(*decode_value(expect_default, factory)), make_spec(false));
        EXPECT_EQ(spec_from_value(*decode_value(expect_double, factory)), make_spec(false));
        EXPECT_EQ(spec_from_value(*decode_value(expect_float, factory)), make_spec(true));
    }
};
TensorExample::~TensorExample() = default;

//-----------------------------------------------------------------------------

struct SparseTensorExample : TensorExample {
    TensorSpec make_spec(bool use_float) const override {
        return TensorSpec(make_type_spec(use_float, "(x{},y{})"))
            .add({{"x","a"},{"y","a"}}, 1)
            .add({{"x","a"},{"y","b"}}, 2)
            .add({{"x","b"},{"y","a"}}, 3);
    }
    std::unique_ptr<Value> make_tensor(bool use_float) const override {
        return value_from_spec(make_spec(use_float), factory);
    }
    template <typename T>
    void encode_inner(nbostream &dst) const {
        dst.putInt1_4Bytes(2);
        dst.writeSmallString("x");
        dst.writeSmallString("y");
        dst.putInt1_4Bytes(3);
        dst.writeSmallString("a");
        dst.writeSmallString("a");
        dst << (T) 1;
        dst.writeSmallString("a");
        dst.writeSmallString("b");
        dst << (T) 2;
        dst.writeSmallString("b");
        dst.writeSmallString("a");
        dst << (T) 3;
    }
    void encode_default(nbostream &dst) const override {
        dst.putInt1_4Bytes(1);
        encode_inner<double>(dst);
    }
    void encode_with_double(nbostream &dst) const override {
        dst.putInt1_4Bytes(5);
        dst.putInt1_4Bytes(0);
        encode_inner<double>(dst);
    }
    void encode_with_float(nbostream &dst) const override {
        dst.putInt1_4Bytes(5);
        dst.putInt1_4Bytes(1);
        encode_inner<float>(dst);
    }
};

TEST(ValueCodecTest, sparse_tensors_can_be_encoded_and_decoded) {
    SparseTensorExample f1;
    f1.verify_encode_decode(false);
}

//-----------------------------------------------------------------------------

struct DenseTensorExample : TensorExample {
    TensorSpec make_spec(bool use_float) const override {
        return TensorSpec(make_type_spec(use_float, "(x[3],y[2])"))
            .add({{"x",0},{"y",0}}, 1)
            .add({{"x",0},{"y",1}}, 2)
            .add({{"x",1},{"y",0}}, 3)
            .add({{"x",1},{"y",1}}, 4)
            .add({{"x",2},{"y",0}}, 5)
            .add({{"x",2},{"y",1}}, 6);
    }
    std::unique_ptr<Value> make_tensor(bool use_float) const override {
        return value_from_spec(make_spec(use_float), factory);
    }
    template <typename T>
    void encode_inner(nbostream &dst) const {
        dst.putInt1_4Bytes(2);
        dst.writeSmallString("x");
        dst.putInt1_4Bytes(3);
        dst.writeSmallString("y");
        dst.putInt1_4Bytes(2);
        dst << (T) 1;
        dst << (T) 2;
        dst << (T) 3;
        dst << (T) 4;
        dst << (T) 5;
        dst << (T) 6;
    }
    void encode_default(nbostream &dst) const override {
        dst.putInt1_4Bytes(2);
        encode_inner<double>(dst);
    }
    void encode_with_double(nbostream &dst) const override {
        dst.putInt1_4Bytes(6);
        dst.putInt1_4Bytes(0);
        encode_inner<double>(dst);
    }
    void encode_with_float(nbostream &dst) const override {
        dst.putInt1_4Bytes(6);
        dst.putInt1_4Bytes(1);
        encode_inner<float>(dst);
    }
};

TEST(ValueCodecTest, dense_tensors_can_be_encoded_and_decoded) {
    DenseTensorExample f1;
    f1.verify_encode_decode(true);
}

//-----------------------------------------------------------------------------

struct MixedTensorExample : TensorExample {
    TensorSpec make_spec(bool use_float) const override {
        return TensorSpec(make_type_spec(use_float, "(x{},y{},z[2])"))
            .add({{"x","a"},{"y","a"},{"z",0}}, 1)
            .add({{"x","a"},{"y","a"},{"z",1}}, 2)
            .add({{"x","a"},{"y","b"},{"z",0}}, 3)
            .add({{"x","a"},{"y","b"},{"z",1}}, 4)
            .add({{"x","b"},{"y","a"},{"z",0}}, 5)
            .add({{"x","b"},{"y","a"},{"z",1}}, 6);
    }
    std::unique_ptr<Value> make_tensor(bool use_float) const override {
        return value_from_spec(make_spec(use_float), factory);
    }
    template <typename T>
    void encode_inner(nbostream &dst) const {
        dst.putInt1_4Bytes(2);
        dst.writeSmallString("x");
        dst.writeSmallString("y");
        dst.putInt1_4Bytes(1);
        dst.writeSmallString("z");
        dst.putInt1_4Bytes(2);
        dst.putInt1_4Bytes(3);
        dst.writeSmallString("a");
        dst.writeSmallString("a");
        dst << (T) 1;
        dst << (T) 2;
        dst.writeSmallString("a");
        dst.writeSmallString("b");
        dst << (T) 3;
        dst << (T) 4;
        dst.writeSmallString("b");
        dst.writeSmallString("a");
        dst << (T) 5;
        dst << (T) 6;
    }
    void encode_default(nbostream &dst) const override {
        dst.putInt1_4Bytes(3);
        encode_inner<double>(dst);
    }
    void encode_with_double(nbostream &dst) const override {
        dst.putInt1_4Bytes(7);
        dst.putInt1_4Bytes(0);
        encode_inner<double>(dst);
    }
    void encode_with_float(nbostream &dst) const override {
        dst.putInt1_4Bytes(7);
        dst.putInt1_4Bytes(1);
        encode_inner<float>(dst);
    }
};

TEST(ValueCodecTest, mixed_tensors_can_be_encoded_and_decoded) {
    MixedTensorExample f1;
    f1.verify_encode_decode(false);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
