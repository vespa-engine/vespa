// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryfeaturesdfw.h"
#include "docsumstate.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>

using vespalib::FeatureSet;

namespace search::docsummary {


SummaryFeaturesDFW::SummaryFeaturesDFW() = default;

SummaryFeaturesDFW::~SummaryFeaturesDFW() = default;

static vespalib::Memory _M_cached("vespa.summaryFeatures.cached");

void
SummaryFeaturesDFW::insertField(uint32_t docid, GetDocsumsState& state, vespalib::slime::Inserter &target) const
{
    if (state._omit_summary_features) {
        return;
    }
    if ( ! state._summaryFeatures) {
        state._callback.fillSummaryFeatures(state);
        if ( !state._summaryFeatures) { // still no summary features to write
            return;
        }
    }
    const FeatureSet::StringVector &names = state._summaryFeatures->getNames();
    const FeatureSet::Value *values = state._summaryFeatures->getFeaturesByDocId(docid);
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
    if (state._summaryFeaturesCached) {
        obj.setDouble(_M_cached, 1.0);
    } else {
        obj.setDouble(_M_cached, 0.0);
    }
}

}
