// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "value_type.h"
#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <map>

namespace vespalib { class Stash; }

namespace vespalib::eval {

class TensorSpec;

using TypedCells = ::vespalib::tensor::TypedCells;

/**
 * Experimental interface layer that will be moved into Value when all
 * existing implementations are able to implement it. This interface
 * will try to unify scalars, dense tensors, sparse tensors and mixed
 * tensors while also enabling operations to be implemented
 * efficiently using this interface without having knowledge about the
 * actual implementation. Baseline operations will treat all values as
 * mixed tensors. Simplified and optimized variants may replace them
 * as done today based on type knowledge.
 *
 * All values are expected to be separated into a continuous area
 * storing cells as concatenated dense subspaces, and an index
 * structure used to look up label combinations; mapping them into a
 * set of dense subspaces.
 **/
struct NewValue : Value {

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
    virtual ~NewValue() {}
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
    virtual ArrayRef<T> add_subspace(const std::vector<vespalib::stringref> &addr) = 0;

    // Given the ownership of the builder itself, produce the newly
    // created value. This means that builders can only be used once,
    // it also means values can build themselves.
    virtual std::unique_ptr<NewValue> build(std::unique_ptr<ValueBuilder> self) = 0;
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
class SimpleValue : public NewValue, public NewValue::Index
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
    const NewValue::Index &index() const override { return *this; }
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
    std::unique_ptr<NewValue> build(std::unique_ptr<ValueBuilder<T>> self) override {
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<NewValue>(this);
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
    template <typename F> void execute(size_t idx, size_t lhs, size_t rhs, F &&f) const {
        if (idx < loop_cnt.size()) {
            for (size_t i = 0; i < loop_cnt[idx]; ++i) {
                execute(idx + 1, lhs, rhs, std::forward<F>(f));
                lhs += lhs_stride[idx];
                rhs += rhs_stride[idx];
            }
        } else {
            f(lhs, rhs);
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
std::unique_ptr<NewValue> new_join(const NewValue &a, const NewValue &b, join_fun_t function, const ValueBuilderFactory &factory);

/**
 * Make a value from a tensor spec using a value builder factory
 * interface, making it work with any value implementation.
 **/
std::unique_ptr<NewValue> new_value_from_spec(const TensorSpec &spec, const ValueBuilderFactory &factory);

/**
 * Convert a generic value to a tensor spec.
 **/
TensorSpec spec_from_new_value(const NewValue &value);

}
