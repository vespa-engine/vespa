// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <map>

namespace vespalib { class Stash; }

namespace vespalib::eval {

class TensorSpec;

using TypedCells = ::vespalib::tensor::TypedCells;

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
    virtual ArrayRef<T> add_subspace(const std::vector<vespalib::stringref> &addr) = 0;

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

/**
 * A simple implementation of a generic value that can also be used to
 * build new values. This class focuses on simplicity over speed and
 * is intended as a reference implementation that can also be used to
 * test the correctness of tensor operations as they are moved away
 * from the implementation of individual tensor classes.
 **/
class SimpleValue : public Value, public Value::Index
{
private:
    using Addr = std::vector<vespalib::string>;
    ValueType _type;
    size_t _num_mapped_dims;
    size_t _subspace_size;
    std::map<Addr,size_t> _index;
protected:
    size_t subspace_size() const { return _subspace_size; }
    void add_mapping(const std::vector<vespalib::stringref> &addr);
public:
    SimpleValue(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in);
    ~SimpleValue() override;
    const ValueType &type() const override { return _type; }
    const Value::Index &index() const override { return *this; }
    size_t size() const override { return _index.size(); }
    std::unique_ptr<View> create_view(const std::vector<size_t> &dims) const override;
};

/**
 * Subclasses of SimpleValue handling cell type specialization.
 **/
template <typename T>
class SimpleValueT : public SimpleValue, public ValueBuilder<T>
{
private:
    std::vector<T> _cells;
public:
    SimpleValueT(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in);
    ~SimpleValueT() override;
    TypedCells cells() const override { return TypedCells(ConstArrayRef<T>(_cells)); }
    ArrayRef<T> add_subspace(const std::vector<vespalib::stringref> &addr) override;
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>> self) override {
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
    }
};

/**
 * ValueBuilderFactory implementation for SimpleValue. 
 **/
struct SimpleValueBuilderFactory : ValueBuilderFactory {
    ~SimpleValueBuilderFactory() override {}
protected:
    std::unique_ptr<ValueBuilderBase> create_value_builder_base(const ValueType &type,
            size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces) const override;    
};

/**
 * Plan for how to traverse two partially overlapping dense subspaces
 * in parallel, identifying all matching cell index combinations, in
 * the exact order the joined cells will be stored in the result. The
 * plan can be made up-front during tensor function compilation.
 **/
struct DenseJoinPlan {
    size_t lhs_size;
    size_t rhs_size;
    size_t out_size;
    std::vector<size_t> loop_cnt;
    std::vector<size_t> lhs_stride;
    std::vector<size_t> rhs_stride;
    DenseJoinPlan(const ValueType &lhs_type, const ValueType &rhs_type);
    ~DenseJoinPlan();
    template <typename F> void execute(size_t lhs, size_t rhs, F &&f) const {
        switch(loops_left(0)) {
        case 0: return execute_few<F, 0>(0, lhs, rhs, std::forward<F>(f));
        case 1: return execute_few<F, 1>(0, lhs, rhs, std::forward<F>(f));
        case 2: return execute_few<F, 2>(0, lhs, rhs, std::forward<F>(f));
        case 3: return execute_few<F, 3>(0, lhs, rhs, std::forward<F>(f));
        default: return execute_many<F>(0, lhs, rhs, std::forward<F>(f));
        }
    }
private:
    size_t loops_left(size_t idx) const { return (loop_cnt.size() - idx); }
    template <typename F, size_t N> void execute_few(size_t idx, size_t lhs, size_t rhs, F &&f) const {
        if constexpr (N == 0) {
            f(lhs, rhs);
        } else {
            for (size_t i = 0; i < loop_cnt[idx]; ++i, lhs += lhs_stride[idx], rhs += rhs_stride[idx]) {
                execute_few<F, N - 1>(idx + 1, lhs, rhs, std::forward<F>(f));
            }
        }
    }
    template <typename F> void execute_many(size_t idx, size_t lhs, size_t rhs, F &&f) const {
        for (size_t i = 0; i < loop_cnt[idx]; ++i, lhs += lhs_stride[idx], rhs += rhs_stride[idx]) {
            if (loops_left(idx + 1) == 3) {
                execute_few<F, 3>(idx + 1, lhs, rhs, std::forward<F>(f));
            } else {
                execute_many<F>(idx + 1, lhs, rhs, std::forward<F>(f));
            }
        }
    }
};

/**
 * Plan for how to join the sparse part (all mapped dimensions)
 * between two values. The plan can be made up-front during tensor
 * function compilation.
 **/
struct SparseJoinPlan {
    enum class Source { LHS, RHS, BOTH };
    std::vector<Source> sources;
    std::vector<size_t> lhs_overlap;
    std::vector<size_t> rhs_overlap;
    SparseJoinPlan(const ValueType &lhs_type, const ValueType &rhs_type);
    ~SparseJoinPlan();
};

/**
 * Generic join operation treating both values as mixed
 * tensors. Packaging will change, and while the baseline join will
 * not have information about low-level value class implementations,
 * it will have up-front knowledge about types, specifically
 * dimensional overlap and result type.
 **/
using join_fun_t = double (*)(double, double);
std::unique_ptr<Value> new_join(const Value &a, const Value &b, join_fun_t function, const ValueBuilderFactory &factory);

/**
 * Make a value from a tensor spec using a value builder factory
 * interface, making it work with any value implementation.
 **/
std::unique_ptr<Value> value_from_spec(const TensorSpec &spec, const ValueBuilderFactory &factory);

/**
 * Convert a generic value to a tensor spec.
 **/
TensorSpec spec_from_value(const Value &value);

}
