// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory_usage_stuff.h"
#include "value_type.h"
#include "typed_cells.h"
#include <vespa/vespalib/util/string_id.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/traits.h>
#include <vector>
#include <memory>

namespace vespalib::eval {

/**
 * An abstract Value.
 **/
struct Value {
    using UP = std::unique_ptr<Value>;
    using CREF = std::reference_wrapper<const Value>;
    virtual const ValueType &type() const = 0;
    virtual ~Value() {}

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
            virtual void lookup(ConstArrayRef<const string_id*> addr) = 0;

            // Extract the next result (if any) from the previous
            // lookup into the given partial address and index. Only
            // the labels for the dimensions NOT specified in
            // create_view will be extracted here.
            virtual bool next_result(ConstArrayRef<string_id*> addr_out, size_t &idx_out) = 0;

            virtual ~View() {}
        };

        // total number of mappings (equal to the number of dense subspaces)
        virtual size_t size() const = 0;

        // create a view able to look up dense subspaces based on
        // labels from a subset of the mapped dimensions.
        virtual std::unique_ptr<View> create_view(ConstArrayRef<size_t> dims) const = 0;

        virtual ~Index() {}
    };
    virtual TypedCells cells() const = 0;
    virtual const Index &index() const = 0;
    virtual MemoryUsage get_memory_usage() const = 0;
    virtual double as_double() const;
    bool as_bool() const { return (as_double() != 0.0); }
};

/**
 * Common index for values without any mapped dimensions.
 **/
class TrivialIndex : public Value::Index {
private:
    TrivialIndex();
    static TrivialIndex _index;
public:
    static const TrivialIndex &get() { return _index; }
    size_t size() const override;
    std::unique_ptr<View> create_view(ConstArrayRef<size_t> dims) const override;
};

class DoubleValue final : public Value
{
private:
    double _value;
    static ValueType _type;
public:
    DoubleValue(double value) : _value(value) {}
    TypedCells cells() const final override { return TypedCells(ConstArrayRef<double>(&_value, 1)); }
    const Index &index() const final override { return TrivialIndex::get(); }
    MemoryUsage get_memory_usage() const final override { return self_memory_usage<DoubleValue>(); }
    double as_double() const final override { return _value; }
    const ValueType &type() const final override { return _type; }
    static const ValueType &shared_type() { return _type; }
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
    MemoryUsage get_memory_usage() const final override { return self_memory_usage<DenseValueView>(); }
};

/**
 * A generic value referencing its components without owning anything.
 **/
class ValueView final : public Value
{
private:
    const ValueType &_type;
    const Index &_index;
    TypedCells _cells;
public:
    ValueView(const ValueType &type_in, const Index &index_in, TypedCells cells_in)
        : _type(type_in), _index(index_in), _cells(cells_in) {}
    const ValueType &type() const final override { return _type; }
    TypedCells cells() const final override { return _cells; }
    const Index &index() const final override { return _index; }
    MemoryUsage get_memory_usage() const final override { return self_memory_usage<ValueView>(); }
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

    // add a dense subspace for the given address where labels are
    // specified by shared string repo ids. Note that the caller is
    // responsible for making sure the ids are valid 'long enough'.
    virtual ArrayRef<T> add_subspace(ConstArrayRef<string_id> addr) = 0;

    // convenience function to add a subspace with an empty address
    ArrayRef<T> add_subspace() { return add_subspace(ConstArrayRef<string_id>()); }

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
private:
    template <typename T>
    std::unique_ptr<ValueBuilder<T>> create_value_builder(const ValueType &type, bool transient,
            size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces) const
    {
        assert(check_cell_type<T>(type.cell_type()));
        auto base = create_value_builder_base(type, transient, num_mapped_dims_in, subspace_size_in, expected_subspaces);
        ValueBuilder<T> *builder = dynamic_cast<ValueBuilder<T>*>(base.get());
        assert(builder);
        base.release();
        return std::unique_ptr<ValueBuilder<T>>(builder);
    }
public:
    template <typename T>
    std::unique_ptr<ValueBuilder<T>> create_value_builder(const ValueType &type,
            size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces) const
    {
        return create_value_builder<T>(type, false, num_mapped_dims_in, subspace_size_in, expected_subspaces);
    }
    template <typename T>
    std::unique_ptr<ValueBuilder<T>> create_transient_value_builder(const ValueType &type,
            size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces) const
    {
        return create_value_builder<T>(type, true, num_mapped_dims_in, subspace_size_in, expected_subspaces);
    }
    template <typename T>
    std::unique_ptr<ValueBuilder<T>> create_value_builder(const ValueType &type) const
    {
        return create_value_builder<T>(type, false, type.count_mapped_dimensions(), type.dense_subspace_size(), 1);
    }
    std::unique_ptr<Value> copy(const Value &value) const;
    virtual ~ValueBuilderFactory() {}
protected:
    virtual std::unique_ptr<ValueBuilderBase> create_value_builder_base(const ValueType &type, bool transient,
            size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces) const = 0;
};

}

VESPA_CAN_SKIP_DESTRUCTION(::vespalib::eval::DoubleValue);
VESPA_CAN_SKIP_DESTRUCTION(::vespalib::eval::DenseValueView);
VESPA_CAN_SKIP_DESTRUCTION(::vespalib::eval::ValueView);
