// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"

namespace vespalib::eval {

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
        ValueBuilder<T> *builder = static_cast<ValueBuilder<T>*>(base.get());
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
