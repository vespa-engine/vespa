// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_docsum_field_writer_factory.h"

namespace search { class MatchingElementsFields; }

namespace search::docsummary {

class IDocsumEnvironment;

/*
 * Factory class for creating docsum field writers.
 */
class DocsumFieldWriterFactory : public IDocsumFieldWriterFactory
{
    bool _use_v8_geo_positions;
    IDocsumEnvironment * _env;
protected:
    std::shared_ptr<MatchingElementsFields> _matching_elems_fields;
    IDocsumEnvironment * getEnvironment() { return _env; }
public:
    DocsumFieldWriterFactory(bool use_v8_geo_positions, IDocsumEnvironment* env);
    ~DocsumFieldWriterFactory() override;
    std::unique_ptr<DocsumFieldWriter> create_docsum_field_writer(const vespalib::string& fieldName, const vespalib::string& overrideName, const vespalib::string& argument, bool& rc) override;
};

}
