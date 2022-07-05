// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace document { class FieldValue; }

namespace search::docsummary {

/**
 * Interface class providing access to a document retrieved from an
 * IDocsumStore.  Some implementations (e.g. DocsumStoreVsmDocument) might
 * apply transforms when accessing some fields.
 **/
class IDocsumStoreDocument
{
public:
    virtual ~IDocsumStoreDocument() = default;
    virtual std::unique_ptr<document::FieldValue> get_field_value(const vespalib::string& field_name) const = 0;
};

}
