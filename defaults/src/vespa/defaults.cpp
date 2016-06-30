// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "defaults.h"

#include <stdlib.h>
#include <dirent.h>
#include <stdio.h>
#include <string.h>
#include <string>
#include <unistd.h>
#include <atomic>

namespace {

const char *defaultHome = "/opt/vespa/";
char computedHome[PATH_MAX];
int defaultWebServicePort = 8080;
int defaultPortBase = 19000;
std::atomic<bool> initialized(false);

void findDefaults() {
    if (initialized) return;
    const char *env = getenv("VESPA_HOME");
    if (env != NULL) {
        DIR *dp = NULL;
        if (*env == '/') {
            dp = opendir(env);
        }
        if (dp != NULL) {
            size_t len = strlen(env);
            if (env[len-1] == '/') {
                // already ends with slash
                defaultHome = env;
            } else {
                // append slash
                strncpy(computedHome, env, PATH_MAX);
                strncat(computedHome, "/", PATH_MAX);
                defaultHome = computedHome;
            }
            // fprintf(stderr, "debug\tVESPA_HOME is '%s'\n", defaultHome);
            closedir(dp);
        } else {
            fprintf(stderr, "warning\tbad VESPA_HOME '%s' (ignored)\n", env);
        }
    }
    env = getenv("VESPA_WEB_SERVICE_PORT");
    if (env != NULL && *env != '\0') {
        char *endptr = NULL;
        long p = strtol(env, &endptr, 10);
        if (endptr != NULL && *endptr == '\0') {
            defaultWebServicePort = p;
            // fprintf(stderr, "debug\tdefault web service port is '%ld'\n", p);
        } else {
            fprintf(stderr, "warning\tbad VESPA_WEB_SERVICE_PORT '%s' (ignored)\n", env);
        }
    }
    env = getenv("VESPA_PORT_BASE");
    if (env != NULL && *env != '\0') {
        char *endptr = NULL;
        long p = strtol(env, &endptr, 10);
        if (endptr != NULL && *endptr == '\0') {
            defaultPortBase = p;
            // fprintf(stderr, "debug\tdefault port base is '%ld'\n", p);
        } else {
            fprintf(stderr, "warning\tbad VESPA_PORT_BASE '%s' (ignored)\n", env);
        }
    }
    initialized = true;
}

}

namespace vespa {

std::string myPath(const char *argv0)
{
    if (argv0[0] == '/') return argv0;

    const char *pathEnv = getenv("PATH");
    if (pathEnv != 0) {
        std::string path = pathEnv;
        size_t pos = 0;
        size_t colon;
        do {
            colon = path.find(':', pos);
            std::string pre = path.substr(pos, colon-pos);
            pos = colon+1;
            if (pre[0] == '/') {
                pre.append("/");
                pre.append(argv0);
                if (access(pre.c_str(), X_OK) == 0) {
                    return pre;
                }
            }
        } while (colon != std::string::npos);
    }
    return argv0;
}

void
Defaults::bootstrap(const char *argv0)
{
    if (getenv("VESPA_HOME") == 0) {
        std::string path = myPath(argv0);
        size_t slash = path.rfind('/');
        if (slash != std::string::npos) {
            path.resize(slash);
            slash = path.rfind('/', slash-1);
            if (slash != std::string::npos) {
               const char *dirname = path.c_str() + slash;
               if (strncmp(dirname, "/bin", 4) == 0 ||
                   strncmp(dirname, "/sbin", 5) == 0)
               {
                   path.resize(slash);
               }
            }
            std::string setting = "VESPA_HOME";
            setting.append("=");
            setting.append(path);
            putenv(&setting[0]);
        }
    }
    initialized = false;
}

const char *
Defaults::vespaHome()
{
    findDefaults();
    return defaultHome;
}

int
Defaults::vespaWebServicePort()
{
    findDefaults();
    return defaultWebServicePort;
}

int
Defaults::vespaPortBase()
{
    findDefaults();
    return defaultPortBase;
}

} // namespace vespa
