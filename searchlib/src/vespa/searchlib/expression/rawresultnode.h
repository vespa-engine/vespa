// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/singleresultnode.h>

namespace search {
namespace expression {

class RawResultNode : public SingleResultNode
{
public:
    DECLARE_EXPRESSIONNODE(RawResultNode);
    DECLARE_NBO_SERIALIZE;
    DECLARE_RESULTNODE_SERIALIZE;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    RawResultNode() : _value(1) { setBuffer("", 0); }
    RawResultNode(const void * buf, size_t sz) { setBuffer(buf, sz); }
    virtual int onCmp(const Identifiable & b) const;
    virtual size_t hash() const;
    virtual void set(const ResultNode & rhs);
    void setBuffer(const void * buf, size_t sz);
    ConstBufferRef get() const { return ConstBufferRef(&_value[0], _value.size()); }
    virtual void min(const ResultNode & b);
    virtual void max(const ResultNode & b);
    virtual void add(const ResultNode & b);
    virtual void negate();
private:
    typedef std::vector<uint8_t> V;
    virtual int cmpMem(const void * a, const void *b) const {
        const V & ai(*static_cast<const V *>(a));
        const V & bi(*static_cast<const V *>(b));
        int result = memcmp(&ai[0], &bi[0], std::min(ai.size(), bi.size()));
        if (result == 0) {
            result = ai.size() < bi.size() ? -1 : ai.size() > bi.size() ? 1 : 0;
        }
        return result;
    }
    virtual void decode(const void * buf) { _value = *static_cast<const V *>(buf); }
    virtual void encode(void * buf) const { *static_cast<V *>(buf) = _value; }
    virtual size_t hash(const void * buf) const;

    virtual size_t onGetRawByteSize() const { return sizeof(_value); }
    virtual void setMin();
    virtual void setMax();
    virtual int64_t onGetInteger(size_t index) const;
    virtual double onGetFloat(size_t index)    const;
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const;
    V _value;
};

}
}

