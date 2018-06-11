// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_field_writer.h"
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <cassert>

using search::attribute::BasicType;
using search::attribute::IAttributeVector;
using search::attribute::getUndefined;
using vespalib::slime::Cursor;

namespace search::docsummary {

AttributeFieldWriter::AttributeFieldWriter(vespalib::Memory fieldName,
                                           const IAttributeVector &attr)
    : _fieldName(fieldName),
      _attr(attr),
      _size(0)
{
}

AttributeFieldWriter::~AttributeFieldWriter() = default;

namespace {

template <class Content>
class WriteField : public AttributeFieldWriter
{
protected:
    Content _content;

    WriteField(vespalib::Memory fieldName, const IAttributeVector &attr);
    ~WriteField() override;
private:
    void fetch(uint32_t docId) override;
};

class WriteStringField : public WriteField<search::attribute::ConstCharContent>
{
public:
    WriteStringField(vespalib::Memory fieldName,
                     const IAttributeVector &attr);
    ~WriteStringField() override;
    void print(uint32_t idx, Cursor &cursor) override;
};


class WriteFloatField : public WriteField<search::attribute::FloatContent>
{
public:
    WriteFloatField(vespalib::Memory fieldName,
                    const IAttributeVector &attr);
    ~WriteFloatField() override;
    void print(uint32_t idx, Cursor &cursor) override;
};

class WriteIntField : public WriteField<search::attribute::IntegerContent>
{
    IAttributeVector::largeint_t _undefined;
public:
    WriteIntField(vespalib::Memory fieldName,
                  const IAttributeVector &attr,
                  IAttributeVector::largeint_t undefined);
    ~WriteIntField() override;
    void print(uint32_t idx, Cursor &cursor) override;
};

template <class Content>
WriteField<Content>::WriteField(vespalib::Memory fieldName, const IAttributeVector &attr)
    : AttributeFieldWriter(fieldName, attr),
      _content()
{
}

template <class Content>
WriteField<Content>::~WriteField() = default;

template <class Content>
void
WriteField<Content>::fetch(uint32_t docId)
{
    _content.fill(_attr, docId);
    _size = _content.size();
}

WriteStringField::WriteStringField(vespalib::Memory fieldName,
                                   const IAttributeVector &attr)
    : WriteField(fieldName, attr)
{
}

WriteStringField::~WriteStringField() = default;

void
WriteStringField::print(uint32_t idx, Cursor &cursor)
{
    if (idx < _size) {
        const char *s = _content[idx];
        if (s[0] != '\0') {
            cursor.setString(_fieldName, vespalib::Memory(s));
        }
    }
}

WriteFloatField::WriteFloatField(vespalib::Memory fieldName,
                                 const IAttributeVector &attr)
    : WriteField(fieldName, attr)
{
}

WriteFloatField::~WriteFloatField() = default;

void
WriteFloatField::print(uint32_t idx, Cursor &cursor)
{
    if (idx < _size) {
        double val = _content[idx];
        if (!search::attribute::isUndefined(val)) {
            cursor.setDouble(_fieldName, val);
        }
    }
}

WriteIntField::WriteIntField(vespalib::Memory fieldName,
                             const IAttributeVector &attr,
                             IAttributeVector::largeint_t undefined)
    : WriteField(fieldName, attr),
      _undefined(undefined)
{
}

WriteIntField::~WriteIntField() = default;

void
WriteIntField::print(uint32_t idx, Cursor &cursor)
{
    if (idx < _size) {
        auto val = _content[idx];
        if (val != _undefined) {
            cursor.setLong(_fieldName, _content[idx]);
        }
    }
}

}

std::unique_ptr<AttributeFieldWriter>
AttributeFieldWriter::create(vespalib::Memory fieldName, const IAttributeVector &attr)
{
    switch (attr.getBasicType()) {
    case BasicType::INT8:
        return std::make_unique<WriteIntField>(fieldName, attr, getUndefined<int8_t>());
    case BasicType::INT16:
        return std::make_unique<WriteIntField>(fieldName, attr, getUndefined<int16_t>());
    case BasicType::INT32:
        return std::make_unique<WriteIntField>(fieldName, attr, getUndefined<int32_t>());
    case BasicType::INT64:
        return std::make_unique<WriteIntField>(fieldName, attr, getUndefined<int64_t>());
    case BasicType::FLOAT:
    case BasicType::DOUBLE:
        return std::make_unique<WriteFloatField>(fieldName, attr);
    case BasicType::STRING:
        return std::make_unique<WriteStringField>(fieldName, attr);
    default:
        assert(false);
        abort();
    }
}

}
