// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace config {

VESPA_IMPLEMENT_EXCEPTION(InvalidConfigException, vespalib::Exception);

VESPA_IMPLEMENT_EXCEPTION(IllegalConfigKeyException, vespalib::Exception);

VESPA_IMPLEMENT_EXCEPTION(ConfigRuntimeException, vespalib::Exception);

VESPA_IMPLEMENT_EXCEPTION(InvalidConfigSourceException, vespalib::Exception);

VESPA_IMPLEMENT_EXCEPTION(ConfigWriteException, vespalib::Exception);

VESPA_IMPLEMENT_EXCEPTION(ConfigReadException, vespalib::Exception);

VESPA_IMPLEMENT_EXCEPTION(ConfigTimeoutException, ConfigRuntimeException);

}

