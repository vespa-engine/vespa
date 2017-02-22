// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/searchsummary/docsummary/urlresult.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/searchsummary/docsummary/docsumstore.h>
#include <vespa/searchsummary/docsummary/keywordextractor.h>
#include <vespa/searchsummary/docsummary/docsumfieldwriter.h>
#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/fastlib/text/wordfolder.h>
#include "juniperproperties.h"

using search::IAttributeManager;

namespace search {
namespace docsummary {

class IDocsumWriter
{
public:
    struct ResolveClassInfo {
        bool mustSkip;
        bool allGenerated;
        uint32_t outputClassId;
        const ResultClass *outputClass;
        const ResultClass::DynamicInfo *outputClassInfo;
        const ResultClass *inputClass;
        ResolveClassInfo()
            : mustSkip(false), allGenerated(false),
              outputClassId(ResultConfig::NoClassID()),
              outputClass(NULL), outputClassInfo(NULL), inputClass(NULL)
        { }
    };

    virtual ~IDocsumWriter() {}
    virtual void InitState(IAttributeManager & attrMan, GetDocsumsState *state) = 0;
    virtual uint32_t WriteDocsum(uint32_t docid,
                                 GetDocsumsState *state,
                                 IDocsumStore *docinfos,
                                 search::RawBuf *target) = 0;
    virtual void insertDocsum(const ResolveClassInfo & rci,
                              uint32_t docid,
                              GetDocsumsState *state,
                              IDocsumStore *docinfos,
                              vespalib::Slime & slime,
                              vespalib::slime::Inserter & target) = 0;
    virtual ResolveClassInfo resolveClassInfo(vespalib::stringref outputClassName, uint32_t inputClassId) const = 0;

    static uint32_t slime2RawBuf(const vespalib::Slime & slime, RawBuf & buf);
};

//--------------------------------------------------------------------------

class DynamicDocsumWriter : public IDocsumWriter
{
private:
    DynamicDocsumWriter(const DynamicDocsumWriter &);
    DynamicDocsumWriter& operator=(const DynamicDocsumWriter &);


private:
    ResultConfig        *_resultConfig;
    KeywordExtractor    *_keywordExtractor;
    uint32_t             _defaultOutputClass;
    uint32_t             _numClasses;
    uint32_t             _numEnumValues;
    ResultClass::DynamicInfo *_classInfoTable;
    IDocsumFieldWriter **_overrideTable;

    uint32_t WriteClassID(uint32_t classID, search::RawBuf *target);

    uint32_t GenerateDocsum(uint32_t docid,
                            GetDocsumsState *state,
                            const ResultClass *outputClass,
                            search::RawBuf *target);

    uint32_t RepackDocsum(GeneralResult *gres,
                          GetDocsumsState *state,
                          const ResultClass *outputClass,
                          search::RawBuf *target);

    void resolveInputClass(ResolveClassInfo &rci, uint32_t id) const;

    ResolveClassInfo resolveOutputClass(vespalib::stringref outputClassName) const;

public:
    DynamicDocsumWriter(ResultConfig *config, KeywordExtractor *extractor);
    virtual ~DynamicDocsumWriter();

    ResultConfig *GetResultConfig() { return _resultConfig; }

    bool SetDefaultOutputClass(uint32_t classID);
    bool Override(const char *fieldName, IDocsumFieldWriter *writer);
    void InitState(IAttributeManager & attrMan, GetDocsumsState *state) override;
    uint32_t WriteDocsum(uint32_t docid,
                         GetDocsumsState *state,
                         IDocsumStore *docinfos,
                         search::RawBuf *target) override;

    void insertDocsum(const ResolveClassInfo & outputClassInfo,
                      uint32_t docid,
                      GetDocsumsState *state,
                      IDocsumStore *docinfos,
                      vespalib::Slime & slime,
                      vespalib::slime::Inserter & target) override;

    ResolveClassInfo resolveClassInfo(vespalib::stringref outputClassName, uint32_t inputClassId) const override;
};

}
}

