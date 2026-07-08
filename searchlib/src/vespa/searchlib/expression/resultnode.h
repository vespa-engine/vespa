// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressionnode.h"
#include "serializer.h"

#include <vespa/vespalib/util/buffer.h>

#include <string_view>

namespace search::expression {

class BucketResultNode;

#define DECLARE_ABSTRACT_RESULTNODE(Class) DECLARE_IDENTIFIABLE_ABSTRACT_NS2(search, expression, Class)
#define DECLARE_ABSTRACT_RESULTNODE_NS1(ns, Class) DECLARE_IDENTIFIABLE_ABSTRACT_NS3(search, expression, ns, Class)

#define DECLARE_RESULTNODE(Class)                       \
    DECLARE_IDENTIFIABLE_NS2(search, expression, Class) \
    Class* clone() const override;

#define DECLARE_RESULTNODE_NS1(ns, Class)                   \
    DECLARE_IDENTIFIABLE_NS3(search, expression, ns, Class) \
    Class* clone() const override;

#define DECLARE_RESULTNODE_SERIALIZE                                          \
    ResultSerializer& onSerializeResult(ResultSerializer& os) const override; \
    ResultDeserializer& onDeserializeResult(ResultDeserializer& is) override;

#define IMPLEMENT_ABSTRACT_RESULTNODE(Class, base)                       \
    IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(search, expression, Class, base)

#define IMPLEMENT_RESULTNODE(Class, base)                       \
    IMPLEMENT_IDENTIFIABLE_NS2(search, expression, Class, base) \
    Class* Class::clone() const {                               \
        return new Class(*this);                                \
    }

/**
 * Common base type for all values in the expression and aggregation/grouping subsystem.
 *
 * Concrete subclasses represent integers, floats, strings, raw bytes, buckets (ranges), and vectors of these. Every
 * ExpressionNode produces a ResultNode, and the grouping engine uses ResultNodes as group identifiers and aggregation
 * accumulators.
 */
class ResultNode : public vespalib::Identifiable {
public:
    using BufferRef = vespalib::BufferRef;
    using ConstBufferRef = vespalib::ConstBufferRef;

public:
    int64_t getInteger() const { return onGetInteger(0); }
    int64_t getEnum() const { return onGetEnum(0); }
    double getFloat() const { return onGetFloat(0); }
    ConstBufferRef getString(BufferRef buf) const { return onGetString(0, buf); }

    int64_t getInteger(size_t index) const { return onGetInteger(index); }
    double getFloat(size_t index) const { return onGetFloat(index); }
    ConstBufferRef getString(size_t index, BufferRef buf) const { return onGetString(index, buf); }

    /**
     * Friendly type name that can be displayed to user.
     */
    [[nodiscard]] virtual std::string_view friendly_type_name() const noexcept = 0;

private:
    virtual int64_t onGetInteger(size_t index) const = 0;
    virtual int64_t onGetEnum(size_t index) const;
    virtual double onGetFloat(size_t index) const = 0;
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const = 0;

public:
    DECLARE_ABSTRACT_RESULTNODE(ResultNode);
    using UP = std::unique_ptr<ResultNode>;
    using CP = vespalib::IdentifiablePtr<ResultNode>;
    virtual void set(const ResultNode& rhs) = 0;
    virtual void negate();
    virtual void sort();
    virtual void reverse();
    virtual size_t hash() const = 0;
    virtual ResultNode* clone() const = 0;
    ResultNode::UP createBaseType() const {
        return ResultNode::UP(static_cast<ResultNode*>(getBaseClass().create()));
    }
    virtual ResultSerializer& onSerializeResult(ResultSerializer& os) const;
    virtual ResultDeserializer& onDeserializeResult(ResultDeserializer& is);
    virtual bool isMultiValue() const { return false; }
    virtual const BucketResultNode& getNullBucket() const;
};

/**
 * Uniform access to the string form of any ResultNode.
 *
 * The internal ConstBufferRef is a non-owning view that points to memory possibly owned elsewhere.
 * For string/raw nodes it points into the ResultNode's own storage.
 * For numeric nodes it points into _num_buf, a scratch buffer owned by this object.
 */
class HoldString {
public:
    HoldString(const ResultNode& rv, size_t idx = 0) { _res = rv.getString(idx, {_num_buf, sizeof(_num_buf)}); }
    HoldString(HoldString&&) = delete;
    HoldString(const HoldString&) = delete;
    HoldString& operator=(HoldString&&) = delete;
    HoldString& operator=(const HoldString&) = delete;
    const char* data() const noexcept { return _res.c_str(); }
    size_t size() const noexcept { return _res.size(); }
    operator vespalib::ConstBufferRef() const noexcept { return _res; }
    operator std::string_view() const noexcept { return {data(), size()}; }

private:
    vespalib::ConstBufferRef _res;
    char                     _num_buf[32]; // for numbers converted to string
};

} // namespace search::expression
