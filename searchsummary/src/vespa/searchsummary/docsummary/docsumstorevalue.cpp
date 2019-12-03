// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumstorevalue.h"
#include <vespa/document/fieldvalue/document.h>

namespace search::docsummary {

DocsumStoreValue::DocsumStoreValue()
    : _value(static_cast<const char*>(0), 0),
      _document()
{
}

DocsumStoreValue::DocsumStoreValue(const char *pt_, uint32_t len_)
    : _value(pt_, len_),
      _document()
{
}

DocsumStoreValue::DocsumStoreValue(const char *pt_, uint32_t len_, std::unique_ptr<document::Document> document_)
    : _value(pt_, len_),
      _document(std::move(document_))
{
}

DocsumStoreValue::~DocsumStoreValue() = default;

}
