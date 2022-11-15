// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/defaults.h>
#include <stdio.h>
#include <string.h>

void dumpAllVars() {
    printf("VESPA_HOME = '%s'\n", vespa::Defaults::vespaHome());
    std::string v = vespa::Defaults::underVespaHome("foo");
    printf("underVespaHome(foo) = '%s'\n", v.c_str());
    printf("VESPA_USER = '%s'\n", vespa::Defaults::vespaUser());
    printf("VESPA_HOSTNAME = '%s'\n", vespa::Defaults::vespaHostname());
    printf("web service port = %d\n", vespa::Defaults::vespaWebServicePort());
    printf("VESPA_PORT_BASE = %d\n", vespa::Defaults::vespaPortBase());
    printf("config server rpc port = %d\n", vespa::Defaults::vespaConfigServerRpcPort());
    size_t count = 0;
    for (std::string vv : vespa::Defaults::vespaConfigServerHosts()) {
        ++count;
        printf("config server host %zu = '%s'\n", count, vv.c_str());
    }
    count = 0;
    for (std::string vv : vespa::Defaults::vespaConfigServerRestUrls()) {
        ++count;
        printf("config server rest URL %zu = '%s'\n", count, vv.c_str());
    }
    v = vespa::Defaults::vespaConfigProxyRpcAddr();
    printf("config proxy RPC addr = '%s'\n", v.c_str());
    printf("sanitizers = '%s'\n", vespa::Defaults::get_sanitizers().c_str());
    printf("vespa version = '%s'\n", V_TAG_COMPONENT);
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s <variable>\n", argv[0]);
        fprintf(stderr, "  variable names are: home, user, hostname, portbase, configservers,\n");
        fprintf(stderr, "                      configserver_rpc_port, configservers_rpc,\n");
        fprintf(stderr, "                      configservers_http, configsources, configproxy_rpc,\n");
        fprintf(stderr, "                      sanitizers,\n");
        fprintf(stderr, "                      version\n");
        return 1;
    }
    if (strcmp(argv[1], "home") == 0) {
        printf("%s\n", vespa::Defaults::vespaHome());
    } else if (strcmp(argv[1], "everything") == 0) {
        dumpAllVars();
    } else if (strcmp(argv[1], "user") == 0) {
        printf("%s\n", vespa::Defaults::vespaUser());
    } else if (strcmp(argv[1], "hostname") == 0) {
        printf("%s\n", vespa::Defaults::vespaHostname());
    } else if (strcmp(argv[1], "portbase") == 0) {
        printf("%d\n", vespa::Defaults::vespaPortBase());
    } else if (strcmp(argv[1], "configserver_rpc_port") == 0) {
        printf("%d\n", vespa::Defaults::vespaConfigServerRpcPort());
    } else if (strcmp(argv[1], "configservers") == 0) {
        for (std::string v : vespa::Defaults::vespaConfigServerHosts()) {
            printf("%s\n", v.c_str());
        }
    } else if (strcmp(argv[1], "configservers_rpc") == 0) {
        size_t count = 0;
        for (std::string v : vespa::Defaults::vespaConfigServerRpcAddrs()) {
            if (count++ > 0) printf(",");
            printf("%s", v.c_str());
        }
        printf("\n");
    } else if (strcmp(argv[1], "configservers_http") == 0) {
        for (std::string v : vespa::Defaults::vespaConfigServerRestUrls()) {
            printf("%s\n", v.c_str());
        }
    } else if (strcmp(argv[1], "configsources") == 0) {
        size_t count = 0;
        for (std::string v : vespa::Defaults::vespaConfigSourcesRpcAddrs()) {
            if (count++ > 0) printf(",");
            printf("%s", v.c_str());
        }
        printf("\n");
    } else if (strcmp(argv[1], "configproxy_rpc") == 0) {
        std::string v = vespa::Defaults::vespaConfigProxyRpcAddr();
        printf("%s\n", v.c_str());
    } else if (strcmp(argv[1], "sanitizers") == 0) {
        std::string v = vespa::Defaults::get_sanitizers();
        printf("%s\n", v.c_str());
    } else if (strcmp(argv[1], "version") == 0) {
        printf("%s\n", V_TAG_COMPONENT);
    } else {
        fprintf(stderr, "Unknown variable '%s'\n", argv[1]);
        return 1;
    }
    return 0;
}
