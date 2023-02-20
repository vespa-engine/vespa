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

class IQueryTermFilter;
class IQueryTermFilterFactory;

class JuniperDFW : public DocsumFieldWriter
{
public:
    virtual bool Init(
            const char *fieldName,
            const vespalib::string& inputField,
            const IQueryTermFilterFactory& query_term_filter_factory);
protected:
    explicit JuniperDFW(const juniper::Juniper * juniper);
    ~JuniperDFW() override;

    vespalib::string                 _input_field_name;
    std::unique_ptr<juniper::Config> _juniperConfig;
    const juniper::Juniper          *_juniper;
    std::shared_ptr<const IQueryTermFilter> _query_term_filter;
private:
    bool isGenerated() const override { return false; }
    JuniperDFW(const JuniperDFW &);
    JuniperDFW & operator=(const JuniperDFW &);
};


class JuniperTeaserDFW : public JuniperDFW
{
public:
    bool Init(const char *fieldName,
              const vespalib::string& inputField,
              const IQueryTermFilterFactory& query_term_filter_factory) override;
protected:
    explicit JuniperTeaserDFW(const juniper::Juniper * juniper) : JuniperDFW(juniper) { }
};


class DynamicTeaserDFW : public JuniperTeaserDFW
{
public:
    explicit DynamicTeaserDFW(const juniper::Juniper * juniper) : JuniperTeaserDFW(juniper) { }

    void insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                     vespalib::slime::Inserter &target) const override;
    void insert_juniper_field(uint32_t docid, vespalib::stringref input, GetDocsumsState& state, vespalib::slime::Inserter& inserter) const;
};

}

