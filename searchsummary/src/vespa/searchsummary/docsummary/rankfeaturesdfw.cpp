// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/common/packets.h>
#include "rankfeaturesdfw.h"
#include <vespa/searchlib/common/feature.h>
#include "docsumformat.h"
#include "docsumstate.h"
#include <vespa/vespalib/data/slime/cursor.h>

namespace search {
namespace docsummary {

RankFeaturesDFW::RankFeaturesDFW() :
    _env(NULL)
{ }

RankFeaturesDFW::~RankFeaturesDFW() { }

void
RankFeaturesDFW::init(IDocsumEnvironment * env)
{
    _env = env;
}

uint32_t
RankFeaturesDFW::WriteField(uint32_t docid,
                            GeneralResult * gres,
                            GetDocsumsState * state,
                            ResType type,
                            search::RawBuf * target)
{
    (void) gres;

    if (state->_rankFeatures.get() == NULL) {
        state->_callback.FillRankFeatures(state, _env);
        if (state->_rankFeatures.get() == NULL) { // still no rank features to write
            return DocsumFormat::addEmpty(type, *target);
        }
    }

    uint32_t written = 0;

    const FeatureSet::StringVector & names = state->_rankFeatures->getNames();
    const feature_t * values = state->_rankFeatures->getFeaturesByDocId(docid);
    vespalib::JSONStringer & json(state->_jsonStringer);
    if (values != NULL) {
        json.clear();
        json.beginObject();
        for (uint32_t i = 0; i < names.size(); ++i) {
            featureDump(json, names[i], values[i]);
        }
        json.endObject();
        written += SummaryFeaturesDFW::writeString(json.toString(), type, target);
        json.clear();
    } else {
        written += DocsumFormat::addEmpty(type, *target);
    }

    return written;
}


void
RankFeaturesDFW::insertField(uint32_t docid,
                             GeneralResult *,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target)
{
    if (state->_rankFeatures.get() == NULL) {
        state->_callback.FillRankFeatures(state, _env);
        if (state->_rankFeatures.get() == NULL) { // still no rank features to write
            return;
        }
    }
    const FeatureSet::StringVector & names = state->_rankFeatures->getNames();
    const feature_t * values = state->_rankFeatures->getFeaturesByDocId(docid);
    if (type == RES_FEATUREDATA && values != NULL) {
        vespalib::slime::Cursor& obj = target.insertObject();
        for (uint32_t i = 0; i < names.size(); ++i) {
            vespalib::Memory name(names[i].c_str(), names[i].size());
            obj.setDouble(name, values[i]);
        }
        return;
    }
    vespalib::JSONStringer & json(state->_jsonStringer);
    if (values != NULL) {
        json.clear();
        json.beginObject();
        for (uint32_t i = 0; i < names.size(); ++i) {
            featureDump(json, names[i], values[i]);
        }
        json.endObject();
        vespalib::Memory value(json.toString().c_str(),
                                      json.toString().size());
        if (type == RES_STRING || type == RES_LONG_STRING) {
            target.insertString(value);
        }
        if (type == RES_DATA || type == RES_LONG_DATA) {
            target.insertData(value);
        }
        json.clear();
    }
}

}
}
