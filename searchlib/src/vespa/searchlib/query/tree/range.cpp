// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "range.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace search::query {

Range::Range(int64_t f, int64_t t)
{
    vespalib::asciistream ost;
    ost << "[" << f << ";" << t << "]";
    _range = ost.str();
}

vespalib::asciistream &operator<<(vespalib::asciistream &out, const Range &range)
{
    return out << range.getRangeString();
}

}
