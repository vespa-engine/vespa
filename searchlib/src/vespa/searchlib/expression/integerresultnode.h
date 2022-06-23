// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numericresultnode.h"
#include <vespa/vespalib/util/sort.h>
#include <limits>
#include <type_traits>

namespace search::expression {

class BucketResultNode;

class IntegerResultNode : public NumericResultNode
{
public:
    DECLARE_ABSTRACT_RESULTNODE(IntegerResultNode);

    virtual const BucketResultNode& getNullBucket() const override;
};

template <typename T>
class IntegerResultNodeT : public IntegerResultNode
{
public:
    IntegerResultNodeT(int64_t v=0) : _value(v) { }
    size_t hash() const override { return _value; }
    int onCmp(const Identifiable & b) const override {
        T bv(static_cast<const IntegerResultNodeT &>(b)._value);
        return (_value < bv) ? -1 : (_value > bv) ? 1 : 0;
    }
    void add(const ResultNode & b) override { _value = uint64_t(_value) + uint64_t(b.getInteger()); }
    void negate() override { _value = - _value; }
    void multiply(const ResultNode & b) override {
        if constexpr (std::is_same_v<T, bool>) {
         _value = (_value && (b.getInteger() != 0));
       } else {
         _value *= b.getInteger();
       }
    }
    void divide(const ResultNode & b) override {
        int64_t val = b.getInteger();
        _value = (val == 0) ? 0 : (_value / val);
    }
    void modulo(const ResultNode & b) override {
        int64_t val = b.getInteger();
        _value = (val == 0) ? 0 : (_value % val);
    }
    void min(const ResultNode & b) override {
        int64_t t(b.getInteger());
        if (t < _value) { _value = t; }
    }
    void max(const ResultNode & b) override {
        int64_t t(b.getInteger());
        if (t > _value) { _value = t; }
    }
    void set(const ResultNode & rhs) override { _value = rhs.getInteger(); }
    void andOp(const ResultNode & b) { _value &= b.getInteger(); }
    void  orOp(const ResultNode & b) { _value |= b.getInteger(); }
    void xorOp(const ResultNode & b) { _value ^= b.getInteger(); }
    int64_t get()      const { return _value; }
    void set(int64_t value)  { _value = value; }
    IntegerResultNode & operator ++() { _value++; return *this; }
    IntegerResultNode & operator +=(int64_t v) { _value += v; return *this; }

protected:
    void setValue(const T &value) { _value = value; }
    T getValue() const { return _value; }
private:
    int cmpMem(const void * a, const void *b) const override {
        const T & ai(*static_cast<const T *>(a));
        const T & bi(*static_cast<const T *>(b));
        return ai < bi ? -1 : ai == bi ? 0 : 1;
    }
    void create(void * buf)  const override { (void) buf; }
    void destroy(void * buf) const override { (void) buf; }
    void decode(const void * buf)  override { _value = *static_cast<const T *>(buf); }
    void encode(void * buf) const  override { *static_cast<T *>(buf) = _value; }
    void swap(void * buf)          override { std::swap(*static_cast<T *>(buf), _value); }
    size_t hash(const void * buf) const override { return *static_cast<const T *>(buf); }
    uint64_t  radixAsc(const void * buf) const override {
        return vespalib::convertForSort<T,  true>::convert(*static_cast<const T *>(buf));
    }
    uint64_t radixDesc(const void * buf) const override {
        return vespalib::convertForSort<T, false>::convert(*static_cast<const T *>(buf));
    }
    size_t onGetRawByteSize() const override { return sizeof(_value); }
    void setMin() override { _value = std::numeric_limits<T>::min(); }
    void setMax() override { _value = std::numeric_limits<T>::max(); }
    vespalib::Serializer & onSerialize(vespalib::Serializer & os) const override { return os << _value; }
    vespalib::Deserializer & onDeserialize(vespalib::Deserializer & is) override { return is >> _value; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override { visit(visitor, "value", _value); }
    int64_t onGetInteger(size_t index) const override { (void) index; return _value; }
    double onGetFloat(size_t index)    const override { (void) index; return _value; }
    T _value;
};

class BoolResultNode : public IntegerResultNodeT<bool>
{
private:
    using Base = IntegerResultNodeT<bool>;
public:
    DECLARE_RESULTNODE(BoolResultNode);
    BoolResultNode(bool v=false) : Base(v) { }
    bool getBool() const { return getValue(); }
private:
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
};

class Int8ResultNode : public IntegerResultNodeT<int8_t>
{
private:
    typedef IntegerResultNodeT<int8_t> Base;
public:
    DECLARE_RESULTNODE(Int8ResultNode);
    Int8ResultNode(int8_t v=0) : Base(v) { }
private:
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
};

class Int16ResultNode : public IntegerResultNodeT<int16_t>
{
private:
    typedef IntegerResultNodeT<int16_t> Base;
public:
    DECLARE_RESULTNODE(Int16ResultNode);
    Int16ResultNode(int16_t v=0) : Base(v) { }
private:
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
};

class Int32ResultNode : public IntegerResultNodeT<int32_t>
{
private:
    typedef IntegerResultNodeT<int32_t> Base;
public:
    DECLARE_RESULTNODE(Int32ResultNode);
    Int32ResultNode(int32_t v=0) : Base(v) { }
private:
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
};

class Int64ResultNode : public IntegerResultNodeT<int64_t>
{
private:
    typedef IntegerResultNodeT<int64_t> Base;
public:
    DECLARE_RESULTNODE(Int64ResultNode);
    Int64ResultNode(int64_t v=0) : Base(v) { }
private:
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
};

}
