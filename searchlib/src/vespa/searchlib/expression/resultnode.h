// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressionnode.h"
#include "serializer.h"
#include <vespa/vespalib/util/buffer.h>

namespace search::expression {

class BucketResultNode;

#define DECLARE_ABSTRACT_RESULTNODE(Class) DECLARE_IDENTIFIABLE_ABSTRACT_NS2(search, expression, Class)
#define DECLARE_ABSTRACT_RESULTNODE_NS1(ns, Class) DECLARE_IDENTIFIABLE_ABSTRACT_NS3(search, expression, ns, Class)

#define DECLARE_RESULTNODE(Class)                   \
    DECLARE_IDENTIFIABLE_NS2(search, expression, Class) \
    Class * clone() const override;

#define DECLARE_RESULTNODE_NS1(ns, Class)               \
    DECLARE_IDENTIFIABLE_NS3(search, expression, ns, Class) \
    virtual Class * clone() const override;

#define DECLARE_RESULTNODE_SERIALIZE \
    ResultSerializer & onSerializeResult(ResultSerializer & os) const override; \
    ResultDeserializer & onDeserializeResult(ResultDeserializer & is) override;

#define IMPLEMENT_ABSTRACT_RESULTNODE(Class, base) IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(search, expression, Class, base)

#define IMPLEMENT_RESULTNODE(Class, base) \
    IMPLEMENT_IDENTIFIABLE_NS2(search, expression, Class, base) \
    Class * Class::clone() const { return new Class(*this); }

class ResultNode : public vespalib::Identifiable
{
public:
    using BufferRef = vespalib::BufferRef;
    using ConstBufferRef = vespalib::ConstBufferRef;
public:
    int64_t getInteger() const { return onGetInteger(0); }
    int64_t getEnum() const { return onGetEnum(0); }
    double    getFloat() const { return onGetFloat(0); }
    ConstBufferRef getString(BufferRef buf) const { return onGetString(0, buf); }

    int64_t getInteger(size_t index) const { return onGetInteger(index); }
    double    getFloat(size_t index) const { return onGetFloat(index); }
    ConstBufferRef getString(size_t index, BufferRef buf) const { return onGetString(index, buf); }

private:
    virtual int64_t onGetInteger(size_t index) const = 0;
    virtual int64_t onGetEnum(size_t index) const;
    virtual double    onGetFloat(size_t index) const = 0;
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const = 0;

public:
    DECLARE_ABSTRACT_RESULTNODE(ResultNode);
    using UP = std::unique_ptr<ResultNode>;
    using CP = vespalib::IdentifiablePtr<ResultNode>;
    virtual void set(const ResultNode & rhs) = 0;

    /**
     * Will initialize a memory area that must be destroyed. After creation it can be encoded or decoded.
     * Memory must be fixed size.
     * This interface is used to efficiently store data in vectors without the overhead of virtual objects.
     * @param memory area to initialize
     */
    virtual void create(void * buf) const;
    /**
     * Will initialize itself with the memory area supplied.
     * @param memory area containing alrady encoded data.
     */
    virtual void decode(const void * buf);
    /**
     * Will decode itself into the memory area supplied.
     * @param memory area used as storage.
     */
    virtual void encode(void * buf) const;
    /**
     * Will return a radixsortable value that will sort ascending.
     * @param memory area used as storage.
     */
    virtual uint64_t radixAsc(const void * buf) const;
    /**
     * Will return a radixsortable value that will sort descending.
     * @param memory area used as storage.
     */
    virtual uint64_t radixDesc(const void * buf) const;
    /**
     * Will return the typed hash of memory area supplied.
     * @param memory area used as storage.
     */
    virtual size_t hash(const void * buf) const;
    /**
     * Will decode itself into the memory area supplied.
     * It will also encode itself from the memory area.
     * @param memory area used as storage.
     */
    virtual void swap(void * buf);
    /**
     * Will destroy any initialized memory.
     * @param memory area used as storage.
     */
    virtual void destroy(void * buf) const;
    /**
     * Will do a typed compare of the given memory a and b.
     * @param a memory area of a
     * @param b memory area of b
     * @return -1 if a<b, 0 if a==b, and 1 if a>b
     */
    virtual int cmpMem(const void * a, const void *b) const;

    virtual void negate();
    virtual void sort();
    virtual void reverse();
    virtual size_t hash() const = 0;
    virtual ResultNode * clone() const = 0;
    ResultNode::UP createBaseType() const { return ResultNode::UP(static_cast<ResultNode *>(getBaseClass().create())); }
    virtual ResultSerializer & onSerializeResult(ResultSerializer & os) const;
    virtual ResultDeserializer & onDeserializeResult(ResultDeserializer & is);
    virtual size_t getRawByteSize() const;
    virtual bool isMultiValue() const { return false; }
    virtual const BucketResultNode& getNullBucket() const;
};

}
