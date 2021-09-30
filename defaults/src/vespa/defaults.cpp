// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "defaults.h"

#include <stdlib.h>
#include <dirent.h>
#include <stdio.h>
#include <string.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <atomic>
#include <pwd.h>

namespace {

#define HOST_BUF_SZ 1024

const char *defaultHome = 0;
const char *defaultUser = 0;
const char *defaultHost = 0;
int defaultWebServicePort = 0;
int defaultPortBase = 0;
int defaultPortConfigServerRpc = 0;
int defaultPortConfigServerHttp = 0;
int defaultPortConfigProxyRpc = 0;
const char *defaultConfigServers = 0;
std::string VESPA_HOME_ENV = "VESPA_HOME=";

std::atomic<bool> initialized(false);

long getNumFromEnv(const char *envName)
{
    const char *env = getenv(envName);
    if (env != nullptr && *env != '\0') {
       char *endptr = nullptr;
       long num = strtol(env, &endptr, 10);
       if (endptr != nullptr && *endptr == '\0') {
           return num;
       }
       fprintf(stderr, "warning\tbad %s '%s' (ignored)\n",
               envName, env);
    }
    return -1;
}

const char *findVespaHome(const char *defHome) {
    const char *env = getenv("VESPA_HOME");
    if (env != NULL && *env != '\0') {
        DIR *dp = NULL;
        if (*env == '/' || *env == '.') {
            dp = opendir(env);
        }
        if (dp != NULL) {
            // fprintf(stderr, "debug\tVESPA_HOME is '%s'\n", env);
            closedir(dp);
            return env;
        } else {
            fprintf(stderr, "warning\tbad VESPA_HOME '%s' (ignored)\n", env);
        }
    }
    return defHome;
}

const char *findVespaUser(const char *defUser) {
    const char *env = getenv("VESPA_USER");
    if (env != NULL && *env != '\0') {
        if (getpwnam(env) == 0) {
            fprintf(stderr, "warning\tbad VESPA_USER '%s' (ignored)\n", env);
        } else {
            return env;
        }
    }
    return defUser;
}

const char *findHostname(const char *defHost) {
    const char *env = getenv("VESPA_HOSTNAME");
    if (env != NULL && *env != '\0') {
        return env;
    }
    return defHost;
}

int findWebServicePort(int defPort) {
    long p = getNumFromEnv("VESPA_WEB_SERVICE_PORT");
    if (p > 0) {
        // fprintf(stderr, "debug\tdefault web service port is '%ld'\n", p);
        return p;
    }
    return defPort;
}

int findVespaPortBase(int defPort) {
    long p = getNumFromEnv("VESPA_PORT_BASE");
    if (p > 0) {
        // fprintf(stderr, "debug\tdefault port base is '%ld'\n", p);
        return p;
    }
    return defPort;
}

int findConfigServerPort(int defPort) {
    long p = getNumFromEnv("port_configserver_rpc");
    if (p > 0) {
        return p;
    }
    return defPort;
}

int findConfigProxyPort(int defPort) {
    long p = getNumFromEnv("port_configproxy_rpc");
    if (p > 0) {
        return p;
    }
    return defPort;
}

const char *findConfigServers(const char *defServers) {
    const char *env = getenv("VESPA_CONFIGSERVERS");
    if (env == NULL || *env == '\0') {
        env = getenv("addr_configserver");
    }
    if (env != NULL && *env != '\0') {
        // fprintf(stderr, "debug\tdefault configserver(s) is '%s'\n", env);
        return env;
    }
    return defServers;
}

void findDefaults() {
    if (initialized) return;

    defaultHome = findVespaHome("/opt/vespa");
    defaultUser = findVespaUser("vespa");
    defaultHost = findHostname("localhost");
    defaultWebServicePort = findWebServicePort(8080);
    defaultPortBase = findVespaPortBase(19000);
    defaultPortConfigServerRpc = findConfigServerPort(defaultPortBase + 70);
    defaultPortConfigServerHttp = defaultPortConfigServerRpc + 1;
    defaultPortConfigProxyRpc = findConfigProxyPort(defaultPortBase + 90);
    defaultConfigServers = findConfigServers("localhost");

    initialized = true;
}

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

}

