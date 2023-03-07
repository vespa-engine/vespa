// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resultnode.h"
#include <vespa/searchcommon/attribute/iattributevector.h>

namespace search::expression {

class AttributeResult : public ResultNode
{
public:
    using UP = std::unique_ptr<AttributeResult>;
    DECLARE_RESULTNODE(AttributeResult);
    AttributeResult() : _attribute(nullptr), _docId(0) { }
    AttributeResult(const attribute::IAttributeVector * attribute, DocId docId)
        : _attribute(attribute),
          _docId(docId)
    { }
    void setDocId(DocId docId) { _docId = docId; }
    const search::attribute::IAttributeVector *getAttribute() const { return _attribute; }
    DocId getDocId() const { return _docId; }
protected:
    ConstBufferRef get_raw() const {
        auto raw = getAttribute()->get_raw(_docId);
        return {raw.data(), raw.size()};
    }
private:
    int64_t onGetInteger(size_t index) const override { (void) index; return _attribute->getInt(_docId); }
    double onGetFloat(size_t index)    const override { (void) index; return _attribute->getFloat(_docId); }
    ConstBufferRef onGetString(size_t, BufferRef) const override {
        return get_raw();
    }
    int64_t onGetEnum(size_t index) const override { (void) index; return (static_cast<int64_t>(_attribute->getEnum(_docId))); }
    void set(const search::expression::ResultNode&) override { }
    size_t hash() const override { return _docId; }

    const search::attribute::IAttributeVector *_attribute;
    DocId                                      _docId;
};

class IntegerAttributeResult : public AttributeResult {
public:
    DECLARE_RESULTNODE(IntegerAttributeResult);
    IntegerAttributeResult() : AttributeResult() {}
    IntegerAttributeResult(const attribute::IAttributeVector * attribute, DocId docId)
        : AttributeResult(attribute, docId)
    { }
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
};

class FloatAttributeResult : public AttributeResult {
public:
    DECLARE_RESULTNODE(FloatAttributeResult);
    FloatAttributeResult() : AttributeResult() {}
    FloatAttributeResult(const attribute::IAttributeVector * attribute, DocId docId)
        : AttributeResult(attribute, docId)
    { }
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
};

}
