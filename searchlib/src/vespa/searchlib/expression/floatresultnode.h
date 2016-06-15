// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/numericresultnode.h>
#include <vespa/vespalib/util/sort.h>

namespace search {
namespace expression {

class FloatResultNode : public NumericResultNode
{
public:
    DECLARE_EXPRESSIONNODE(FloatResultNode);
    DECLARE_NBO_SERIALIZE;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    FloatResultNode(double v=0) : _value(v) { }
    virtual size_t hash() const { size_t tmpHash(0); memcpy(&tmpHash, &_value, sizeof(tmpHash)); return tmpHash; }
    virtual int onCmp(const Identifiable & b) const;
    virtual void add(const ResultNode & b);
    virtual void negate();
    virtual void multiply(const ResultNode & b);
    virtual void divide(const ResultNode & b);
    virtual void modulo(const ResultNode & b);
    virtual void min(const ResultNode & b);
    virtual void max(const ResultNode & b);
    virtual void set(const ResultNode & rhs);
    double get() const { return _value; }
    void set(double value) { _value = value; }
private:
    virtual int cmpMem(const void * a, const void *b) const {
        const double & ai(*static_cast<const double *>(a));
        const double & bi(*static_cast<const double *>(b));
        return ai < bi ? -1 : ai == bi ? 0 : 1;
    }
    virtual void create(void * buf)  const { (void) buf; }
    virtual void destroy(void * buf) const { (void) buf; }
    virtual void decode(const void * buf) { _value = *static_cast<const double *>(buf); }
    virtual void encode(void * buf) const { *static_cast<double *>(buf) = _value; }
    virtual void swap(void * buf) { std::swap(*static_cast<double *>(buf), _value); }
    virtual size_t hash(const void * buf) const { size_t tmpHash(0); memcpy(&tmpHash, buf, sizeof(tmpHash)); return tmpHash; }
    virtual uint64_t  radixAsc(const void * buf) const { return vespalib::convertForSort<double,  true>::convert(*static_cast<const double *>(buf)); }
    virtual uint64_t radixDesc(const void * buf) const { return vespalib::convertForSort<double, false>::convert(*static_cast<const double *>(buf)); }

    virtual size_t onGetRawByteSize() const { return sizeof(_value); }
    bool isNan() const;
    virtual void setMin();
    virtual void setMax();
    virtual int64_t onGetInteger(size_t index) const;
    virtual double onGetFloat(size_t index)    const;
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const;
    double _value;
};

}
}

