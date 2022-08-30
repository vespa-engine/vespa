// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum_field_writer_factory.h"
#include <vespa/searchsummary/docsummary/copy_dfw.h>
#include <vespa/searchsummary/docsummary/docsum_field_writer.h>
#include <vespa/searchsummary/docsummary/empty_dfw.h>
#include <vespa/searchsummary/docsummary/matched_elements_filter_dfw.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/vsm/config/config-vsmfields.h>

using search::MatchingElementsFields;
using search::docsummary::CopyDFW;
using search::docsummary::EmptyDFW;
using search::docsummary::IDocsumEnvironment;
using search::docsummary::DocsumFieldWriter;
using search::docsummary::MatchedElementsFilterDFW;
using vespa::config::search::vsm::VsmfieldsConfig;

namespace vsm {

namespace {

void populate_fields(MatchingElementsFields& fields, VsmfieldsConfig& fields_config, const vespalib::string& field_name)
{
    vespalib::string prefix = field_name + ".";
    for (const auto& spec : fields_config.fieldspec) {
        if (spec.name.substr(0, prefix.size()) == prefix) {
            fields.add_mapping(field_name, spec.name);
        }
        if (spec.name == field_name) {
            fields.add_field(field_name);
        }
    }
}

}

DocsumFieldWriterFactory::DocsumFieldWriterFactory(bool use_v8_geo_positions, const IDocsumEnvironment& env, const vespa::config::search::vsm::VsmfieldsConfig& vsm_fields_config)
    : search::docsummary::DocsumFieldWriterFactory(use_v8_geo_positions, env),
      _vsm_fields_config(vsm_fields_config)
{
}

DocsumFieldWriterFactory::~DocsumFieldWriterFactory() = default;

std::unique_ptr<DocsumFieldWriter>
DocsumFieldWriterFactory::create_docsum_field_writer(const vespalib::string& fieldName, const vespalib::string& overrideName, const vespalib::string& argument, bool& rc)
{
    std::unique_ptr<DocsumFieldWriter> fieldWriter;
    if ((overrideName == "staticrank") ||
        (overrideName == "ranklog") ||
        (overrideName == "label") ||
        (overrideName == "project") ||
        (overrideName == "positions") ||
        (overrideName == "absdist") ||
        (overrideName == "subproject"))
    {
        fieldWriter = std::make_unique<EmptyDFW>();
        rc = true;
    } else if ((overrideName == "attribute") ||
               (overrideName == "attributecombiner")) {
        if (!argument.empty() && argument != fieldName) {
            fieldWriter = std::make_unique<CopyDFW>(argument);
        }
        rc = true;
    } else if (overrideName == "geopos") {
        rc = true;
    } else if ((overrideName == "matchedattributeelementsfilter") ||
               (overrideName == "matchedelementsfilter")) {
        vespalib::string source_field = argument.empty() ? fieldName : argument;
        populate_fields(*_matching_elems_fields, _vsm_fields_config, source_field);
        fieldWriter = MatchedElementsFilterDFW::create(source_field, _matching_elems_fields);
        rc = static_cast<bool>(fieldWriter);
    } else {
        return search::docsummary::DocsumFieldWriterFactory::create_docsum_field_writer(fieldName, overrideName, argument, rc);
    }
    return fieldWriter;
}

}
