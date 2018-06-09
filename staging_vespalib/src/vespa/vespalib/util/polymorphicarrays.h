// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#pragma once

#include "polymorphicarray.h"
#include <vespa/vespalib/util/memory.h>
#include <vector>

namespace vespalib {

template <typename T, typename B>
class PrimitiveArrayT final : public IArrayT<B>
{
    using typename IArrayT<B>::iterator;
public:
    PrimitiveArrayT() : _array() { }
    ~PrimitiveArrayT() { }
    const T & operator [] (size_t i) const override { return _array[i]; }
    T & operator [] (size_t i) override { return _array[i]; }
    void resize(size_t sz) override { _array.resize(sz); }
    void reserve(size_t sz) override { _array.reserve(sz); }
    void clear() override { _array.clear(); }
    IArrayT<B> * clone() const override { return new PrimitiveArrayT<T, B>(*this); }
    size_t size() const override { return _array.size(); }
    iterator erase(iterator it) override  { _array.erase(_array.begin() + (it - this->begin())); return it; }
    void push_back(const B & v) override {
        _array.emplace_back();
        _array.back().assign(v);
    }
private:
    std::vector<T> _array;
};

template <typename B>
class ComplexArrayT final : public IArrayT<B>
{
    using typename IArrayT<B>::iterator;
public:
    class Factory {
    public:
        typedef std::unique_ptr<Factory> UP;
        typedef vespalib::CloneablePtr<Factory> CP;
        virtual B * create() = 0;
        virtual Factory * clone() const = 0;
        virtual ~Factory() { }
    };
    explicit ComplexArrayT(typename Factory::UP factory) : _array(), _factory(factory.release()) { }
    ~ComplexArrayT() { }
    const B & operator [] (size_t i) const override { return *_array[i]; }
    B & operator [] (size_t i) override { return *_array[i]; }
    void resize(size_t sz) override {
        _array.resize(sz);
        for (auto & cp : _array) {
            if ( cp.get() == nullptr) {
               cp.reset(_factory->create());
            }
        }
    }
    void reserve(size_t sz) override { _array.reserve(sz); }
    void clear() override { _array.clear(); }
    IArrayT<B> * clone() const override { return new ComplexArrayT<B>(*this); }
    size_t size() const override { return _array.size(); }
    iterator erase(iterator it) override  { _array.erase(_array.begin() + (it - this->begin())); return it; }
    void push_back(const B & v) override { _array.push_back(v.clone()); }
private:
    typedef vespalib::CloneablePtr<B> CP;
    std::vector<CP> _array;
    typename Factory::CP _factory;
};

}
