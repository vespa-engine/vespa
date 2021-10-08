// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_combiner_dfw.h"
#include "docsumconfig.h"
#include "docsumwriter.h"
#include "geoposdfw.h"
#include "idocsumenvironment.h"
#include "juniperdfw.h"
#include "matched_elements_filter_dfw.h"
#include "positionsdfw.h"
#include "rankfeaturesdfw.h"
#include "textextractordfw.h"
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>

namespace search::docsummary {

using vespalib::IllegalArgumentException;
using vespalib::make_string;

const ResultConfig &
DynamicDocsumConfig::getResultConfig() const {
    return *_writer->GetResultConfig();
}

IDocsumFieldWriter::UP
DynamicDocsumConfig::createFieldWriter(const string & fieldName, const string & overrideName, const string & argument, bool & rc, std::shared_ptr<MatchingElementsFields> matching_elems_fields)
{
    const ResultConfig & resultConfig = getResultConfig();
    rc = false;
    IDocsumFieldWriter::UP fieldWriter;
    if (overrideName == "dynamicteaser") {
        if ( ! argument.empty() ) {
            const char *langFieldName = "something unused";
            DynamicTeaserDFW *fw = new DynamicTeaserDFW(getEnvironment()->getJuniper());
            fieldWriter.reset(fw);
            rc = fw->Init(fieldName.c_str(), langFieldName, resultConfig, argument.c_str());
        } else {
            throw IllegalArgumentException("Missing argument");
        }
    } else if (overrideName == "textextractor") {
        if ( ! argument.empty() ) {
            TextExtractorDFW * fw = new TextExtractorDFW();
            fieldWriter.reset(fw);
            rc = fw->init(fieldName, argument, resultConfig);
        } else {
            throw IllegalArgumentException("Missing argument");
        }
    } else if (overrideName == "summaryfeatures") {
        SummaryFeaturesDFW *fw = new SummaryFeaturesDFW();
        fieldWriter.reset(fw);
        fw->init(getEnvironment());
        rc = true;
    } else if (overrideName == "rankfeatures") {
        RankFeaturesDFW * fw = new RankFeaturesDFW();
        fw->init(getEnvironment());
        fieldWriter.reset(fw);
        rc = true;
    } else if (overrideName == "empty") {
        EmptyDFW *fw = new EmptyDFW();
        fieldWriter.reset(fw);
        rc = true;
    } else if (overrideName == "copy") {
        if ( ! argument.empty() ) {
            CopyDFW *fw = new CopyDFW();
            fieldWriter.reset(fw);
            rc = fw->Init(resultConfig, argument.c_str());
        } else {
            throw IllegalArgumentException("Missing argument");
        }
    } else if (overrideName == "absdist") {
        if (getEnvironment()) {
            IAttributeManager *am = getEnvironment()->getAttributeManager();
            fieldWriter = createAbsDistanceDFW(argument.c_str(), am);
            rc = fieldWriter.get();
        }
    } else if (overrideName == "positions") {
        if (getEnvironment()) {
            IAttributeManager *am = getEnvironment()->getAttributeManager();
            fieldWriter = createPositionsDFW(argument.c_str(), am);
            rc = fieldWriter.get();
        }
    } else if (overrideName == "geopos") {
        if (getEnvironment()) {
            IAttributeManager *am = getEnvironment()->getAttributeManager();
            fieldWriter = GeoPositionDFW::create(argument.c_str(), am);
            rc = fieldWriter.get();
        }
    } else if (overrideName == "attribute") {
        if (getEnvironment() && getEnvironment()->getAttributeManager()) {
            fieldWriter = AttributeDFWFactory::create(*getEnvironment()->getAttributeManager(), argument);
            rc = true; // Allow missing attribute vector
        }
    } else if (overrideName == "attributecombiner") {
        if (getEnvironment() && getEnvironment()->getAttributeManager()) {
            auto attr_ctx = getEnvironment()->getAttributeManager()->createContext();
            fieldWriter = AttributeCombinerDFW::create(fieldName, *attr_ctx, false, std::shared_ptr<MatchingElementsFields>());
            rc = static_cast<bool>(fieldWriter);
        }
    } else if (overrideName == "matchedattributeelementsfilter") {
        string source_field = argument.empty() ? fieldName : argument;
        if (getEnvironment() && getEnvironment()->getAttributeManager()) {
            auto attr_ctx = getEnvironment()->getAttributeManager()->createContext();
            if (attr_ctx->getAttribute(source_field) != nullptr) {
                fieldWriter = AttributeDFWFactory::create(*getEnvironment()->getAttributeManager(), source_field, true, matching_elems_fields);
            } else {
                fieldWriter = AttributeCombinerDFW::create(source_field, *attr_ctx, true, matching_elems_fields);
            }
            rc = static_cast<bool>(fieldWriter);
        }
    } else if (overrideName == "matchedelementsfilter") {
        string source_field = argument.empty() ? fieldName : argument;
        if (getEnvironment() && getEnvironment()->getAttributeManager()) {
            auto attr_ctx = getEnvironment()->getAttributeManager()->createContext();
            fieldWriter = MatchedElementsFilterDFW::create(source_field, resultConfig.GetFieldNameEnum().Lookup(source_field.c_str()),
                                                           *attr_ctx, matching_elems_fields);
            rc = static_cast<bool>(fieldWriter);
        }
    } else {
        throw IllegalArgumentException("unknown override operation '" + overrideName + "' for field '" + fieldName + "'.");
    }
    return fieldWriter;
}

void
DynamicDocsumConfig::configure(const vespa::config::search::SummarymapConfig &cfg)
{
    std::vector<string> strCfg;
    auto matching_elems_fields = std::make_shared<MatchingElementsFields>();
    if ((cfg.defaultoutputclass != -1) && !_writer->SetDefaultOutputClass(cfg.defaultoutputclass)) {
        throw IllegalArgumentException(make_string("could not set default output class to %d", cfg.defaultoutputclass));
    }
    for (size_t i = 0; i < cfg.override.size(); ++i) {
        const vespa::config::search::SummarymapConfig::Override & o = cfg.override[i];
        bool rc(false);
        IDocsumFieldWriter::UP fieldWriter = createFieldWriter(o.field, o.command, o.arguments, rc, matching_elems_fields);
        if (rc && fieldWriter.get() != NULL) {
            rc = _writer->Override(o.field.c_str(), fieldWriter.release()); // OBJECT HAND-OVER
        }
        if (!rc) {
            throw IllegalArgumentException(o.command + " override operation failed during initialization");
        }
    }
}

}
