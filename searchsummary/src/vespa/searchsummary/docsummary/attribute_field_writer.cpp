// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_field_writer.h"
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/stash.h>
#include <cassert>

using search::attribute::BasicType;
using search::attribute::IAttributeVector;
using search::attribute::getUndefined;
using search::attribute::IArrayReadView;
using vespalib::slime::Cursor;

namespace search::docsummary {

AttributeFieldWriter::AttributeFieldWriter(vespalib::Memory fieldName)
    : _fieldName(fieldName)
{
}

AttributeFieldWriter::~AttributeFieldWriter() = default;

namespace {

template <class BasicType>
class WriteField : public AttributeFieldWriter
{
protected:
    const IArrayReadView<BasicType>*   _array_read_view;
    vespalib::ConstArrayRef<BasicType> _content;

    WriteField(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash &stash);
    ~WriteField() override;
private:
    uint32_t fetch(uint32_t docId) override;
};

class WriteStringField : public WriteField<const char*>
{
public:
    WriteStringField(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash& stash);
    ~WriteStringField() override;
    void print(uint32_t idx, Cursor &cursor) override;
};

class WriteStringFieldNeverSkip : public WriteField<const char*>
{
public:
    WriteStringFieldNeverSkip(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash& stash)
        : WriteField(fieldName, attr, stash) {}
    ~WriteStringFieldNeverSkip() override = default;
    void print(uint32_t idx, Cursor &cursor) override;
};

template <typename BasicType>
class WriteFloatField : public WriteField<BasicType>
{
public:
    WriteFloatField(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash& stash);
    ~WriteFloatField() override;
    void print(uint32_t idx, Cursor &cursor) override;
};

template <typename BasicType>
class WriteIntField : public WriteField<BasicType>
{
public:
    WriteIntField(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash& stash);
    ~WriteIntField() override;
    void print(uint32_t idx, Cursor &cursor) override;
};

template <typename BasicType>
const search::attribute::IArrayReadView<BasicType>*
make_array_read_view(const IAttributeVector& attribute, vespalib::Stash& stash)
{
    auto multi_value_attribute = attribute.as_multi_value_attribute();
    if (multi_value_attribute != nullptr) {
        return multi_value_attribute->make_read_view(search::attribute::IMultiValueAttribute::ArrayTag<BasicType>(), stash);
    }
    return nullptr;
}

template <class BasicType>
WriteField<BasicType>::WriteField(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash& stash)
    : AttributeFieldWriter(fieldName),
      _array_read_view(make_array_read_view<BasicType>(attr, stash)),
      _content()
{
}

template <class BasicType>
WriteField<BasicType>::~WriteField() = default;

template <class BasicType>
uint32_t
WriteField<BasicType>::fetch(uint32_t docId)
{
    if (_array_read_view) {
        _content = _array_read_view->get_values(docId);
    }
    return _content.size();
}

WriteStringField::WriteStringField(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash& stash)
    : WriteField(fieldName, attr, stash)
{
}

WriteStringField::~WriteStringField() = default;

void
WriteStringField::print(uint32_t idx, Cursor &cursor)
{
    if (idx < _content.size()) {
        const char *s = _content[idx];
        if (s[0] != '\0') {
            cursor.setString(_fieldName, vespalib::Memory(s));
        }
    }
}

void
WriteStringFieldNeverSkip::print(uint32_t idx, Cursor &cursor)
{
    if (idx < _content.size()) {
        const char *s = _content[idx];
        cursor.setString(_fieldName, vespalib::Memory(s));
    } else {
        cursor.setString(_fieldName, vespalib::Memory(""));
    }
}

template <typename BasicType>
WriteFloatField<BasicType>::WriteFloatField(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash& stash)
    : WriteField<BasicType>(fieldName, attr, stash)
{
}

template <typename BasicType>
WriteFloatField<BasicType>::~WriteFloatField() = default;

template <typename BasicType>
void
WriteFloatField<BasicType>::print(uint32_t idx, Cursor &cursor)
{
    if (idx < this->_content.size()) {
        double val = this->_content[idx];
        if (!search::attribute::isUndefined(val)) {
            cursor.setDouble(this->_fieldName, val);
        }
    }
}

template <typename BasicType>
WriteIntField<BasicType>::WriteIntField(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash& stash)
    : WriteField<BasicType>(fieldName, attr, stash)
{
}

template <typename BasicType>
WriteIntField<BasicType>::~WriteIntField() = default;

template <typename BasicType>
void
WriteIntField<BasicType>::print(uint32_t idx, Cursor &cursor)
{
    if (idx < this->_content.size()) {
        auto val = this->_content[idx];
        if (val != getUndefined<BasicType>()) {
            cursor.setLong(this->_fieldName, val);
        }
    }
}

}

AttributeFieldWriter&
AttributeFieldWriter::create(vespalib::Memory fieldName, const IAttributeVector& attr, vespalib::Stash& stash, bool keep_empty_strings)
{
    switch (attr.getBasicType()) {
    case BasicType::INT8:
        return stash.create<WriteIntField<int8_t>>(fieldName, attr, stash);
    case BasicType::INT16:
        return stash.create<WriteIntField<int16_t>>(fieldName, attr, stash);
    case BasicType::INT32:
        return stash.create<WriteIntField<int32_t>>(fieldName, attr, stash);
    case BasicType::INT64:
        return stash.create<WriteIntField<int64_t>>(fieldName, attr, stash);
    case BasicType::FLOAT:
        return stash.create<WriteFloatField<float>>(fieldName, attr, stash);
    case BasicType::DOUBLE:
        return stash.create<WriteFloatField<double>>(fieldName, attr, stash);
    case BasicType::STRING:
        if (keep_empty_strings) {
            return stash.create<WriteStringFieldNeverSkip>(fieldName, attr, stash);
        } else {
            return stash.create<WriteStringField>(fieldName, attr, stash);
        }
    default:
        assert(false);
    }
}

}
