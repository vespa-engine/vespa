// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/exception.h>

namespace config {

VESPA_DEFINE_EXCEPTION(InvalidConfigException, vespalib::Exception);

VESPA_DEFINE_EXCEPTION(IllegalConfigKeyException, vespalib::Exception);

VESPA_DEFINE_EXCEPTION(ConfigRuntimeException, vespalib::Exception);

VESPA_DEFINE_EXCEPTION(InvalidConfigSourceException, vespalib::Exception);

VESPA_DEFINE_EXCEPTION(ConfigWriteException, vespalib::Exception);

VESPA_DEFINE_EXCEPTION(ConfigReadException, vespalib::Exception);

VESPA_DEFINE_EXCEPTION(ConfigTimeoutException, ConfigRuntimeException);

}

