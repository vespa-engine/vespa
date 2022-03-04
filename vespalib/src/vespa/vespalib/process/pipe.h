// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/guard.h>

namespace vespalib {

/**
 * A thin wrapper around a pipe between two file-descriptors.
 **/
struct Pipe {
    FileDescriptor read_end;
    FileDescriptor write_end;
    bool valid() const { return (read_end.valid() && write_end.valid()); }
    static Pipe create();
    ~Pipe();
};

} // namespace vespalib
