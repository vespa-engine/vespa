// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#pragma once

#include "polymorphicarraybase.h"

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
class IArrayT : public IArrayBase {
public:
    class iterator {
    public:
        iterator(IArrayT &a, size_t i) : _a(&a), _i(i) {}
        iterator operator+(size_t diff) const { return iterator(*_a, _i + diff); }
        iterator &operator++() {
            ++_i;
            return *this;
        }
        iterator operator++(int) {
            iterator other(*this);
            ++_i;
            return other;
        }
        bool operator==(const iterator &other) const { return (_a == other._a) && (_i == other._i); }
        bool operator!=(const iterator &other) const { return (_i != other._i) || (_a != other._a); }
        B &operator*() { return (*_a)[_i]; }
        B *operator->() { return &(*_a)[_i]; }
        friend ssize_t operator-(const iterator &a, const iterator &b) { return a._i - b._i; }
    private:
        IArrayT *_a;
        size_t _i;
    };

    class const_iterator {
    public:
        const_iterator(const IArrayT &a, size_t i) : _a(&a), _i(i) {}
        const_iterator operator+(size_t diff) const { return const_iterator(*_a, _i + diff); }
        const_iterator &operator++() {
            ++_i;
            return *this;
        }
        const_iterator operator++(int) {
            const_iterator other(*this);
            ++_i;
            return other;
        }
        bool operator==(const const_iterator &other) const { return (_a == other._a) && (_i == other._i); }
        bool operator!=(const const_iterator &other) const { return (_i != other._i) || (_a != other._a); }
        const B &operator*() const { return (*_a)[_i]; }
        const B *operator->() const { return &(*_a)[_i]; }
        size_t operator-(const const_iterator &b) const { return _i - b._i; }
    private:
        const IArrayT *_a;
        size_t _i;
    };

    typedef std::unique_ptr<IArrayT> UP;
    virtual const B &operator[](size_t i) const = 0;
    virtual B &operator[](size_t i) = 0;
    virtual IArrayT *clone() const override = 0;
    virtual iterator erase(iterator it) = 0;
    virtual const_iterator begin() const { return const_iterator(*this, 0); }
    virtual const_iterator end() const { return const_iterator(*this, size()); }
    virtual iterator begin() { return iterator(*this, 0); }
    virtual iterator end() { return iterator(*this, size()); }
    virtual void push_back(const B &v) = 0;
};

}
