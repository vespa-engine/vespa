// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resultclass.h"
#include "docsumstorevalue.h"

namespace search::docsummary {

class urlresult
{
protected:
    uint32_t _partition;
    uint32_t _docid;
    HitRank  _metric;

public:
    urlresult(uint32_t partition, uint32_t docid, HitRank metric);
    virtual ~urlresult();

    virtual bool IsGeneral() const { return false; }
    uint32_t GetPartition() const  { return _partition; }
    uint32_t GetDocID() const  { return _docid; }
    HitRank GetMetric() const { return _metric; }
    virtual int unpack(const char *buf, const size_t buflen) = 0;
};


class badurlresult : public urlresult
{
public:
    badurlresult();
    badurlresult(uint32_t partition, uint32_t docid, HitRank metric);
    ~badurlresult() override;

    int unpack(const char *buf, const size_t buflen) override;
};


class GeneralResult : public urlresult
{
private:
    GeneralResult(const GeneralResult &);
    GeneralResult& operator=(const GeneralResult &);

    const ResultClass      *_resClass;
    uint32_t                _entrycnt;
    ResEntry               *_entries;
    char                   *_buf;     // allocated in same chunk as _entries
    char                   *_bufEnd;  // first byte after _buf

    bool InBuf(void *pt) {
        return ((char *)pt >= _buf &&
                (char *)pt < _bufEnd);
    }

    void AllocEntries(uint32_t buflen, bool inplace = false);
    void FreeEntries();

    bool _inplace_unpack(const char *buf, const size_t buflen);

public:
    GeneralResult(const ResultClass *resClass, uint32_t partition,
                  uint32_t docid, HitRank metric);
    ~GeneralResult();

    const ResultClass *GetClass() const { return _resClass; }
    ResEntry *GetEntry(uint32_t idx);
    ResEntry *GetEntry(const char *name);
    ResEntry *GetEntryFromEnumValue(uint32_t val);
    bool IsGeneral() const override { return true; }
    int unpack(const char *buf, const size_t buflen) override;

    bool inplaceUnpack(const DocsumStoreValue &value) {
        if (value.valid()) {
	    return _inplace_unpack(value.fieldsPt(), value.fieldsSz());
        } else {
            return false;
        }
    }
};

}

