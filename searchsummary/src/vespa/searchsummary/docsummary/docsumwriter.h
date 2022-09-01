// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "juniperproperties.h"
#include "resultclass.h"
#include "resultconfig.h"
#include "docsumstore.h"
#include "docsum_field_writer.h"
#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/fastlib/text/wordfolder.h>

namespace search {
    class IAttributeManager;
}

namespace vespalib { class Slime; }

namespace search::docsummary {

class KeywordExtractor;

static constexpr uint32_t SLIME_MAGIC_ID = 0x55555555;

class IDocsumWriter
{
public:
    using Inserter = vespalib::slime::Inserter;
    struct ResolveClassInfo {
        bool allGenerated;
        const ResultClass *outputClass;
        ResolveClassInfo()
            : allGenerated(false),
              outputClass(nullptr)
        { }
    };

    virtual ~IDocsumWriter() = default;
    virtual void InitState(const search::IAttributeManager & attrMan, GetDocsumsState *state) = 0;
    virtual void WriteDocsum(uint32_t docid, GetDocsumsState *state,
                             IDocsumStore *docinfos, Inserter & target) = 0;
    virtual void insertDocsum(const ResolveClassInfo & rci, uint32_t docid, GetDocsumsState *state,
                              IDocsumStore *docinfos, Inserter & target) = 0;
    virtual ResolveClassInfo resolveClassInfo(vespalib::stringref outputClassName) const = 0;
};

//--------------------------------------------------------------------------

class DynamicDocsumWriter : public IDocsumWriter
{
private:
    std::unique_ptr<ResultConfig>                         _resultConfig;
    std::unique_ptr<KeywordExtractor>                     _keywordExtractor;
    uint32_t                                              _numFieldWriterStates;
    std::vector<ResultClass::DynamicInfo>                 _classInfoTable;
    std::vector<std::unique_ptr<const DocsumFieldWriter>> _overrideTable;

    ResolveClassInfo resolveOutputClass(vespalib::stringref outputClassName) const;

public:
    DynamicDocsumWriter(std::unique_ptr<ResultConfig> config, std::unique_ptr<KeywordExtractor> extractor);
    DynamicDocsumWriter(const DynamicDocsumWriter &) = delete;
    DynamicDocsumWriter& operator=(const DynamicDocsumWriter &) = delete;
    ~DynamicDocsumWriter() override;

    const ResultConfig *GetResultConfig() { return _resultConfig.get(); }

    bool Override(const char *fieldName, std::unique_ptr<DocsumFieldWriter> writer);
    void InitState(const search::IAttributeManager & attrMan, GetDocsumsState *state) override;
    void WriteDocsum(uint32_t docid, GetDocsumsState *state,
                     IDocsumStore *docinfos, Inserter & inserter) override;

    void insertDocsum(const ResolveClassInfo & outputClassInfo, uint32_t docid, GetDocsumsState *state,
                      IDocsumStore *docinfos, Inserter & inserter) override;

    ResolveClassInfo resolveClassInfo(vespalib::stringref outputClassName) const override;
};

}
