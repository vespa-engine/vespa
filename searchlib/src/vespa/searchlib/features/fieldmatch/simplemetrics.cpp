// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplemetrics.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace search::features::fieldmatch {

SimpleMetrics::SimpleMetrics(const Params & params) :
    _params(params),
    _matches(0),
    _matchesWithPosOcc(0),
    _matchWithInvalidFieldLength(false),
    _numTerms(0),
    _matchedWeight(0),
    _totalWeightInField(0),
    _totalWeightInQuery(0)
{
}

vespalib::string SimpleMetrics::toString() const
{
    vespalib::asciistream ss;
    ss << "matches(" << _matches << "), matchedWithPosOcc(" << _matchesWithPosOcc << "), ";
    ss << "matchWithInvalidFieldLength(" << (_matchWithInvalidFieldLength ? "true" : "false") << "), ";
    ss << "numTerms(" << _numTerms << "), ";
    ss << "matchedWeight(" << _matchedWeight << "), totalWeightInField(" << _totalWeightInField << "), ";
    ss << "totalWeightInQuery(" << _totalWeightInQuery << ")";
    return ss.str();
}

}
