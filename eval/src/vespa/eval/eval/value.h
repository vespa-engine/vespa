// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_type.h"
#include "typed_cells.h"
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
            virtual void lookup(ConstArrayRef<const vespalib::stringref*> addr) = 0;

            // Extract the next result (if any) from the previous
            // lookup into the given partial address and index. Only
            // the labels for the dimensions NOT specified in
            // create_view will be extracted here.
            virtual bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) = 0;

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

/**
 * A generic value without any mapped dimensions referencing its
 * components without owning anything.
 **/
class DenseValueView final : public Value
{
private:
    const ValueType &_type;
    TypedCells _cells;
public:
    DenseValueView(const ValueType &type_in, TypedCells cells_in)
        : _type(type_in), _cells(cells_in) {}
    const ValueType &type() const final override { return _type; }
    TypedCells cells() const final override { return _cells; }
    const Index &index() const final override { return TrivialIndex::get(); }
};

/**
 * Tagging interface used as return type from factories before
 * downcasting to actual builder with specialized cell type.
 **/
struct ValueBuilderBase {
    virtual ~ValueBuilderBase() {}
};

/**
 * Interface used to build a value one dense subspace at a
 * time. Enables decoupling of what the value should contain from how
 * to store the value.
 **/
template <typename T>
struct ValueBuilder : ValueBuilderBase {
    // add a dense subspace for the given address (label for all
    // mapped dimensions in canonical order). Note that previously
    // returned subspaces will be invalidated when new subspaces are
    // added. Also note that adding the same subspace multiple times
    // is not allowed.
    virtual ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) = 0;

    // Given the ownership of the builder itself, produce the newly
    // created value. This means that builders can only be used once,
    // it also means values can build themselves.
    virtual std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder> self) = 0;
};

/**
 * Factory able to create appropriate value builders. We do not really
 * care about the full mathematical type here, but it needs to be
 * passed since it is exposed in the value api. The expected number of
 * subspaces is also passed since it enables the builder to pre-size
 * internal structures appropriately. Note that since we are not able
 * to have virtual templated functions we need to cast the created
 * builder. With interoperability between all values.
 **/
struct ValueBuilderFactory {
    template <typename T>
    std::unique_ptr<ValueBuilder<T>> create_value_builder(const ValueType &type,
            size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces) const
    {
        assert(check_cell_type<T>(type.cell_type()));
        auto base = create_value_builder_base(type, num_mapped_dims_in, subspace_size_in, expected_subspaces);
        ValueBuilder<T> *builder = dynamic_cast<ValueBuilder<T>*>(base.get());
        assert(builder);
        base.release();
        return std::unique_ptr<ValueBuilder<T>>(builder);
    }
    template <typename T>
    std::unique_ptr<ValueBuilder<T>> create_value_builder(const ValueType &type) const
    {
        return create_value_builder<T>(type, type.count_mapped_dimensions(), type.dense_subspace_size(), 1);
    }
    virtual ~ValueBuilderFactory() {}
protected:
    virtual std::unique_ptr<ValueBuilderBase> create_value_builder_base(const ValueType &type,
            size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces) const = 0;
};

}

VESPA_CAN_SKIP_DESTRUCTION(::vespalib::eval::DoubleValue);
VESPA_CAN_SKIP_DESTRUCTION(::vespalib::eval::DenseValueView);
