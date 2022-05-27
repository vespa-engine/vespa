// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/jsonwriter.h>

namespace search::util {

class LogUtil {
public:
    /**
     * Extract the last num elements from the given path and
     * return a new path with these elements.
     **/
    static vespalib::string extractLastElements(const vespalib::string & path, size_t numElems);

    /**
     * Log the given directory (with size) to the given json stringer.
     *
     * @param jstr     the json stringer to log into.
     * @param path     the path of the directory to log.
     * @param numElems the last number of elements from the path to log.
     **/
    static void logDir(vespalib::JSONStringer & jstr, const vespalib::string & path, size_t numElems);
};

}
