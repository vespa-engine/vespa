// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vsm/vsm/docsumconfig.h>
#include <vespa/searchsummary/docsummary/copy_dfw.h>
#include <vespa/searchsummary/docsummary/empty_dfw.h>
#include <vespa/searchsummary/docsummary/matched_elements_filter_dfw.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/vsm/config/config-vsmfields.h>
#include <vespa/vsm/config/config-vsmsummary.h>

using search::MatchingElementsFields;
using search::docsummary::DocsumFieldWriter;
using search::docsummary::CopyDFW;
using search::docsummary::EmptyDFW;
using search::docsummary::MatchedElementsFilterDFW;
using search::docsummary::ResultConfig;
using vespa::config::search::vsm::VsmfieldsConfig;
using vespa::config::search::vsm::VsmsummaryConfig;

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

DynamicDocsumConfig::DynamicDocsumConfig(search::docsummary::IDocsumEnvironment* env, search::docsummary::DynamicDocsumWriter* writer, std::shared_ptr<VsmfieldsConfig> vsm_fields_config)
    : Parent(env, writer),
      _vsm_fields_config(std::move(vsm_fields_config))
{
}

std::unique_ptr<DocsumFieldWriter>
DynamicDocsumConfig::createFieldWriter(const string & fieldName, const string & overrideName, const string & argument, bool & rc, std::shared_ptr<search::MatchingElementsFields> matching_elems_fields)
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
        string source_field = argument.empty() ? fieldName : argument;
        const ResultConfig& resultConfig = getResultConfig();
        int source_field_enum = resultConfig.GetFieldNameEnum().Lookup(source_field.c_str());
        populate_fields(*matching_elems_fields, *_vsm_fields_config, source_field);
        fieldWriter = MatchedElementsFilterDFW::create(source_field, source_field_enum, matching_elems_fields);
        rc = static_cast<bool>(fieldWriter);
    } else {
        fieldWriter = search::docsummary::DynamicDocsumConfig::createFieldWriter(fieldName, overrideName, argument, rc, matching_elems_fields);
    }
    return fieldWriter;
}

}
