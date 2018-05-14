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

const char *defaultHome = "/opt/vespa";
const char *defaultUser = "vespa";
const char *defaultHost = "localhost";
int defaultWebServicePort = 8080;
int defaultPortBase = 19000;
int defaultPortConfigServerRpc = 19070;
int defaultPortConfigServerHttp = 19071;
int defaultPortConfigProxyRpc = 19090;
const char *defaultConfigServers = "localhost";

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

void findDefaults() {
    if (initialized) return;
    const char *env = getenv("VESPA_HOME");
    if (env != NULL && *env != '\0') {
        DIR *dp = NULL;
        if (*env == '/' || *env == '.') {
            dp = opendir(env);
        }
        if (dp != NULL) {
            defaultHome = env;
            // fprintf(stderr, "debug\tVESPA_HOME is '%s'\n", defaultHome);
            closedir(dp);
        } else {
            fprintf(stderr, "warning\tbad VESPA_HOME '%s' (ignored)\n", env);
        }
    }
    env = getenv("VESPA_USER");
    if (env != NULL && *env != '\0') {
        if (getpwnam(env) == 0) {
            fprintf(stderr, "warning\tbad VESPA_USER '%s' (ignored)\n", env);
        } else {
            defaultUser = env;
        }
    }
    env = getenv("VESPA_HOSTNAME");
    if (env != NULL && *env != '\0') {
        defaultHost = env;
    }
    long p = getNumFromEnv("VESPA_WEB_SERVICE_PORT");
    if (p > 0) {
        // fprintf(stderr, "debug\tdefault web service port is '%ld'\n", p);
        defaultWebServicePort = p;
    }
    p = getNumFromEnv("VESPA_PORT_BASE");
    if (p > 0) {
        // fprintf(stderr, "debug\tdefault port base is '%ld'\n", p);
        defaultPortBase = p;
    }
    p = getNumFromEnv("port_configserver_rpc");
    if (p > 0) {
        defaultPortConfigServerRpc = p;
        defaultPortConfigServerHttp = p+1;
    }
    p = getNumFromEnv("port_configproxy_rpc");
    if (p > 0) {
        defaultPortConfigProxyRpc = p;
    } else {
        defaultPortConfigProxyRpc = defaultPortBase + 90;
    }
    env = getenv("VESPA_CONFIGSERVERS");
    if (env == NULL || *env == '\0') {
        env = getenv("services__addr_configserver");
    }
    if (env == NULL || *env == '\0') {
        env = getenv("vespa_base__addr_configserver");
    }
    if (env == NULL || *env == '\0') {
        env = getenv("addr_configserver");
    }
    if (env != NULL && *env != '\0') {
        // fprintf(stderr, "debug\tdefault configserver(s) is '%s'\n", env);
        defaultConfigServers = env;
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
