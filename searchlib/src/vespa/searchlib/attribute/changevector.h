// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/util/memoryusage.h>
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
    enum {UNSET_ENTRY_REF = 0};

    ChangeBase() :
        _type(NOOP),
        _doc(0),
        _weight(1),
        _cached_entry_ref(UNSET_ENTRY_REF)
    { }

    ChangeBase(Type type, uint32_t d, int32_t w = 1) :
        _type(type),
        _doc(d),
        _weight(w),
        _cached_entry_ref(UNSET_ENTRY_REF)
    { }

    int cmp(const ChangeBase &b) const { int diff(_doc - b._doc); return diff; }
    bool operator <(const ChangeBase & b) const { return cmp(b) < 0; }
    uint32_t get_entry_ref() const { return _cached_entry_ref; }
    void set_entry_ref(uint32_t entry_ref) const { _cached_entry_ref = entry_ref; }
    bool has_entry_ref() const { return _cached_entry_ref != UNSET_ENTRY_REF; }
    void clear_entry_ref() const { _cached_entry_ref = UNSET_ENTRY_REF; }

    Type               _type;
    uint32_t           _doc;
    int32_t            _weight;
    mutable uint32_t   _cached_entry_ref;
};

template <typename T>
class NumericChangeData {
private:
    double  _arithOperand;
    T       _v;
public:
    typedef T DataType;

    NumericChangeData(T v) : _arithOperand(0), _v(v) { }
    NumericChangeData() : _arithOperand(0), _v(T()) { }

    double getArithOperand() const { return _arithOperand; }
    void setArithOperand(double operand)  { _arithOperand = operand; }
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

    T _data;
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

/**
 * Maintains a list of changes.
 * You can select to view the in insert order,
 * or unordered, but changes to the same docid are adjacent and ordered by insertion order.
 */
template <typename T>
class ChangeVectorT {
private:
    using Vector = std::vector<T>;
public:
    using const_iterator = typename Vector::const_iterator;
    ChangeVectorT();
    ~ChangeVectorT();
    void push_back(const T & c);
    template <typename Accessor>
    void push_back(uint32_t doc, Accessor & ac);
    T & back()                   { return _v.back(); }
    size_t size()          const { return _v.size(); }
    size_t capacity()      const { return _v.capacity(); }
    bool empty()           const { return _v.empty(); }
    void clear();
    class InsertOrder {
    public:
        InsertOrder(const Vector & v) : _v(v) { }
        const_iterator begin() const { return _v.begin(); }
        const_iterator end()   const { return _v.end(); }
    private:
        const Vector &_v;
    };
    class DocIdInsertOrder {
        using AdjacentDocIds = std::vector<uint64_t>;
    public:
        class const_iterator {
        public:
            const_iterator(const Vector & vector, const AdjacentDocIds & order,  uint32_t cur)
                : _v(&vector), _o(&order), _cur(cur) { }
            bool operator == (const const_iterator & rhs) const { return _v == rhs._v && _cur == rhs._cur; }
            bool operator != (const const_iterator & rhs) const { return _v != rhs._v || _cur != rhs._cur; }
            const_iterator& operator++()    { _cur++; return *this; }
            const_iterator  operator++(int) { const_iterator other(*this); _cur++; return other; }
            const T & operator *  ()  const { return v(); }
            const T * operator -> ()  const { return &v(); }
        private:
            const T & v() const { return (*_v)[(*_o)[_cur] & 0xffffffff]; }
            const Vector *         _v;
            const AdjacentDocIds * _o;
            uint32_t               _cur;
        };
        DocIdInsertOrder(const Vector & v);
        const_iterator begin() const { return const_iterator(_v, _adjacent, 0); }
        const_iterator end()   const { return const_iterator(_v, _adjacent, _v.size()); }
    private:
        const Vector   &_v;
        AdjacentDocIds  _adjacent;
    };
    InsertOrder getInsertOrder() const { return InsertOrder(_v); }
    DocIdInsertOrder getDocIdInsertOrder() const { return DocIdInsertOrder(_v); }
    vespalib::MemoryUsage getMemoryUsage() const;
private:
    Vector _v;
};

} // namespace search
