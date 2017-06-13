// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buildid.h"
#include <vespa/vespalib/component/vtag.h>

const char *storage::spi::getBuildId() {
    return vespalib::VersionTagComponent;
}
