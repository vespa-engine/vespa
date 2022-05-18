// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "types.h"
#include <vespa/vespalib/objects/nbostream.h>

namespace storage::spi {

DEFINE_PRIMITIVE_WRAPPER_NBOSTREAM(NodeIndex);
DEFINE_PRIMITIVE_WRAPPER_NBOSTREAM(IteratorId);
DEFINE_PRIMITIVE_WRAPPER_NBOSTREAM(Timestamp);
DEFINE_PRIMITIVE_WRAPPER_NBOSTREAM(BucketChecksum);

}
