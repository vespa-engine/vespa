// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resultclass.h"
#include "docsumstorevalue.h"

namespace document {
class FieldValue;
}

namespace search::docsummary {

class DocsumStoreDocument;

class GeneralResult
{
private:
    GeneralResult(const GeneralResult &);
    GeneralResult& operator=(const GeneralResult &);

    const ResultClass      *_resClass;
    uint32_t                _entrycnt;
    ResEntry               *_entries;
    const IDocsumStoreDocument* _document;

    void AllocEntries();
    void FreeEntries();

public:
    GeneralResult(const ResultClass *resClass);
    ~GeneralResult();

    const ResultClass *GetClass() const { return _resClass; }
    ResEntry *GetEntry(uint32_t idx) { return (idx < _entrycnt) ? &_entries[idx] : nullptr; }
    ResEntry *GetPresentEntry(uint32_t idx) {
        if (idx >= _entrycnt) {
            return nullptr;
        }
        ResEntry* entry = &_entries[idx];
        return entry->_not_present ? nullptr : entry;
    }
    ResEntry *GetPresentEntry(const char *name);
    ResEntry *GetPresentEntryFromEnumValue(uint32_t val);
    std::unique_ptr<document::FieldValue> get_field_value(const vespalib::string& field_name) const;
    bool unpack(const char *buf, const size_t buflen);

    bool inplaceUnpack(const DocsumStoreValue &value) {
        if (value.valid()) {
            _document = value.get_document();
            return unpack(value.fieldsPt(), value.fieldsSz());
        } else {
            return false;
        }
    }

    const IDocsumStoreDocument *get_document() const noexcept { return _document; }
};

}

