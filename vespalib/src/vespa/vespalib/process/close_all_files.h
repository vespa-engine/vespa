// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/**
 * to be used between fork and exec
 *
 * Calling this function will close all open file descriptors except
 * stdin(0), stdout(1) and stderr(2)
 **/
void close_all_files();

} // namespace vespalib
