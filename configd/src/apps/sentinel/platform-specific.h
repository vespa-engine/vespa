// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace config::platform_specific {

/**
 * If running on Linux, sets the `no_new_privs` process bit, which amongst other
 * things prevents all launched sub-process(es) from acquiring more privileges
 * through setuid/setgid executables.
 *
 * Only takes effect if the `VESPA_PR_SET_NO_NEW_PRIVS` environment variable is
 * set to "true" or "yes".
 */
void pledge_no_new_privileges_if_env_configured();

}
