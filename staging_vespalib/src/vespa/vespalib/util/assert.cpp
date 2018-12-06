// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "assert.h"
#include <vespa/defaults.h>

#include <fstream>
#include <map>
#include <mutex>
#include <chrono>
#include <iomanip>

#include <vespa/log/log.h>
LOG_SETUP(".vespa.assert");

namespace vespalib::assert {

namespace {

std::mutex _G_lock;
std::map<std::string, size_t> _G_assertMap;

}

size_t getNumAsserts(const char *key)
{
    std::lock_guard guard(_G_lock);
    return _G_assertMap[key];
}

void assertOnceOrLog(const char *expr, const char *key, size_t freq)
{
    std::string relativePath("tmp/");
    relativePath += key;
    relativePath += ".assert.";
    relativePath += vespa::Defaults::vespaUser();
    std::string rememberAssert = vespa::Defaults::underVespaHome(relativePath.c_str());
    std::ifstream prevAssertFile(rememberAssert.c_str());
    if (prevAssertFile) {
        size_t count(0);
        {
            std::lock_guard guard(_G_lock);
            count = _G_assertMap[key]++;
        }
        if ((count % freq) == 0) {
            LOG(error,"assert(%s) named '%s' has failed %zu times", expr, key, count+1);
        }
    } else {
        {
            LOG(error, "assert(%s) named '%s' failed first time.", expr, key);
            std::ofstream assertStream(rememberAssert.c_str());
            std::chrono::time_point now = std::chrono::system_clock::now();
            std::time_t now_c = std::chrono::system_clock::to_time_t(now);
            assertStream << std::put_time(std::gmtime(&now_c), "%F %T") << " assert(" << expr << ") failed" << std::endl;
            assertStream.close();
        }
        abort();
    }
}

}
