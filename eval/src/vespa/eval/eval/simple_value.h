// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <map>

namespace vespalib { class Stash; }

namespace vespalib::eval {

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
    using Labels = std::vector<vespalib::string>;

    ValueType _type;
    size_t _num_mapped_dims;
    size_t _subspace_size;
    std::map<Labels,size_t> _index;
protected:
    size_t subspace_size() const { return _subspace_size; }
    void add_mapping(ConstArrayRef<vespalib::stringref> addr);
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
    SimpleValueT(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces_in);
    ~SimpleValueT() override;
    TypedCells cells() const override { return TypedCells(ConstArrayRef<T>(_cells)); }
    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) override;
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
class SimpleValueBuilderFactory : public ValueBuilderFactory {
private:
    SimpleValueBuilderFactory();
    static SimpleValueBuilderFactory _factory;
    std::unique_ptr<ValueBuilderBase> create_value_builder_base(const ValueType &type,
            size_t num_mapped_dims, size_t subspace_size, size_t expected_subspaces) const override;
public:
    static const SimpleValueBuilderFactory &get() { return _factory; }
};

}
