// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rankfeaturesdfw.h"
#include "docsumstate.h"
#include <vespa/searchlib/common/packets.h>
#include <vespa/vespalib/data/slime/cursor.h>

namespace search::docsummary {

RankFeaturesDFW::RankFeaturesDFW() :
    _env(nullptr)
{ }

RankFeaturesDFW::~RankFeaturesDFW() = default;

void
RankFeaturesDFW::init(IDocsumEnvironment * env)
{
    _env = env;
}

void
RankFeaturesDFW::insertField(uint32_t docid, GetDocsumsState *state,
                             ResType type, vespalib::slime::Inserter &target)
{
    if (state->_rankFeatures.get() == nullptr) {
        state->_callback.FillRankFeatures(state, _env);
        if (state->_rankFeatures.get() == nullptr) { // still no rank features to write
            return;
        }
    }
    const FeatureSet::StringVector & names = state->_rankFeatures->getNames();
    const FeatureSet::Value * values = state->_rankFeatures->getFeaturesByDocId(docid);
    if (type == RES_FEATUREDATA && values != nullptr) {
        vespalib::slime::Cursor& obj = target.insertObject();
        for (uint32_t i = 0; i < names.size(); ++i) {
            vespalib::Memory name(names[i].c_str(), names[i].size());
            if (values[i].is_data()) {
                obj.setData(name, values[i].as_data());
            } else {
                obj.setDouble(name, values[i].as_double());
            }
        }
        return;
    }
    vespalib::JSONStringer & json(state->jsonStringer());
    if (values != nullptr) {
        json.clear();
        json.beginObject();
        for (uint32_t i = 0; i < names.size(); ++i) {
            featureDump(json, names[i], values[i].as_double());
        }
        json.endObject();
        vespalib::Memory value(json.toString().data(),
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
