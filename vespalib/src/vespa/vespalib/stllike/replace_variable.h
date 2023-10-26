// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string.h"

namespace vespalib {
vespalib::string replace_variable(const vespalib::string &input,
                                  const vespalib::string &variable,
                                  const vespalib::string &replacement);
} // namespace vespalib
