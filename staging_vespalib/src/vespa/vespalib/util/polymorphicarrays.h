// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#pragma once

#include <vespa/vespalib/util/memory.h>
#include <vector>

namespace vespalib {

/**
 * Describes an interface an array of polymorphic types.
 * The intention is to allow efficient implementations when that is possible
 * while still enjoying the flexibility of the polymorph interface.
 * It is not a full feldged Array implementation as std::vector. It contains just
 * the minimum required to allow for efficient implementations for document::ArrayFieldValue.
 *
 * You specify the base type the interface shall provide. This base type must define
 * virtual void assign(const B & rhs);
 * For use with ComplexArrayT your type also need
 * virtual T * clone() const;
 */
template<typename B>
class IArrayT
{
public:
    class iterator {
    public:
        iterator(IArrayT & a, size_t i) : _a(&a), _i(i) { }
        iterator operator+(size_t diff) const { return iterator(*_a, _i + diff); }
        iterator& operator++() { ++_i; return *this; }
        iterator operator++(int) { iterator other(*this); ++_i; return other; }
        bool operator==(const iterator & other) const { return (_a == other._a) && (_i == other._i); }
        bool operator!=(const iterator & other) const { return (_i != other._i) || (_a != other._a); }
        B & operator*() { return (*_a)[_i]; }
        B * operator->() { return &(*_a)[_i]; }
        friend ssize_t operator - (const iterator & a, const iterator & b) { return a._i - b._i; }
    private:
        IArrayT * _a;
        size_t  _i;
    };
    class const_iterator {
    public:
        const_iterator(const IArrayT & a, size_t i) : _a(&a), _i(i) { }
        const_iterator operator+(size_t diff) const { return const_iterator(*_a, _i + diff); }
        const_iterator& operator++() { ++_i; return *this; }
        const_iterator operator++(int) { const_iterator other(*this); ++_i; return other; }
        bool operator==(const const_iterator & other) const { return (_a == other._a) && (_i == other._i); }
        bool operator!=(const const_iterator & other) const { return (_i != other._i) || (_a != other._a); }
        const B & operator*() const { return (*_a)[_i]; }
        const B * operator->() const { return &(*_a)[_i]; }
        size_t operator - (const const_iterator & b) const { return _i - b._i; }
    private:
        const IArrayT * _a;
        size_t  _i;
    };
    typedef std::unique_ptr<IArrayT> UP;

    virtual ~IArrayT() { }
    virtual const B & operator [] (size_t i) const = 0;
    virtual B & operator [] (size_t i) = 0;
    virtual void resize(size_t sz) = 0;
    virtual void reserve(size_t sz) = 0;
    virtual void clear() = 0;
    virtual IArrayT * clone() const = 0;
    virtual size_t size() const = 0;
    virtual iterator erase(iterator it) = 0;
    virtual const_iterator begin() const { return const_iterator(*this, 0); }
    virtual const_iterator end() const { return const_iterator(*this, size()); }
    virtual iterator begin() { return iterator(*this, 0); }
    virtual iterator end() { return iterator(*this, size()); }
    bool empty() const { return size() == 0; }
    virtual void push_back(const B & v) = 0;
};

template <typename T, typename B>
class PrimitiveArrayT : public IArrayT<B>
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
        size_t sz(_array.size());
        _array.resize(sz + 1);
        _array[sz].assign(v);
    }
private:
    std::vector<T> _array;
};

template <typename B>
class ComplexArrayT : public IArrayT<B>
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
