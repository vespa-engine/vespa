// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryfeaturesdfw.h"
#include "docsumstate.h"
#include <vespa/searchlib/common/packets.h>
#include <vespa/vespalib/data/slime/cursor.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.summaryfeaturesdfw");

namespace search::docsummary {


SummaryFeaturesDFW::SummaryFeaturesDFW() :
    _env(nullptr)
{
}

SummaryFeaturesDFW::~SummaryFeaturesDFW() = default;

void
SummaryFeaturesDFW::init(IDocsumEnvironment * env)
{
    _env = env;
}

static vespalib::string _G_cached("vespa.summaryFeatures.cached");
static vespalib::Memory _M_cached("vespa.summaryFeatures.cached");

void
SummaryFeaturesDFW::insertField(uint32_t docid, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target)
{
    if ( ! state->_summaryFeatures) {
        state->_callback.FillSummaryFeatures(state, _env);
        if ( !state->_summaryFeatures) { // still no summary features to write
            return;
        }
    }
    const FeatureSet::StringVector &names = state->_summaryFeatures->getNames();
    const feature_t *values = state->_summaryFeatures->getFeaturesByDocId(docid);
    if (type == RES_FEATUREDATA && values != nullptr) {
        vespalib::slime::Cursor& obj = target.insertObject();
        for (uint32_t i = 0; i < names.size(); ++i) {
            vespalib::Memory name(names[i].c_str(), names[i].size());
            obj.setDouble(name, values[i]);
        }
        if (state->_summaryFeaturesCached) {
            obj.setDouble(_M_cached, 1.0);
        } else {
            obj.setDouble(_M_cached, 0.0);
        }
        return;
    }
    vespalib::JSONStringer & json(state->_jsonStringer);
    if (values != nullptr) {
        json.clear();
        json.beginObject();
        for (uint32_t i = 0; i < names.size(); ++i) {
            featureDump(json, names[i], values[i]);
        }
        json.appendKey(_G_cached);
        if (state->_summaryFeaturesCached) {
            json.appendDouble(1.0);
        } else {
            json.appendDouble(0.0);
        }
        json.endObject();
        vespalib::Memory value(json.toString().data(), json.toString().size());
        if (type == RES_STRING || type == RES_LONG_STRING) {
            target.insertString(value);
        }
        if (type == RES_DATA || type == RES_LONG_DATA) {
            target.insertData(value);
        }
        json.clear();
    }
}

void FeaturesDFW::featureDump(vespalib::JSONStringer & json, vespalib::stringref name, double feature)
{
    json.appendKey(name);
    if (std::isnan(feature) || std::isinf(feature)) {
        json.appendNull();
    } else {
        json.appendDouble(feature);
    }
}

}
