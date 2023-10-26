// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace document {

/**
 * Class used to represent up to 4 flags used in a DocumentUpdate.
 * These flags are stored as the 4 most significant bits in a 32 bit integer.
 *
 * Flags currently used:
 *   0) create-if-non-existent.
 */
class DocumentUpdateFlags {
private:
    uint8_t _flags;
    DocumentUpdateFlags(uint8_t flags) : _flags(flags) {}

public:
    DocumentUpdateFlags() : _flags(0) {}
    bool getCreateIfNonExistent() {
        return (_flags & 1) != 0;
    }
    void setCreateIfNonExistent(bool value) {
        _flags &= ~1; // clear flag
        _flags |= value ? 1 : 0; // set flag
    }
    int injectInto(int value) {
        return extractValue(value) | (_flags << 28);
    }
    static DocumentUpdateFlags extractFlags(int combined) {
        return DocumentUpdateFlags((uint8_t)(combined >> 28));
    }
    static int extractValue(int combined) {
        int mask = ~(~0U << 28);
        return combined & mask;
    }
};

}

