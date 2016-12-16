// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include <vespa/searchsummary/docsummary/docsumconfig.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchsummary/docsummary/idocsumenvironment.h>
#include <vespa/searchsummary/docsummary/rankfeaturesdfw.h>
#include <vespa/searchsummary/docsummary/textextractordfw.h>
#include <vespa/searchsummary/docsummary/geoposdfw.h>
#include <vespa/searchsummary/docsummary/positionsdfw.h>
#include <vespa/searchsummary/docsummary/juniperdfw.h>
#include <vespa/vespalib/util/vstringfmt.h>
#include <vespa/vespalib/util/exceptions.h>

namespace search {
namespace docsummary {

using vespalib::IllegalArgumentException;
using vespalib::make_string;

const ResultConfig &
DynamicDocsumConfig::getResultConfig() const {
    return *_writer->GetResultConfig();
}

IDocsumFieldWriter::UP
DynamicDocsumConfig::createFieldWriter(const string & fieldName, const string & overrideName, const string & argument, bool & rc)
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
        const char *vectorName = argument.c_str();
        if (getEnvironment() && getEnvironment()->getAttributeManager()) {
            IDocsumFieldWriter *fw = AttributeDFWFactory::create(*getEnvironment()->getAttributeManager(), vectorName);
            fieldWriter.reset(fw);
            rc = fw != NULL;
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
    if ((cfg.defaultoutputclass != -1) && !_writer->SetDefaultOutputClass(cfg.defaultoutputclass)) {
        throw IllegalArgumentException(make_string("could not set default output class to %d", cfg.defaultoutputclass));
    }
    for (size_t i = 0; i < cfg.override.size(); ++i) {
        const vespa::config::search::SummarymapConfig::Override & o = cfg.override[i];
        // DYNAMIC TEASER
        bool rc(false);
        IDocsumFieldWriter::UP fieldWriter = createFieldWriter(o.field, o.command, o.arguments, rc);
        if (rc && fieldWriter.get() != NULL) {
            rc = _writer->Override(o.field.c_str(), fieldWriter.release()); // OBJECT HAND-OVER
        }
        if (!rc) {
            throw IllegalArgumentException(o.command + " override operation failed during initialization");
        }
    }
}


}
}
