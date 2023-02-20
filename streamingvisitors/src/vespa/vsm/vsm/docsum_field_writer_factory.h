// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/docsum_field_writer_factory.h>
#include <vespa/vsm/config/config-vsmfields.h>

namespace vsm {

/*
 * Factory interface class for creating docsum field writers, adjusted for
 * streaming search.
 */
class DocsumFieldWriterFactory : public search::docsummary::DocsumFieldWriterFactory
{
    const vespa::config::search::vsm::VsmfieldsConfig& _vsm_fields_config;

public:
    DocsumFieldWriterFactory(bool use_v8_geo_positions, const search::docsummary::IDocsumEnvironment& env, const search::docsummary::IQueryTermFilterFactory& query_term_filter_factory, const vespa::config::search::vsm::VsmfieldsConfig& vsm_fields_config);
    ~DocsumFieldWriterFactory() override;
    std::unique_ptr<search::docsummary::DocsumFieldWriter>
    create_docsum_field_writer(const vespalib::string& field_name,
                               const vespalib::string& command,
                               const vespalib::string& source) override;
};

}
