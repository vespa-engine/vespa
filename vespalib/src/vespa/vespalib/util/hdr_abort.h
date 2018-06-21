// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {

[[noreturn]] extern void
hdr_abort(const char *message,
          const char *file,
          unsigned int line);

#define HDR_ABORT(msg) \
  (vespalib::hdr_abort(msg, __FILE__, __LINE__))

} // namespace vespalib
