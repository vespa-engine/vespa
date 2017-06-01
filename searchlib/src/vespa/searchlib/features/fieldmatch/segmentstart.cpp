// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "segmentstart.h"
#include "computer.h"
#include "metrics.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace search {
namespace features {
namespace fieldmatch {

SegmentStart::SegmentStart(Computer *owner, const Metrics & metrics, uint32_t previousJ, uint32_t i, uint32_t j) :
    _owner(owner),
    _metrics(metrics),
    _i(i),
    _skipI(0),
    _previousJ(previousJ),
    _semanticDistanceExplored(0),
    _open(true)
{
    if (j < std::numeric_limits<uint32_t>::max()) {
        exploredTo(j);
    }
}

void
SegmentStart::reset(const Metrics & metrics, uint32_t previousJ, uint32_t i, uint32_t j)
{
    _metrics = metrics;
    _i = i;
    _skipI = 0;
    _previousJ = previousJ;
    _semanticDistanceExplored = 0;
    _open = true;
    if (j < std::numeric_limits<uint32_t>::max()) {
        exploredTo(j);
    }
}

SegmentStart &
SegmentStart::exploredTo(uint32_t j)
{
    _semanticDistanceExplored = _owner->fieldIndexToSemanticDistance(j, _previousJ) + 1;
    return *this;
}

bool
SegmentStart::offerHistory(int previousJ, const Metrics & metrics)
{
    if (metrics.getSegmentationScore() <= _metrics.getSegmentationScore()) {
        if (_owner->isTracing()) {
            _owner->trace(vespalib::make_string("    Rejected offered history [score %f, ending at %d] at %s.\n",
                                                metrics.getSegmentationScore(),
                                                previousJ,
                                                toString().c_str()));
        }
        return false; // reject
    }

#if 0
    // Starting over like this achieves higher correctness if the match metric is dependent on relative distance between
    // segments but is more expensive
    if (_previousJ != previousJ) {
        semanticDistanceExplored = 0;
        open = true;
    }
#endif

    if (_owner->isTracing()) {
        _owner->trace(vespalib::make_string("    Accepted offered history [score %f, ending at %d] at %s.\n",
                                            metrics.getSegmentationScore(),
                                            previousJ,
                                            toString().c_str()));
    }
    _previousJ = previousJ;
    _metrics = metrics; // take a copy of the given metrics
    return true; // accept
}

vespalib::string
SegmentStart::toString() {
    if (_i == _owner->getNumQueryTerms()) {
        return vespalib::make_string("Last segment: Complete match %f, previous j %d (%s).",
                                     _metrics.getMatch(),
                                     _previousJ,
                                     _open ? "open" : "closed");
    }
    else {
        return vespalib::make_string("Segment at %d: Match %f, previous j %d, explored to %d (%s).",
                                     _i,
                                     _metrics.getMatch(),
                                     _previousJ,
                                     _semanticDistanceExplored,
                                     _open ? "open" : "closed");
    }
}

} // fieldmatch
} // features
} // search
