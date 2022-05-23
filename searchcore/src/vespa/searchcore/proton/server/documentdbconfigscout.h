// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace proton {

class DocumentDBConfig;

/**
 * Class to create adjusted document db config that minimizes the number of
 * proton restarts needed due to config changes.  Grab the portions from
 * live (supposedly future) config that is safe to apply early during
 * initialization and replay.
 */
class DocumentDBConfigScout
{
public:
    static std::shared_ptr<DocumentDBConfig>
    scout(const std::shared_ptr<DocumentDBConfig> &config, const DocumentDBConfig &liveConfig);
};

}
