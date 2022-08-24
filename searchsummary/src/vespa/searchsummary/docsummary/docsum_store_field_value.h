// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>
#include <memory>

namespace search::docsummary {

/*
 * Class containing a field value returned from an IDocsumStoreDocument.
 */
class DocsumStoreFieldValue {
    const document::FieldValue*           _value;
    std::unique_ptr<document::FieldValue> _value_store;
public:
    explicit DocsumStoreFieldValue(std::unique_ptr<document::FieldValue> value) noexcept
        : _value(value.get()),
          _value_store(std::move(value))
    {
    }
    explicit DocsumStoreFieldValue(const document::FieldValue* value) noexcept
        : _value(value),
          _value_store()
    {
    }
    DocsumStoreFieldValue()
        : DocsumStoreFieldValue(nullptr)
    {
    }
    DocsumStoreFieldValue(DocsumStoreFieldValue&& rhs) noexcept = default;
    ~DocsumStoreFieldValue() = default;
    const document::FieldValue& operator*() const noexcept  { return *_value; }
    const document::FieldValue* operator->() const noexcept { return _value; }
    const document::FieldValue* get() const noexcept        { return _value; }
    operator bool () const noexcept { return _value != nullptr; }
};

}
