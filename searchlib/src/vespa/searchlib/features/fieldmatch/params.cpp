// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "params.h"

#include <vespa/log/log.h>
LOG_SETUP(".features.fieldmatch.params");

namespace search::features::fieldmatch {

Params::Params() :
    _proximityLimit(10),
    _maxAlternativeSegmentations(1000),
    _maxOccurrences(100),
    _proximityCompletenessImportance(0.9f),
    _relatednessImportance(0.9f),
    _earlinessImportance(0.05f),
    _segmentProximityImportance(0.05f),
    _occurrenceImportance(0.05f),
    _fieldCompletenessImportance(0.05f),
    _proximityTable()
{
    feature_t table[] = { 0.01f, 0.02f, 0.03f, 0.04f, 0.06f, 0.08f, 0.12f, 0.17f, 0.24f, 0.33f, 1,
                      0.71f, 0.50f, 0.35f, 0.25f, 0.18f, 0.13f, 0.09f, 0.06f, 0.04f, 0.03f };
    for (uint32_t i = 0; i < _proximityLimit * 2 + 1; ++i) {
        _proximityTable.push_back(table[i]);
    }
}

bool
Params::valid()
{
    if (_proximityTable.size() != (_proximityLimit * 2 + 1)) {
        LOG(error, "Proximity table length is invalid. Proximity limit is %d, but table has only %zd elements "
            "(must be proximityLimit * 2 + 1).",
            _proximityLimit, _proximityTable.size());
        return false;
    }
    return true;
}

}
