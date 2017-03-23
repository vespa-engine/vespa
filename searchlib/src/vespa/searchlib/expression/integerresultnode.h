// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numericresultnode.h"
#include <vespa/vespalib/util/sort.h>
#include <limits>

namespace search {
namespace expression {

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
    virtual size_t hash() const { return _value; }
    virtual int onCmp(const Identifiable & b) const {
        T bv(static_cast<const IntegerResultNodeT &>(b)._value);
        return (_value < bv) ? -1 : (_value > bv) ? 1 : 0;
    }
    virtual void add(const ResultNode & b)       { _value += b.getInteger(); }
    virtual void negate()                        { _value = - _value; }
    virtual void multiply(const ResultNode & b)  { _value *= b.getInteger(); }
    virtual void divide(const ResultNode & b)    {
        int64_t val = b.getInteger();
        _value = (val == 0) ? 0 : (_value / val);
    }
    virtual void modulo(const ResultNode & b)    {
        int64_t val = b.getInteger();
        _value = (val == 0) ? 0 : (_value % val);
    }
    virtual void min(const ResultNode & b)       { int64_t t(b.getInteger()); if (t < _value) { _value = t; } }
    virtual void max(const ResultNode & b)       { int64_t t(b.getInteger()); if (t > _value) { _value = t; } }
    virtual void set(const ResultNode & rhs) { _value = rhs.getInteger(); }
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
    virtual int cmpMem(const void * a, const void *b) const {
        const T & ai(*static_cast<const T *>(a));
        const T & bi(*static_cast<const T *>(b));
        return ai < bi ? -1 : ai == bi ? 0 : 1;
    }
    virtual void create(void * buf)  const  { (void) buf; }
    virtual void destroy(void * buf) const  { (void) buf; }
    virtual void decode(const void * buf)   { _value = *static_cast<const T *>(buf); }
    virtual void encode(void * buf) const   { *static_cast<T *>(buf) = _value; }
    virtual void swap(void * buf)           { std::swap(*static_cast<T *>(buf), _value); }
    virtual size_t hash(const void * buf) const { return *static_cast<const T *>(buf); }
    virtual uint64_t  radixAsc(const void * buf) const { return vespalib::convertForSort<T,  true>::convert(*static_cast<const T *>(buf)); }
    virtual uint64_t radixDesc(const void * buf) const { return vespalib::convertForSort<T, false>::convert(*static_cast<const T *>(buf)); }
    virtual size_t onGetRawByteSize() const { return sizeof(_value); }
    virtual void setMin() { _value = std::numeric_limits<T>::min(); }
    virtual void setMax() { _value = std::numeric_limits<T>::max(); }
    virtual vespalib::Serializer & onSerialize(vespalib::Serializer & os) const { return os << _value; }
    virtual vespalib::Deserializer & onDeserialize(vespalib::Deserializer & is) { return is >> _value; }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const { visit(visitor, "value", _value); }
    virtual int64_t onGetInteger(size_t index) const { (void) index; return _value; }
    virtual double onGetFloat(size_t index)    const { (void) index; return _value; }
    T _value;
};

class Int8ResultNode : public IntegerResultNodeT<int8_t>
{
private:
    typedef IntegerResultNodeT<int8_t> Base;
public:
    DECLARE_RESULTNODE(Int8ResultNode);
    Int8ResultNode(int8_t v=0) : Base(v) { }
private:
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const;
};

class Int16ResultNode : public IntegerResultNodeT<int16_t>
{
private:
    typedef IntegerResultNodeT<int16_t> Base;
public:
    DECLARE_RESULTNODE(Int16ResultNode);
    Int16ResultNode(int16_t v=0) : Base(v) { }
private:
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const;
};

class Int32ResultNode : public IntegerResultNodeT<int32_t>
{
private:
    typedef IntegerResultNodeT<int32_t> Base;
public:
    DECLARE_RESULTNODE(Int32ResultNode);
    Int32ResultNode(int32_t v=0) : Base(v) { }
private:
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const;
};

class Int64ResultNode : public IntegerResultNodeT<int64_t>
{
private:
    typedef IntegerResultNodeT<int64_t> Base;
public:
    DECLARE_RESULTNODE(Int64ResultNode);
    Int64ResultNode(int64_t v=0) : Base(v) { }
private:
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const;
};

}
}

