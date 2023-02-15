// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"
#include <memory>

namespace vespalib {
    class asciistream;
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
vespalib::string calculateContentXxhash64(const StringVector & fileContents);

bool isGenerationNewer(int64_t newGen, int64_t oldGen);

// Helper for throwing invalid config exception
[[noreturn]] void throwInvalid(const char *fmt, ...) __attribute__((format(printf, 1, 2)));

typedef std::shared_ptr<const vespalib::Slime> SlimePtr;

/**
 * Copy slime objects from under src to dest, recursively.
 */
void copySlimeObject(const vespalib::slime::Inspector & src, vespalib::slime::Cursor & dest);

StringVector getlines(vespalib::asciistream & is, char delim='\n');

}
