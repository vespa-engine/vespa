// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_type.h"
#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/traits.h>
#include <vector>
#include <memory>

namespace vespalib::eval {

class Tensor;

/**
 * An abstract Value.
 **/
struct Value {
    using UP = std::unique_ptr<Value>;
    using CREF = std::reference_wrapper<const Value>;
    using TypedCells = tensor::TypedCells;
    virtual const ValueType &type() const = 0;
    virtual ~Value() {}

// ---- new interface enabling separation of values and operations
    // Root lookup structure for mapping labels to dense subspace indexes
    struct Index {

        // A view able to look up dense subspace indexes from labels
        // specifying a partial address for the dimensions given to
        // create_view. A view is re-usable. Lookups are performed by
        // calling the lookup function and lookup results are
        // extracted using the next_result function.
        struct View {

            // look up dense subspace indexes from labels specifying a
            // partial address for the dimensions given to
            // create_view. Results from the lookup is extracted using
            // the next_result function.
            virtual void lookup(const std::vector<const vespalib::stringref*> &addr) = 0;

            // Extract the next result (if any) from the previous
            // lookup into the given partial address and index. Only
            // the labels for the dimensions NOT specified in
            // create_view will be extracted here.
            virtual bool next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out) = 0;

            virtual ~View() {}
        };

        // total number of mappings (equal to the number of dense subspaces)
        virtual size_t size() const = 0;

        // create a view able to look up dense subspaces based on
        // labels from a subset of the mapped dimensions.
        virtual std::unique_ptr<View> create_view(const std::vector<size_t> &dims) const = 0;

        virtual ~Index() {}
    };
    virtual TypedCells cells() const = 0;
    virtual const Index &index() const = 0;
// --- end of new interface

// --- old interface that may be (partially) removed in the future
    virtual bool is_double() const { return false; }
    virtual bool is_tensor() const { return false; }
    virtual double as_double() const { return 0.0; }
    bool as_bool() const { return (as_double() != 0.0); }
    virtual const Tensor *as_tensor() const { return nullptr; }
// --- end of old interface
};

/**
 * Common index for values without any mapped dimensions.
 **/
class TrivialIndex : public Value::Index {
private:
    TrivialIndex();
    static TrivialIndex _index;
    size_t size() const override;
    std::unique_ptr<View> create_view(const std::vector<size_t> &dims) const override;
public:
    static const TrivialIndex &get() { return _index; }
};

class DoubleValue : public Value
{
private:
    double _value;
    static ValueType _type;
public:
    DoubleValue(double value) : _value(value) {}
    TypedCells cells() const override { return TypedCells(ConstArrayRef<double>(&_value, 1)); }
    const Index &index() const override { return TrivialIndex::get(); }
    bool is_double() const override { return true; }
    double as_double() const override { return _value; }
    const ValueType &type() const override { return _type; }
    static const ValueType &double_type() { return _type; }
};

}

VESPA_CAN_SKIP_DESTRUCTION(::vespalib::eval::DoubleValue);
