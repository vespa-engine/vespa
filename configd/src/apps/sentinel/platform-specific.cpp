// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "platform-specific.h"
#include <vespa/vespalib/util/error.h>
#include <cstdlib>
#include <string_view>
#ifdef __linux__
#include <sys/prctl.h>
#endif

#include <vespa/log/log.h>
LOG_SETUP(".sentinel.platform-specific");

using namespace std::string_view_literals;

namespace config::platform_specific {

namespace {

[[maybe_unused]] [[nodiscard]]
bool is_env_toggled(const char* var_name) {
    const char* maybe_toggled = getenv(var_name);
    return (maybe_toggled && (maybe_toggled == "true"sv || maybe_toggled == "yes"sv));
}

}

void pledge_no_new_privileges_if_env_configured() {
#ifdef __linux__
    if (is_env_toggled("VESPA_PR_SET_NO_NEW_PRIVS")) {
        // One-way toggle to prevent any subprocess from possibly getting extra privileges via
        // setuid/setgid executables (modulo exciting things like kernel bugs or a small, trained
        // rat that climbs into your computer and pulls an adorably tiny lever labeled "root access").
        // Helps mitigate a certain class of vulnerabilities, and also allows processes to install
        // their own seccomp filters.
        // See https://www.kernel.org/doc/Documentation/prctl/no_new_privs.txt
        if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) {
            LOG(warning, "Failed to invoke prctl(PR_SET_NO_NEW_PRIVS): %s", vespalib::getErrorString(errno).c_str());
        } else {
            LOG(debug, "Successfully invoked prctl(PR_SET_NO_NEW_PRIVS)");
        }
    }
#endif
}

}
