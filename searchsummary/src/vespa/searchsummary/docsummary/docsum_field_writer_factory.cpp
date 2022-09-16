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

DocsumFieldWriterFactory::DocsumFieldWriterFactory(bool use_v8_geo_positions, const IDocsumEnvironment& env)
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

namespace {

void
throw_if_nullptr(const std::unique_ptr<DocsumFieldWriter>& writer,
                 const vespalib::string& command)
{
    if (writer.get() == nullptr) {
        throw IllegalArgumentException("Failed to create docsum field writer for command '" + command + "'.");
    }
}

void
throw_missing_source(const vespalib::string& command)
{
    throw IllegalArgumentException("Missing source for command '" + command + "'.");
}

}

std::unique_ptr<DocsumFieldWriter>
DocsumFieldWriterFactory::create_docsum_field_writer(const vespalib::string& field_name,
                                                     const vespalib::string& command,
                                                     const vespalib::string& source)
{
    std::unique_ptr<DocsumFieldWriter> fieldWriter;
    if (command == "dynamicteaser") {
        if ( ! source.empty() ) {
            auto fw = std::make_unique<DynamicTeaserDFW>(getEnvironment().getJuniper());
            auto fw_ptr = fw.get();
            fieldWriter = std::move(fw);
            if (!fw_ptr->Init(field_name.c_str(), source)) {
                throw IllegalArgumentException("Failed to initialize DynamicTeaserDFW.");
            }
        } else {
            throw_missing_source(command);
        }
    } else if (command == "summaryfeatures") {
        fieldWriter = std::make_unique<SummaryFeaturesDFW>();
    } else if (command == "rankfeatures") {
        fieldWriter = std::make_unique<RankFeaturesDFW>();
    } else if (command == "empty") {
        fieldWriter = std::make_unique<EmptyDFW>();
    } else if (command == "copy") {
        if ( ! source.empty() ) {
            fieldWriter = std::make_unique<CopyDFW>(source);
        } else {
            throw_missing_source(command);
        }
    } else if (command == "absdist") {
        if (has_attribute_manager()) {
            fieldWriter = AbsDistanceDFW::create(source.c_str(), getEnvironment().getAttributeManager());
            throw_if_nullptr(fieldWriter, command);
        }
    } else if (command == "positions") {
        if (has_attribute_manager()) {
            fieldWriter = PositionsDFW::create(source.c_str(), getEnvironment().getAttributeManager(), _use_v8_geo_positions);
            throw_if_nullptr(fieldWriter, command);
        }
    } else if (command == "geopos") {
        if (has_attribute_manager()) {
            fieldWriter = GeoPositionDFW::create(source.c_str(), getEnvironment().getAttributeManager(), _use_v8_geo_positions);
            throw_if_nullptr(fieldWriter, command);
        }
    } else if (command == "attribute") {
        if (has_attribute_manager()) {
            fieldWriter = AttributeDFWFactory::create(*getEnvironment().getAttributeManager(), source);
            // Missing attribute vector is allowed, so throw_if_nullptr() is NOT used.
        }
    } else if (command == "attributecombiner") {
        if (has_attribute_manager()) {
            auto attr_ctx = getEnvironment().getAttributeManager()->createContext();
            const vespalib::string& source_field = source.empty() ? field_name : source;
            fieldWriter = AttributeCombinerDFW::create(source_field, *attr_ctx, false, std::shared_ptr<MatchingElementsFields>());
            throw_if_nullptr(fieldWriter, command);
        }
    } else if (command == "matchedattributeelementsfilter") {
        const vespalib::string& source_field = source.empty() ? field_name : source;
        if (has_attribute_manager()) {
            auto attr_ctx = getEnvironment().getAttributeManager()->createContext();
            if (attr_ctx->getAttribute(source_field) != nullptr) {
                fieldWriter = AttributeDFWFactory::create(*getEnvironment().getAttributeManager(), source_field, true, _matching_elems_fields);
            } else {
                fieldWriter = AttributeCombinerDFW::create(source_field, *attr_ctx, true, _matching_elems_fields);
            }
            throw_if_nullptr(fieldWriter, command);
        }
    } else if (command == "matchedelementsfilter") {
        const vespalib::string& source_field = source.empty() ? field_name : source;
        if (has_attribute_manager()) {
            auto attr_ctx = getEnvironment().getAttributeManager()->createContext();
            fieldWriter = MatchedElementsFilterDFW::create(source_field,*attr_ctx, _matching_elems_fields);
            throw_if_nullptr(fieldWriter, command);
        }
    } else if (command == "documentid") {
        fieldWriter = std::make_unique<DocumentIdDFW>();
    } else {
        throw IllegalArgumentException("Unknown command '" + command + "'.");
    }
    return fieldWriter;
}

}
