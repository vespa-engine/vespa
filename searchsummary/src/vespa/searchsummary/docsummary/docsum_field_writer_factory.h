// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_docsum_field_writer_factory.h"

namespace search { class MatchingElementsFields; }

namespace search::docsummary {

class IDocsumEnvironment;
class IQueryTermFilterFactory;

/*
 * Factory class for creating docsum field writers.
 */
class DocsumFieldWriterFactory : public IDocsumFieldWriterFactory
{
    bool _use_v8_geo_positions;
    const IDocsumEnvironment& _env;
    const IQueryTermFilterFactory& _query_term_filter_factory;
protected:
    const IDocsumEnvironment& getEnvironment() const noexcept { return _env; }
    bool has_attribute_manager() const noexcept;
public:
    DocsumFieldWriterFactory(bool use_v8_geo_positions, const IDocsumEnvironment& env, const IQueryTermFilterFactory& query_term_filter_factory);
    ~DocsumFieldWriterFactory() override;
    std::unique_ptr<DocsumFieldWriter> create_docsum_field_writer(const vespalib::string& field_name,
                                                                  const vespalib::string& command,
                                                                  const vespalib::string& source,
                                                                  std::shared_ptr<MatchingElementsFields> matching_elems_fields) override;
};

}
