// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/util/log.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchcore.util.log");

/**
 * assert and abort functions.
 */
void __FastS_assert_fail(const char *assertion,
			 const char *file,
			 unsigned int line,
			 const char * function)
{
    const char *vtag = V_TAG;
    if (function != NULL) {
        LOG(error, "FATAL: %s:%d (%s) %s: Failed assertion: '%s'",
            file, line, vtag, function, assertion);
        fprintf(stderr, "%s:%d (%s) %s: Failed assertion: '%s'\n",
                file, line, vtag, function, assertion);
    } else {
        LOG(error, "FATAL: %s:%d (%s): Failed assertion: '%s'",
            file, line, vtag, assertion);
        fprintf(stderr, "%s:%d (%s): Failed assertion: '%s'\n",
                file, line, vtag, assertion);
    }
    EV_STOPPING("", "assert failed");
    abort();
}

void __FastS_abort(const char *message,
		   const char *file,
		   unsigned int line,
		   const char * function)
{
    const char *vtag = V_TAG;
    if (function != NULL) {
        LOG(error, "FATAL: %s:%d (%s) %s: Abort called. Reason: %s",
            file, line, vtag, function, message);
        fprintf(stderr, "%s:%d (%s) %s: Abort called. Reason: %s\n",
                file, line, vtag, function, message);
    } else {
        LOG(error, "FATAL: %s:%d (%s): Abort called. Reason: %s",
            file, line, vtag, message);
        fprintf(stderr, "%s:%d (%s): Abort called. Reason: %s\n",
                file, line, vtag, message);
    }
    EV_STOPPING("", "aborted");
    abort();
}
