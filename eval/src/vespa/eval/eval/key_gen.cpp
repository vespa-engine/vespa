// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "key_gen.h"
#include "node_visitor.h"
#include "node_traverser.h"

namespace vespalib {
namespace eval {

using namespace nodes;

namespace {

struct KeyGen : public NodeVisitor, public NodeTraverser {
    vespalib::string key;

    // build
    void add_double(double value) { key.append(&value, sizeof(value)); }
    void add_size(size_t value) { key.append(&value, sizeof(value)); }
    void add_int(int value) { key.append(&value, sizeof(value)); }
    void add_hash(uint32_t value) { key.append(&value, sizeof(value)); }
    void add_byte(uint8_t value) { key.append(&value, sizeof(value)); }

    // visit
    void visit(const Number   &node) override { add_byte( 1); add_double(node.value()); }
    void visit(const Symbol   &node) override { add_byte( 2); add_int(node.id()); }
    void visit(const String   &node) override { add_byte( 3); add_hash(node.hash()); }
    void visit(const In       &node) override { add_byte( 4);
        add_size(node.num_entries());
        for (size_t i = 0; i < node.num_entries(); ++i) {
            add_double(node.get_entry(i).get_const_value());
        }
    }
    void visit(const Neg            &) override { add_byte( 5); }
    void visit(const Not            &) override { add_byte( 6); }
    void visit(const If         &node) override { add_byte( 7); add_double(node.p_true()); }
    void visit(const Error          &) override { add_byte( 9); }
    void visit(const TensorMap      &) override { add_byte(10); } // lambda should be part of key
    void visit(const TensorJoin     &) override { add_byte(11); } // lambda should be part of key
    void visit(const TensorMerge    &) override { add_byte(12); } // lambda should be part of key
    void visit(const TensorReduce   &) override { add_byte(13); } // aggr/dimensions should be part of key
    void visit(const TensorRename   &) override { add_byte(14); } // dimensions should be part of key
    void visit(const TensorConcat   &) override { add_byte(15); } // dimension should be part of key
    void visit(const TensorCellCast &) override { add_byte(16); } // cell type should be part of key
    void visit(const TensorCreate   &) override { add_byte(17); } // type/addr should be part of key
    void visit(const TensorLambda   &) override { add_byte(18); } // type/lambda should be part of key
    void visit(const TensorPeek     &) override { add_byte(19); } // addr should be part of key
    void visit(const Add            &) override { add_byte(20); }
    void visit(const Sub            &) override { add_byte(21); }
    void visit(const Mul            &) override { add_byte(22); }
    void visit(const Div            &) override { add_byte(23); }
    void visit(const Mod            &) override { add_byte(24); }
    void visit(const Pow            &) override { add_byte(25); }
    void visit(const Equal          &) override { add_byte(26); }
    void visit(const NotEqual       &) override { add_byte(27); }
    void visit(const Approx         &) override { add_byte(28); }
    void visit(const Less           &) override { add_byte(29); }
    void visit(const LessEqual      &) override { add_byte(30); }
    void visit(const Greater        &) override { add_byte(31); }
    void visit(const GreaterEqual   &) override { add_byte(32); }
    void visit(const And            &) override { add_byte(34); }
    void visit(const Or             &) override { add_byte(35); }
    void visit(const Cos            &) override { add_byte(36); }
    void visit(const Sin            &) override { add_byte(37); }
    void visit(const Tan            &) override { add_byte(38); }
    void visit(const Cosh           &) override { add_byte(39); }
    void visit(const Sinh           &) override { add_byte(40); }
    void visit(const Tanh           &) override { add_byte(41); }
    void visit(const Acos           &) override { add_byte(42); }
    void visit(const Asin           &) override { add_byte(43); }
    void visit(const Atan           &) override { add_byte(44); }
    void visit(const Exp            &) override { add_byte(45); }
    void visit(const Log10          &) override { add_byte(46); }
    void visit(const Log            &) override { add_byte(47); }
    void visit(const Sqrt           &) override { add_byte(48); }
    void visit(const Ceil           &) override { add_byte(49); }
    void visit(const Fabs           &) override { add_byte(50); }
    void visit(const Floor          &) override { add_byte(51); }
    void visit(const Atan2          &) override { add_byte(52); }
    void visit(const Ldexp          &) override { add_byte(53); }
    void visit(const Pow2           &) override { add_byte(54); }
    void visit(const Fmod           &) override { add_byte(55); }
    void visit(const Min            &) override { add_byte(56); }
    void visit(const Max            &) override { add_byte(57); }
    void visit(const IsNan          &) override { add_byte(58); }
    void visit(const Relu           &) override { add_byte(59); }
    void visit(const Sigmoid        &) override { add_byte(60); }
    void visit(const Elu            &) override { add_byte(61); }
    void visit(const Erf            &) override { add_byte(62); }

    // traverse
    bool open(const Node &node) override { node.accept(*this); return true; }
    void close(const Node &) override {}
};

} // namespace vespalib::eval::<unnamed>

vespalib::string gen_key(const Function &function, PassParams pass_params)
{
    KeyGen key_gen;
    key_gen.add_byte(uint8_t(pass_params));
    key_gen.add_size(function.num_params());
    function.root().traverse(key_gen);
    return key_gen.key;
}

} // namespace vespalib::eval
} // namespace vespalib
