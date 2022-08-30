// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum_field_writer_factory.h"
#include "attribute_combiner_dfw.h"
#include "copy_dfw.h"
#include "document_id_dfw.h"
#include "empty_dfw.h"
#include "geoposdfw.h"
#include "idocsumenvironment.h"
#include "juniperdfw.h"
#include "matched_elements_filter_dfw.h"
#include "positionsdfw.h"
#include "rankfeaturesdfw.h"
#include "summaryfeaturesdfw.h"
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/vespalib/util/exceptions.h>

using vespalib::IllegalArgumentException;

namespace search::docsummary {

DocsumFieldWriterFactory::DocsumFieldWriterFactory(bool use_v8_geo_positions, IDocsumEnvironment& env)
    : _use_v8_geo_positions(use_v8_geo_positions),
      _env(env),
      _matching_elems_fields(std::make_shared<MatchingElementsFields>())
{
}

DocsumFieldWriterFactory::~DocsumFieldWriterFactory() = default;

bool
DocsumFieldWriterFactory::has_attribute_manager() const noexcept
{
    return getEnvironment().getAttributeManager() != nullptr;
}

std::unique_ptr<DocsumFieldWriter>
DocsumFieldWriterFactory::create_docsum_field_writer(const vespalib::string& fieldName, const vespalib::string& overrideName, const vespalib::string& argument, bool& rc)
{
    rc = false;
    std::unique_ptr<DocsumFieldWriter> fieldWriter;
    if (overrideName == "dynamicteaser") {
        if ( ! argument.empty() ) {
            auto fw = std::make_unique<DynamicTeaserDFW>(getEnvironment().getJuniper());
            auto fw_ptr = fw.get();
            fieldWriter = std::move(fw);
            rc = fw_ptr->Init(fieldName.c_str(), argument);
        } else {
            throw IllegalArgumentException("Missing argument");
        }
    } else if (overrideName == "summaryfeatures") {
        fieldWriter = std::make_unique<SummaryFeaturesDFW>();
        rc = true;
    } else if (overrideName == "rankfeatures") {
        fieldWriter = std::make_unique<RankFeaturesDFW>();
        rc = true;
    } else if (overrideName == "empty") {
        fieldWriter = std::make_unique<EmptyDFW>();
        rc = true;
    } else if (overrideName == "copy") {
        if ( ! argument.empty() ) {
            fieldWriter = std::make_unique<CopyDFW>(argument);
            rc = true;
        } else {
            throw IllegalArgumentException("Missing argument");
        }
    } else if (overrideName == "absdist") {
        if (has_attribute_manager()) {
            fieldWriter = AbsDistanceDFW::create(argument.c_str(), getEnvironment().getAttributeManager());
            rc = static_cast<bool>(fieldWriter);
        }
    } else if (overrideName == "positions") {
        if (has_attribute_manager()) {
            fieldWriter = PositionsDFW::create(argument.c_str(), getEnvironment().getAttributeManager(), _use_v8_geo_positions);
            rc = static_cast<bool>(fieldWriter);
        }
    } else if (overrideName == "geopos") {
        if (has_attribute_manager()) {
            fieldWriter = GeoPositionDFW::create(argument.c_str(), getEnvironment().getAttributeManager(), _use_v8_geo_positions);
            rc = static_cast<bool>(fieldWriter);
        }
    } else if (overrideName == "attribute") {
        if (has_attribute_manager()) {
            fieldWriter = AttributeDFWFactory::create(*getEnvironment().getAttributeManager(), argument);
            rc = true; // Allow missing attribute vector
        }
    } else if (overrideName == "attributecombiner") {
        if (has_attribute_manager()) {
            auto attr_ctx = getEnvironment().getAttributeManager()->createContext();
            const vespalib::string& source_field = argument.empty() ? fieldName : argument;
            fieldWriter = AttributeCombinerDFW::create(source_field, *attr_ctx, false, std::shared_ptr<MatchingElementsFields>());
            rc = static_cast<bool>(fieldWriter);
        }
    } else if (overrideName == "matchedattributeelementsfilter") {
        const vespalib::string& source_field = argument.empty() ? fieldName : argument;
        if (has_attribute_manager()) {
            auto attr_ctx = getEnvironment().getAttributeManager()->createContext();
            if (attr_ctx->getAttribute(source_field) != nullptr) {
                fieldWriter = AttributeDFWFactory::create(*getEnvironment().getAttributeManager(), source_field, true, _matching_elems_fields);
            } else {
                fieldWriter = AttributeCombinerDFW::create(source_field, *attr_ctx, true, _matching_elems_fields);
            }
            rc = static_cast<bool>(fieldWriter);
        }
    } else if (overrideName == "matchedelementsfilter") {
        const vespalib::string& source_field = argument.empty() ? fieldName : argument;
        if (has_attribute_manager()) {
            auto attr_ctx = getEnvironment().getAttributeManager()->createContext();
            fieldWriter = MatchedElementsFilterDFW::create(source_field,*attr_ctx, _matching_elems_fields);
            rc = static_cast<bool>(fieldWriter);
        }
    } else if (overrideName == "documentid") {
        fieldWriter = std::make_unique<DocumentIdDFW>();
        rc = true;
    } else {
        throw IllegalArgumentException("unknown override operation '" + overrideName + "' for field '" + fieldName + "'.");
    }
    return fieldWriter;
}

}
