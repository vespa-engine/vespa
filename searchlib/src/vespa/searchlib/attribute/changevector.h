// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vector>

namespace vespalib { class MemoryUsage; }

namespace search {

struct ChangeBase {
    enum Type {
        NOOP,
        UPDATE,
        APPEND,
        REMOVE,
        INCREASEWEIGHT,
        MULWEIGHT,
        DIVWEIGHT,
        SETWEIGHT,
        ADD,
        SUB,
        MUL,
        DIV,
        CLEARDOC
    };
    enum {TAIL=0, UNSET_ENUM = 0xffffffffu};

    ChangeBase() :
        _type(NOOP),
        _next(TAIL),
        _doc(0),
        _weight(1),
        _enumScratchPad(UNSET_ENUM),
        _arithOperand(0)
    { }

    ChangeBase(Type type, uint32_t d, int32_t w = 1) :
        _type(type),
        _next(TAIL),
        _doc(d),
        _weight(w),
        _enumScratchPad(UNSET_ENUM),
        _arithOperand(0)
    { }

    int cmp(const ChangeBase &b) const { int diff(_doc - b._doc); return diff; }
    bool operator <(const ChangeBase & b) const { return cmp(b) < 0; }
    bool isAtEnd() const { return _next == TAIL; }
    uint32_t getNext() const { return _next; }
    void setNext(uint32_t next) { _next = next; }
    uint32_t getEnum() const { return _enumScratchPad; }
    void setEnum(uint32_t value) const { _enumScratchPad = value; }
    bool isEnumValid() const { return _enumScratchPad != UNSET_ENUM; }
    void invalidateEnum() const { _enumScratchPad = UNSET_ENUM; }

    Type               _type;
private:
    uint32_t           _next;
public:
    uint32_t           _doc;
    int32_t            _weight;
    mutable uint32_t   _enumScratchPad;
    double             _arithOperand;
};

template <typename T>
class NumericChangeData {
private:
    T _v;
public:
    typedef T DataType;

    NumericChangeData(T v) : _v(v) { }
    NumericChangeData() : _v(T()) { }

    T get() const { return _v; }
    T raw() const { return _v; }
    operator T() const { return _v; }
    operator T&() { return _v; }
    bool operator<(const NumericChangeData<T> &rhs) const { return _v < rhs._v; }
};

class StringChangeData {
public:
    typedef vespalib::string DataType;

    StringChangeData(const DataType & s);
    StringChangeData() : _s() { }

    const DataType & get() const { return _s; }
    const char * raw() const { return _s.c_str(); }
    operator const DataType&() const { return _s; }
    operator DataType&() { return _s; }
    bool operator <(const StringChangeData & rhs) const { return _s < rhs._s; }
private:
    DataType _s;
};

template<typename T>
struct ChangeTemplate : public ChangeBase {
    typedef T DataType;

    ChangeTemplate() : ChangeBase() { }
    ChangeTemplate(Type type, uint32_t d, const T & v, int32_t w = 1) :
        ChangeBase(type, d, w), _data(v)
    { }

    T          _data;
};

template <>
inline
NumericChangeData<double>::NumericChangeData(double v) :
    _v(attribute::isUndefined<double>(v) ?  attribute::getUndefined<double>() : v)
{
}

template <>
inline bool
NumericChangeData<double>::operator<(const NumericChangeData<double> &rhs) const
{
    if (std::isnan(_v)) {
        return !std::isnan(rhs._v);
    }
    if (std::isnan(rhs._v)) {
        return false;
    }
    return _v < rhs._v;
}

class ChangeVectorBase {
protected:
};

/**
 * Maintains a list of changes where changes to the same docid are adjacent, but ordered by insertion order.
 * Apart from that no ordering by docid.
 */
template <typename T>
class ChangeVectorT : public ChangeVectorBase {
private:
    using Map = vespalib::hash_map<uint32_t, uint32_t>;
    using Vector = std::vector<T>;
public:
    ChangeVectorT();
    ~ChangeVectorT();
    class const_iterator {
    public:
        const_iterator(const Vector & vector, uint32_t next) : _v(&vector), _next(next) { }
        bool operator == (const const_iterator & rhs) const { return _v == rhs._v && _next == rhs._next; }
        bool operator != (const const_iterator & rhs) const { return _v != rhs._v || _next != rhs._next; }
        const_iterator& operator++()    { advance(); return *this; }
        const_iterator  operator++(int) { const_iterator other(*this); advance(); return other; }
        const T & operator *  ()  const { return v()[_next]; }
        const T * operator -> ()  const { return &v()[_next]; }
    private:
        void  advance()          { _next = v()[_next].getNext(); }
        const Vector & v() const { return *_v; }
        const Vector * _v;
        uint32_t       _next;
    };

    void push_back(const T & c);
    template <typename Accessor>
    void push_back(uint32_t doc, Accessor & ac);
    const T & back()       const { return _v.back(); }
    T & back()                   { return _v.back(); }
    size_t size()          const { return _v.size(); }
    bool empty()           const { return _v.empty(); }
    void clear();
    const_iterator begin() const { return const_iterator(_v, 0); }
    const_iterator end()   const { return const_iterator(_v, size()); }
    vespalib::MemoryUsage getMemoryUsage() const;
private:
    void linkIn(uint32_t doc, size_t index, size_t last);
    Vector   _v;
    Map      _docs;
    uint32_t _tail;
};

} // namespace search
