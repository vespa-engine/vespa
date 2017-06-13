// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/searchsummary/docsummary/urlresult.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include "docsumfieldwriter.h"
#include <vespa/juniper/rpinterface.h>

namespace search {
namespace docsummary {

class JuniperDFW : public IDocsumFieldWriter
{
public:
    virtual bool Init(
            const char *fieldName,
            const char *langFieldName,
            const ResultConfig & config,
            const char *inputField);
protected:
    JuniperDFW(juniper::Juniper * juniper);
    virtual ~JuniperDFW();

    uint32_t                         _inputFieldEnumValue;
    std::unique_ptr<juniper::Config> _juniperConfig;
    uint32_t                         _langFieldEnumValue;
    juniper::Juniper                *_juniper;
private:
    virtual bool IsGenerated() const override { return false; }
    JuniperDFW(const JuniperDFW &);
    JuniperDFW & operator=(const JuniperDFW &);
};


class JuniperTeaserDFW : public JuniperDFW
{
public:
    virtual bool Init(
            const char *fieldName,
            const char *langFieldName,
            const ResultConfig & config,
            const char *inputField) override;
protected:
    JuniperTeaserDFW(juniper::Juniper * juniper) : JuniperDFW(juniper) { }
};


class DynamicTeaserDFW : public JuniperTeaserDFW
{
public:
    DynamicTeaserDFW(juniper::Juniper * juniper) : JuniperTeaserDFW(juniper) { }

    vespalib::string makeDynamicTeaser(uint32_t docid,
                                       GeneralResult *gres,
                                       GetDocsumsState *state);

    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target) override;
};

}  // namespace docsummary
}  // namespace search

