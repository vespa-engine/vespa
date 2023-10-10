// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/data/fileheader.h>

namespace search {

/**
 * This class offers convenience methods to add tags to a GenericHeader.
 */
class FileHeaderTk {
public:
    /**
     * Adds all available version tags to the given header. These tags are set by the build environment and
     * describe things such as build time, build tag, builder, etc.
     *
     * @param header The header to add tags to.
     */
    static void addVersionTags(vespalib::GenericHeader &header);
};

}

