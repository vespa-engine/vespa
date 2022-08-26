// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"
#include <memory>

namespace juniper {
class Config;
class Juniper;
}
namespace vespalib::slime { struct Inserter; }

namespace search::docsummary {

class JuniperDFW : public DocsumFieldWriter
{
public:
    virtual bool Init(
            const char *fieldName,
            const vespalib::string& inputField);
protected:
    explicit JuniperDFW(juniper::Juniper * juniper);
    ~JuniperDFW() override;

    vespalib::string                 _input_field_name;
    std::unique_ptr<juniper::Config> _juniperConfig;
    juniper::Juniper                *_juniper;
private:
    bool IsGenerated() const override { return false; }
    JuniperDFW(const JuniperDFW &);
    JuniperDFW & operator=(const JuniperDFW &);
};


class JuniperTeaserDFW : public JuniperDFW
{
public:
    bool Init(const char *fieldName,
              const vespalib::string& inputField) override;
protected:
    explicit JuniperTeaserDFW(juniper::Juniper * juniper) : JuniperDFW(juniper) { }
};


class DynamicTeaserDFW : public JuniperTeaserDFW
{
    vespalib::string makeDynamicTeaser(uint32_t docid,
                                       vespalib::stringref input,
                                       GetDocsumsState *state) const;
public:
    explicit DynamicTeaserDFW(juniper::Juniper * juniper) : JuniperTeaserDFW(juniper) { }

    void insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState *state,
                     ResType type, vespalib::slime::Inserter &target) const override;
};

}

