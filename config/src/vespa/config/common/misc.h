// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configkey.h"
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <memory>

namespace vespalib {
    class Slime;
    namespace slime {
        struct Inspector;
        struct Cursor;
    }
}

namespace config {

/**
 * Miscellaneous utility functions specific to config.
 */
vespalib::string calculateContentMd5(const std::vector<vespalib::string> & fileContents);

bool isGenerationNewer(int64_t newGen, int64_t oldGen);

// Helper for throwing invalid config exception
void throwInvalid(const char *fmt, ...)
    __attribute__((format(printf, 1, 2))) __attribute__((noreturn));

typedef std::shared_ptr<const vespalib::Slime> SlimePtr;

/**
 * Copy slime objects from under src to dest, recursively.
 */
void copySlimeObject(const vespalib::slime::Inspector & src, vespalib::slime::Cursor & dest);

}
