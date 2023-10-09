// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "location.h"
#include <limits>

namespace search::common {

Location::Location(const GeoLocation &from) : GeoLocation(from) {}

} // namespace
