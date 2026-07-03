// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/util/memoryusage.h>

#include <vector>

namespace vespalib {
class MemoryUsage;
}

namespace search {

struct ChangeBase {
    enum Type {
        NOOP,
        UPDATE,
        APPEND,
        REMOVE,
        ASSIGN_ELEMENT,
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
    enum { UNSET_ENTRY_REF = 0 };

    ChangeBase() : _type(NOOP), _doc(0), _weight(1), _element_index(0), _cached_entry_ref(UNSET_ENTRY_REF) {}

    ChangeBase(Type type, uint32_t d, int32_t w = 1)
        : _type(type), _doc(d), _weight(w), _element_index(0), _cached_entry_ref(UNSET_ENTRY_REF) {}

    [[nodiscard]] uint32_t get_entry_ref() const noexcept { return _cached_entry_ref; }
    void set_entry_ref(uint32_t entry_ref) const noexcept { _cached_entry_ref = entry_ref; }
    [[nodiscard]] bool has_entry_ref() const noexcept { return _cached_entry_ref != UNSET_ENTRY_REF; }
    void clear_entry_ref() const noexcept { _cached_entry_ref = UNSET_ENTRY_REF; }
    [[nodiscard]] uint32_t element_index() const noexcept { return _element_index; }
    void set_element_index(uint32_t index) noexcept { _element_index = index; }

    Type             _type;
    uint32_t         _doc;
    int32_t          _weight;
    uint32_t         _element_index;
    mutable uint32_t _cached_entry_ref;
};

template <typename T> class NumericChangeData {
private:
    double _arithOperand;
    T      _v;

public:
    using DataType = T;

    NumericChangeData(T v) : _arithOperand(0), _v(v) {}
    NumericChangeData() : _arithOperand(0), _v(T()) {}

    [[nodiscard]] double getArithOperand() const noexcept { return _arithOperand; }
    void setArithOperand(double operand) noexcept { _arithOperand = operand; }
    [[nodiscard]] T get() const noexcept { return _v; }
    [[nodiscard]] T raw() const noexcept { return _v; }
    operator T() const noexcept { return _v; }
    operator T&() noexcept { return _v; }
};

class StringChangeData {
public:
    using DataType = std::string;

    StringChangeData(std::string_view s) : StringChangeData(DataType(s)) {}
    StringChangeData(DataType s) noexcept;
    StringChangeData() noexcept : _s() {}

    [[nodiscard]] const DataType& get() const noexcept { return _s; }
    [[nodiscard]] const char* raw() const noexcept { return _s.c_str(); }
    operator const DataType&() const noexcept { return _s; }
    operator DataType&() noexcept { return _s; }

private:
    DataType _s;
};

template <typename T> struct ChangeTemplate : public ChangeBase {
    using DataType = T;

    ChangeTemplate() : ChangeBase() {}
    ChangeTemplate(Type type, uint32_t d, const T& v, int32_t w = 1) : ChangeBase(type, d, w), _data(v) {}

    T _data;
};

template <>
inline NumericChangeData<double>::NumericChangeData(double v)
    : _v(attribute::isUndefined<double>(v) ? attribute::getUndefined<double>() : v) {
}

/**
 * Maintains a list of changes.
 * You can select to view the in insert order,
 * or unordered, but changes to the same docid are adjacent and ordered by insertion order.
 */
template <typename T> class ChangeVectorT {
private:
    using Vector = std::vector<T>;

public:
    using const_iterator = typename Vector::const_iterator;
    ChangeVectorT();
    ~ChangeVectorT();
    void push_back(const T& c);
    template <typename Accessor> void push_back(uint32_t doc, Accessor& ac);
    [[nodiscard]] T& back() { return _v.back(); }
    [[nodiscard]] size_t size() const noexcept { return _v.size(); }
    [[nodiscard]] size_t capacity() const noexcept { return _v.capacity(); }
    [[nodiscard]] bool empty() const noexcept { return _v.empty(); }
    void clear();
    class InsertOrder {
    public:
        InsertOrder(const Vector& v) : _v(v) {}
        const_iterator begin() const noexcept { return _v.begin(); }
        const_iterator end() const noexcept { return _v.end(); }

    private:
        const Vector& _v;
    };
    class DocIdInsertOrder {
        using AdjacentDocIds = std::vector<uint64_t>;

    public:
        class const_iterator {
        public:
            const_iterator(const Vector& vector, const AdjacentDocIds& order, uint32_t cur)
                : _v(&vector), _o(&order), _cur(cur) {}
            [[nodiscard]] bool operator==(const const_iterator& rhs) const noexcept {
                return _v == rhs._v && _cur == rhs._cur;
            }
            [[nodiscard]] bool operator!=(const const_iterator& rhs) const noexcept {
                return _v != rhs._v || _cur != rhs._cur;
            }
            const_iterator& operator++() {
                _cur++;
                return *this;
            }
            const_iterator operator++(int) {
                const_iterator other(*this);
                _cur++;
                return other;
            }
            [[nodiscard]] const T& operator*() const { return v(); }
            [[nodiscard]] const T* operator->() const { return &v(); }

        private:
            const T& v() const { return (*_v)[(*_o)[_cur] & 0xffffffff]; }
            const Vector*         _v;
            const AdjacentDocIds* _o;
            uint32_t              _cur;
        };
        DocIdInsertOrder(const Vector& v);
        const_iterator begin() const noexcept { return const_iterator(_v, _adjacent, 0); }
        const_iterator end() const noexcept { return const_iterator(_v, _adjacent, _v.size()); }

    private:
        const Vector&  _v;
        AdjacentDocIds _adjacent;
    };
    [[nodiscard]] InsertOrder getInsertOrder() const noexcept { return InsertOrder(_v); }
    [[nodiscard]] DocIdInsertOrder getDocIdInsertOrder() const { return DocIdInsertOrder(_v); }
    [[nodiscard]] vespalib::MemoryUsage getMemoryUsage() const;

private:
    Vector _v;
};

} // namespace search