namespace vespa {

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
            VESPA_HOME_ENV.append(path);
            putenv(&VESPA_HOME_ENV[0]);
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

std::string
Defaults::underVespaHome(const char *path)
{
    if (path[0] == '/') {
        return path;
    }
    if (path[0] == '.' && path[1] == '/') {
        return path;
    }
    findDefaults();
    std::string ret = defaultHome;
    ret += '/';
    ret += path;
    return ret;
}

const char *
Defaults::vespaUser()
{
    findDefaults();
    return defaultUser;
}

const char *
Defaults::vespaHostname()
{
    findDefaults();
    return defaultHost;
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

std::vector<std::string>
Defaults::vespaConfigServerHosts()
{
    findDefaults();
    std::vector<std::string> ret;
    char *toParse = strdup(defaultConfigServers);
    char *savePtr = 0;
    char *token = strtok_r(toParse, " ,", &savePtr);
    if (token == 0) {
        ret.push_back("localhost");
    } else {
        while (token != 0) {
            char *colon = strchr(token, ':');
            if (colon != 0 && colon != token) {
                *colon = '\0';
            }
            ret.push_back(token);
            token = strtok_r(0, " ,", &savePtr);
        }
    }
    free(toParse);
    return ret;
}

int
Defaults::vespaConfigServerRpcPort()
{
    findDefaults();
    return defaultPortConfigServerRpc;
}

std::vector<std::string>
Defaults::vespaConfigServerRpcAddrs()
{
    findDefaults();
    std::vector<std::string> ret;
    char *toParse = strdup(defaultConfigServers);
    char *savePtr = 0;
    char *token = strtok_r(toParse, " ,", &savePtr);
    if (token == 0) {
        std::string one = "tcp/localhost:";
        one += std::to_string(defaultPortConfigServerRpc);
        ret.push_back(one);
    } else {
        while (token != 0) {
            std::string one = "tcp/";
            one += token;
            if (strchr(token, ':') == 0) {
                one += ":";
                one += std::to_string(defaultPortConfigServerRpc);
            }
            ret.push_back(one);
            token = strtok_r(0, " ,", &savePtr);
        }
    }
    free(toParse);
    return ret;
}

std::vector<std::string>
Defaults::vespaConfigServerRestUrls()
{
    findDefaults();
    std::vector<std::string> ret;
    char *toParse = strdup(defaultConfigServers);
    char *savePtr = 0;
    char *token = strtok_r(toParse, " ,", &savePtr);
    if (token == 0) {
        std::string one = "http://localhost:";
        one += std::to_string(defaultPortConfigServerHttp);
        ret.push_back(one);
    } else {
        while (token != 0) {
            std::string one = "http://";
            char *colon = strchr(token, ':');
            if (colon != 0 && colon != token) {
                *colon = '\0';
            }
            one += token;
            one += ":";
            one += std::to_string(defaultPortConfigServerHttp);
            one += "/";
            ret.push_back(one);
            token = strtok_r(0, " ,", &savePtr);
        }
    }
    free(toParse);
    return ret;
}

std::string
Defaults::vespaConfigProxyRpcAddr()
{
    findDefaults();
    std::string ret = "tcp/localhost:";
    ret += std::to_string(defaultPortConfigProxyRpc);
    return ret;
}

std::vector<std::string>
Defaults::vespaConfigSourcesRpcAddrs()
{
    findDefaults();
    std::vector<std::string> ret;
    ret.push_back(vespaConfigProxyRpcAddr());
    for (std::string v : vespaConfigServerRpcAddrs()) {
        ret.push_back(v);
    }
    return ret;
}

} // namespace vespa
