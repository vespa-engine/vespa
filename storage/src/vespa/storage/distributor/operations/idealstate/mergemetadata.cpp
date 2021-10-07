// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mergemetadata.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {
namespace distributor {

vespalib::asciistream& operator<<(vespalib::asciistream& out, const MergeMetaData& e)
{
    return out << "MergeMetaData(" << e._nodeIndex << ")";
}

} // distributor
} // storage

