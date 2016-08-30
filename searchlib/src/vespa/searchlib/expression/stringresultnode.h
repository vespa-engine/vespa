// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/singleresultnode.h>

namespace search {
namespace expression {

class StringResultNode : public SingleResultNode
{
public:
    DECLARE_EXPRESSIONNODE(StringResultNode);
    DECLARE_NBO_SERIALIZE;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    StringResultNode(const char * v="") : _value(v) { }
    StringResultNode(const vespalib::stringref & v) : _value(v) { }
    virtual size_t hash() const;
    virtual int onCmp(const Identifiable & b) const;
    virtual void set(const ResultNode & rhs);
    StringResultNode & append(const ResultNode & rhs);
    StringResultNode & clear() { _value.clear(); return *this; }
    const vespalib::string & get() const { return _value; }
    void set(const vespalib::stringref & value) { _value = value; }
    virtual void min(const ResultNode & b);
    virtual void max(const ResultNode & b);
    virtual void add(const ResultNode & b);
    virtual void negate();
    virtual const BucketResultNode& getNullBucket() const override;

private:
    virtual int cmpMem(const void * a, const void *b) const {
        return static_cast<const vespalib::string *>(a)->compare(*static_cast<const vespalib::string *>(b));
    }
    virtual void create(void * buf)  const { new (buf) vespalib::string(); }
    virtual void destroy(void * buf) const { static_cast<vespalib::string *>(buf)->vespalib::string::~string(); }

    virtual void decode(const void * buf) { _value = *static_cast<const vespalib::string *>(buf); }
    virtual void encode(void * buf) const { *static_cast<vespalib::string *>(buf) = _value; }
    virtual void swap(void * buf) { std::swap(*static_cast<vespalib::string *>(buf), _value); }
    virtual size_t hash(const void * buf) const;

    virtual size_t onGetRawByteSize() const { return sizeof(_value); }
    virtual void setMin();
    virtual void setMax();
    virtual int64_t onGetInteger(size_t index) const;
    virtual double onGetFloat(size_t index)    const;
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const;
    vespalib::string _value;
};

}
}

