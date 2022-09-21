// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rankfeaturesdfw.h"
#include "docsumstate.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>

namespace search::docsummary {

RankFeaturesDFW::RankFeaturesDFW() = default;

RankFeaturesDFW::~RankFeaturesDFW() = default;

void
RankFeaturesDFW::insertField(uint32_t docid, GetDocsumsState& state,
                             vespalib::slime::Inserter &target) const
{
    if ( !state._rankFeatures ) {
        state._callback.fillRankFeatures(state);
        if (state._rankFeatures.get() == nullptr) { // still no rank features to write
            return;
        }
    }
    const FeatureSet::StringVector & names = state._rankFeatures->getNames();
    const FeatureSet::Value * values = state._rankFeatures->getFeaturesByDocId(docid);
    if (values == nullptr) { return; }

    vespalib::slime::Cursor& obj = target.insertObject();
    for (uint32_t i = 0; i < names.size(); ++i) {
        vespalib::Memory name(names[i].c_str(), names[i].size());
        if (values[i].is_data()) {
            obj.setData(name, values[i].as_data());
        } else {
            obj.setDouble(name, values[i].as_double());
        }
    }
}

}
