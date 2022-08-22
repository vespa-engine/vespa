// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprintfactory.h"
#include "iindexenvironment.h"

namespace search::fef {

enum class Level {INFO, WARNING, ERROR};
using Message = std::pair<Level, vespalib::string>;

/**
 * Verify whether a specific feature can be computed. If the feature
 * can not be computed, log a reason why, including feature
 * dependencies.
 *
 * @return true if the feature can be computed, false otherwise
 * @param factory blueprint factory
 * @param indexEnv index environment
 * @param featureName name of feature to verify
 * @param desc external description of the feature
 **/
bool verifyFeature(const BlueprintFactory &factory,
                   const IIndexEnvironment &indexEnv,
                   const std::string &featureName,
                   const std::string &desc,
                   std::vector<Message> & errors);

}
