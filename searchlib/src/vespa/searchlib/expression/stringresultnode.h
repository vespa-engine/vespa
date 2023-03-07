// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "singleresultnode.h"

namespace search::expression {

class StringResultNode : public SingleResultNode
{
public:
    DECLARE_EXPRESSIONNODE(StringResultNode);
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    StringResultNode(const char * v="") noexcept : _value(v) { }
    StringResultNode(vespalib::stringref v) : _value(v) { }
    size_t hash() const override;
    int onCmp(const Identifiable & b) const override;
    void set(const ResultNode & rhs) override;
    StringResultNode & append(const ResultNode & rhs);
    StringResultNode & clear() { _value.clear(); return *this; }
    const vespalib::string & get() const { return _value; }
    void set(vespalib::stringref value) { _value = value; }
    void min(const ResultNode & b) override;
    void max(const ResultNode & b) override;
    void add(const ResultNode & b) override;
    void negate() override;
    const BucketResultNode& getNullBucket() const override;

private:
    int cmpMem(const void * a, const void *b) const override {
        return static_cast<const vespalib::string *>(a)->compare(*static_cast<const vespalib::string *>(b));
    }
    void create(void * buf)  const override {
        new (buf) vespalib::string();
    }
    void destroy(void * buf) const override {
        using string = vespalib::string;
        static_cast<string *>(buf)->string::~string();
    }

    void decode(const void * buf) override {
        _value = *static_cast<const vespalib::string *>(buf);
    }
    void encode(void * buf) const override {
        *static_cast<vespalib::string *>(buf) = _value;
    }
    void swap(void * buf) override {
        std::swap(*static_cast<vespalib::string *>(buf), _value);
    }
    size_t hash(const void * buf) const override;

    size_t onGetRawByteSize() const override { return sizeof(_value); }
    void setMin() override;
    void setMax() override;
    int64_t onGetInteger(size_t index) const override;
    double onGetFloat(size_t index)    const override;
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
    vespalib::string _value;
};

}
