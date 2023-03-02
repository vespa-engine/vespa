// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "assert.h"
#include <vespa/defaults.h>
#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/component/vtag.h>
#include <fstream>
#include <map>
#include <mutex>

#include <vespa/log/log.h>
LOG_SETUP(".vespa.assert");

namespace vespalib::assert {

namespace {

std::mutex _G_lock;
std::map<std::string, size_t> _G_assertMap;

}

size_t
getNumAsserts(const char *key)
{
    std::lock_guard guard(_G_lock);
    return _G_assertMap[key];
}

vespalib::string
getAssertLogFileName(const char *key)
{
    vespalib::string relative = make_string("var/db/vespa/tmp/%s.%s.assert", key, Vtag::currentVersion.toString().c_str());
    return vespa::Defaults::underVespaHome(relative.c_str());
}

void
assertOnceOrLog(const char *expr, const char *key, size_t freq)
{
    size_t count(0);
    {
        std::lock_guard guard(_G_lock);
        count = _G_assertMap[key]++;
    }
    if (count) {
        if ((count % freq) == 0) {
            LOG(error, "assert(%s) named '%s' has failed %zu times. Stacktrace = %s",
                expr, key, count+1, vespalib::getStackTrace(0).c_str());
        }
    } else {
        std::string rememberAssert = getAssertLogFileName(key);
        std::ifstream prevAssertFile(rememberAssert.c_str());
        if (prevAssertFile) {
            if ((count % freq) == 0) {
                LOG(error, "assert(%s) named '%s' has failed %zu times. Stacktrace = %s",
                    expr, key, count + 1, vespalib::getStackTrace(0).c_str());
            }
        } else {
            {
                LOG(error, "assert(%s) named '%s' failed first time. Stacktrace = %s",
                    expr, key, vespalib::getStackTrace(0).c_str());
                std::ofstream assertStream(rememberAssert.c_str());
                assertStream << to_string(system_clock::now()) << " assert(" << expr
                             << ") named " << key << " failed" << std::endl;
                assertStream.close();
            }
            abort();
        }
    }
}

}
