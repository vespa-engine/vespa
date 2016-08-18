// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class vdstestlib::SourceDir
 * \ingroup cppunit
 *
 * \brief Helper class for determining source directory when running out of source
 *
 * When running the tests outside of the source tree, it must be possible to know
 * where the source is. This information is used to read input and reference files
 * belonging to the tests.
 */
#pragma once

#include <string>

namespace vdstestlib {
    struct SourceDir {
        static const std::string& get()
        {
            static const char* envSrcDir = getenv("SOURCE_DIRECTORY");
            static const std::string srcDir = envSrcDir ? std::string(envSrcDir) + "/" : "./";
            return srcDir;
        }
    };
}

