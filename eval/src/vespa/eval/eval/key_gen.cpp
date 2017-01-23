// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "key_gen.h"
#include "node_visitor.h"
#include "node_traverser.h"
#include "function.h"

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
    void add_bool(bool value) { key.push_back(value ? '1' : '0'); }

    // visit
    virtual void visit(const Number   &node) { add_byte( 1); add_double(node.value()); }
    virtual void visit(const Symbol   &node) { add_byte( 2); add_int(node.id()); }
    virtual void visit(const String   &node) { add_byte( 3); add_hash(node.hash()); }
    virtual void visit(const Array    &node) { add_byte( 4); add_size(node.size()); }
    virtual void visit(const Neg          &) { add_byte( 5); }
    virtual void visit(const Not          &) { add_byte( 6); }
    virtual void visit(const If       &node) { add_byte( 7); add_double(node.p_true()); }
    virtual void visit(const Let          &) { add_byte( 8); }
    virtual void visit(const Error        &) { add_byte( 9); }
    virtual void visit(const TensorSum    &) { add_byte(10); } // dimensions should be part of key
    virtual void visit(const TensorMap    &) { add_byte(11); } // lambda should be part of key
    virtual void visit(const TensorJoin   &) { add_byte(12); } // lambda should be part of key
    virtual void visit(const TensorReduce &) { add_byte(13); } // aggr/dimensions should be part of key
    virtual void visit(const TensorRename &) { add_byte(14); } // dimensions should be part of key
    virtual void visit(const TensorLambda &) { add_byte(15); } // type/lambda should be part of key
    virtual void visit(const TensorConcat &) { add_byte(16); } // dimension should be part of key
    virtual void visit(const Add          &) { add_byte(20); }
    virtual void visit(const Sub          &) { add_byte(21); }
    virtual void visit(const Mul          &) { add_byte(22); }
    virtual void visit(const Div          &) { add_byte(23); }
    virtual void visit(const Pow          &) { add_byte(24); }
    virtual void visit(const Equal        &) { add_byte(25); }
    virtual void visit(const NotEqual     &) { add_byte(26); }
    virtual void visit(const Approx       &) { add_byte(27); }
    virtual void visit(const Less         &) { add_byte(28); }
    virtual void visit(const LessEqual    &) { add_byte(29); }
    virtual void visit(const Greater      &) { add_byte(30); }
    virtual void visit(const GreaterEqual &) { add_byte(31); }
    virtual void visit(const In           &) { add_byte(32); }
    virtual void visit(const And          &) { add_byte(33); }
    virtual void visit(const Or           &) { add_byte(34); }
    virtual void visit(const Cos          &) { add_byte(35); }
    virtual void visit(const Sin          &) { add_byte(36); }
    virtual void visit(const Tan          &) { add_byte(37); }
    virtual void visit(const Cosh         &) { add_byte(38); }
    virtual void visit(const Sinh         &) { add_byte(39); }
    virtual void visit(const Tanh         &) { add_byte(40); }
    virtual void visit(const Acos         &) { add_byte(41); }
    virtual void visit(const Asin         &) { add_byte(42); }
    virtual void visit(const Atan         &) { add_byte(43); }
    virtual void visit(const Exp          &) { add_byte(44); }
    virtual void visit(const Log10        &) { add_byte(45); }
    virtual void visit(const Log          &) { add_byte(46); }
    virtual void visit(const Sqrt         &) { add_byte(47); }
    virtual void visit(const Ceil         &) { add_byte(48); }
    virtual void visit(const Fabs         &) { add_byte(49); }
    virtual void visit(const Floor        &) { add_byte(50); }
    virtual void visit(const Atan2        &) { add_byte(51); }
    virtual void visit(const Ldexp        &) { add_byte(52); }
    virtual void visit(const Pow2         &) { add_byte(53); }
    virtual void visit(const Fmod         &) { add_byte(54); }
    virtual void visit(const Min          &) { add_byte(55); }
    virtual void visit(const Max          &) { add_byte(56); }
    virtual void visit(const IsNan        &) { add_byte(57); }
    virtual void visit(const Relu         &) { add_byte(58); }
    virtual void visit(const Sigmoid      &) { add_byte(59); }

    // traverse
    virtual bool open(const Node &node) { node.accept(*this); return true; }
    virtual void close(const Node &) {}
};

} // namespace vespalib::eval::<unnamed>

vespalib::string gen_key(const Function &function, PassParams pass_params)
{
    KeyGen key_gen;
    key_gen.add_bool(pass_params == PassParams::ARRAY);
    key_gen.add_size(function.num_params());
    function.root().traverse(key_gen);
    return key_gen.key;
}

} // namespace vespalib::eval
} // namespace vespalib
