// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string.h"

namespace vespalib {
std::string replace_variable(const std::string &input,
                                  const std::string &variable,
                                  const std::string &replacement);
} // namespace vespalib
