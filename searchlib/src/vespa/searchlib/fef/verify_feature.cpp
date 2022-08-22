// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "verify_feature.h"
#include "blueprintresolver.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

namespace search::fef {

bool verifyFeature(const BlueprintFactory &factory,
                   const IIndexEnvironment &indexEnv,
                   const std::string &featureName,
                   const std::string &desc,
                   std::vector<Message> & errors)
{
    indexEnv.hintFeatureMotivation(IIndexEnvironment::VERIFY_SETUP);
    BlueprintResolver resolver(factory, indexEnv);
    resolver.addSeed(featureName);
    bool result = resolver.compile();
    if (!result) {
        const BlueprintResolver::Warnings & warnings(resolver.getWarnings());
        for (const auto & msg : warnings) {
            errors.emplace_back(Level::WARNING, msg);
        }
        vespalib::string msg = fmt("verification failed: %s (%s)",BlueprintResolver::describe_feature(featureName).c_str(), desc.c_str());
        errors.emplace_back(Level::ERROR, msg);
    }
    return result;
}

}
