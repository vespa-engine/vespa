// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"
#include "docsumstore.h"
#include "juniperproperties.h"
#include "resultclass.h"
#include "resultconfig.h"
#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/fastlib/text/wordfolder.h>
#include <vespa/vespalib/stllike/string.h>

namespace search { class IAttributeManager; }

namespace vespalib { class Slime; }

namespace search::docsummary {

class KeywordExtractor;

static constexpr uint32_t SLIME_MAGIC_ID = 0x55555555;

/**
 * Interface for writing a docsum payload (in Slime) for a given document.
 */
class IDocsumWriter
{
public:
    using Inserter = vespalib::slime::Inserter;
    struct ResolveClassInfo {
        bool all_fields_generated;
        const ResultClass* res_class;
        ResolveClassInfo()
            : all_fields_generated(false),
              res_class(nullptr)
        { }
    };

    virtual ~IDocsumWriter() = default;
    virtual void initState(const search::IAttributeManager & attrMan, GetDocsumsState& state, const ResolveClassInfo& rci) = 0;
    virtual void insertDocsum(const ResolveClassInfo & rci, uint32_t docid, GetDocsumsState& state,
                              IDocsumStore &docinfos, Inserter & target) = 0;
    virtual ResolveClassInfo resolveClassInfo(vespalib::stringref class_name,
                                              const vespalib::hash_set<vespalib::string>& fields) const = 0;
};

//--------------------------------------------------------------------------

class DynamicDocsumWriter : public IDocsumWriter
{
private:
    std::unique_ptr<ResultConfig>                         _resultConfig;
    std::unique_ptr<KeywordExtractor>                     _keywordExtractor;

public:
    DynamicDocsumWriter(std::unique_ptr<ResultConfig> config, std::unique_ptr<KeywordExtractor> extractor);
    DynamicDocsumWriter(const DynamicDocsumWriter &) = delete;
    DynamicDocsumWriter& operator=(const DynamicDocsumWriter &) = delete;
    ~DynamicDocsumWriter() override;

    const ResultConfig *GetResultConfig() { return _resultConfig.get(); }

    void initState(const search::IAttributeManager & attrMan, GetDocsumsState& state, const ResolveClassInfo& rci) override;
    void insertDocsum(const ResolveClassInfo & outputClassInfo, uint32_t docid, GetDocsumsState& state,
                      IDocsumStore &docinfos, Inserter & inserter) override;

    ResolveClassInfo resolveClassInfo(vespalib::stringref class_name,
                                      const vespalib::hash_set<vespalib::string>& fields) const override;
};

}
