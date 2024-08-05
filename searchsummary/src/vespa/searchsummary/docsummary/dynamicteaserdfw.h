// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

class DynamicTeaserDFW : public DocsumFieldWriter
{
public:
    DynamicTeaserDFW(const DynamicTeaserDFW &) = delete;
    DynamicTeaserDFW & operator = (const DynamicTeaserDFW &) = delete;
    explicit DynamicTeaserDFW(const juniper::Juniper * juniper, const char *fieldName, std::string_view inputField,
                              const IQueryTermFilterFactory& query_term_filter_factory) ;
    ~DynamicTeaserDFW() override;

    bool isGenerated() const override { return false; }
    void insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                     vespalib::slime::Inserter &target) const override;
    void insert_juniper_field(uint32_t docid, std::string_view input, GetDocsumsState& state,
                              vespalib::slime::Inserter& inserter) const;
private:
    const juniper::Juniper                 *_juniper;
    vespalib::string                        _input_field_name;
    std::unique_ptr<juniper::Config>        _juniperConfig;
    std::shared_ptr<const IQueryTermFilter> _query_term_filter;
};

}

