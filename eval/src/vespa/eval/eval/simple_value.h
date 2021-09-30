// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include <vespa/vespalib/util/shared_string_repo.h>
#include <vector>
#include <map>

namespace vespalib {
class Stash;
class nbostream;
}

namespace vespalib::eval {

class TensorSpec;

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
    using Handle = SharedStringRepo::Handle;
    using Labels = std::vector<Handle>;

    ValueType _type;
    size_t _num_mapped_dims;
    size_t _subspace_size;
    std::map<Labels,size_t> _index;
protected:
    size_t num_mapped_dims() const { return _num_mapped_dims; }
    size_t subspace_size() const { return _subspace_size; }
    void add_mapping(ConstArrayRef<vespalib::stringref> addr);
    void add_mapping(ConstArrayRef<string_id> addr);
    MemoryUsage estimate_extra_memory_usage() const;
public:
    SimpleValue(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in);
    ~SimpleValue() override;
    const ValueType &type() const override { return _type; }
    const Value::Index &index() const override { return *this; }
    size_t size() const override { return _index.size(); }
    std::unique_ptr<View> create_view(ConstArrayRef<size_t> dims) const override;
    static Value::UP from_spec(const TensorSpec &spec);
    static Value::UP from_value(const Value &value);
    static Value::UP from_stream(nbostream &stream);
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
    SimpleValueT(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces_in);
    ~SimpleValueT() override;
    TypedCells cells() const override { return TypedCells(ConstArrayRef<T>(_cells)); }
    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) override;
    ArrayRef<T> add_subspace(ConstArrayRef<string_id> addr) override;
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>> self) override {
        if (num_mapped_dims() == 0) {
            assert(size() == 1);
        }
        assert(_cells.size() == (size() * subspace_size()));
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
    }
    MemoryUsage get_memory_usage() const override {
        MemoryUsage usage = self_memory_usage<SimpleValueT<T>>();
        usage.merge(vector_extra_memory_usage(_cells));
        usage.merge(estimate_extra_memory_usage());
        return usage;
    }
};

/**
 * ValueBuilderFactory implementation for SimpleValue.
 **/
class SimpleValueBuilderFactory : public ValueBuilderFactory {
private:
    SimpleValueBuilderFactory();
    static SimpleValueBuilderFactory _factory;
    std::unique_ptr<ValueBuilderBase> create_value_builder_base(const ValueType &type, bool transient,
            size_t num_mapped_dims, size_t subspace_size, size_t expected_subspaces) const override;
public:
    static const SimpleValueBuilderFactory &get() { return _factory; }
};

}
