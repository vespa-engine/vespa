// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentdbconfig.h"

namespace proton
{

/**
 * Class to create adjusted document db config that minimizes the number of
 * proton restarts needed due to config changes.  Grab the portions from
 * live (supposedly future) config that is safe to apply early during
 * initialization and replay.
 */
class DocumentDBConfigScout
{
public:
    static DocumentDBConfig::SP
    scout(const DocumentDBConfig::SP &config,
          const DocumentDBConfig &liveConfig);
};

}
