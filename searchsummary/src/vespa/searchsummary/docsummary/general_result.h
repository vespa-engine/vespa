// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resultclass.h"
#include "docsumstorevalue.h"

namespace search::docsummary {

class GeneralResult
{
private:
    GeneralResult(const GeneralResult &);
    GeneralResult& operator=(const GeneralResult &);

    const ResultClass      *_resClass;
    uint32_t                _entrycnt;
    ResEntry               *_entries;
    char                   *_buf;     // allocated in same chunk as _entries
    char                   *_bufEnd;  // first byte after _buf

    bool InBuf(const void *pt) const {
        return ((const char *)pt >= _buf &&
                (const char *)pt < _bufEnd);
    }

    void AllocEntries(uint32_t buflen, bool inplace = false);
    void FreeEntries();

public:
    GeneralResult(const ResultClass *resClass);
    ~GeneralResult();

    const ResultClass *GetClass() const { return _resClass; }
    ResEntry *GetEntry(uint32_t idx);
    ResEntry *GetEntry(const char *name);
    ResEntry *GetEntryFromEnumValue(uint32_t val);
    bool unpack(const char *buf, const size_t buflen);

    bool inplaceUnpack(const DocsumStoreValue &value) {
        if (value.valid()) {
            return unpack(value.fieldsPt(), value.fieldsSz());
        } else {
            return false;
        }
    }
};

}

