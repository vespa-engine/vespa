// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "splunk-starter.h"
#include <dirent.h>
#include <sys/stat.h>

#include <vespa/log/log.h>
LOG_SETUP(".splunk-starter");

SplunkStarter::SplunkStarter() = default;

SplunkStarter::~SplunkStarter() = default;

namespace {

vespalib::string fixDir(const vespalib::string &parent, const vespalib::string &subdir) {
    auto dirname = parent + "/" + subdir;
    DIR *dp = opendir(dirname.c_str());
    if (dp == NULL) {
        if (errno != ENOENT || mkdir(dirname.c_str(), 0755) != 0) {
            LOG(warning, "Could not create directory '%s'", dirname.c_str());
            perror(dirname.c_str());
        }
    } else {
        closedir(dp);
    }
    return dirname;
}

vespalib::string
cfFilePath(const vespalib::string &parent, const vespalib::string &filename) {
    vespalib::string path = parent;
    path = fixDir(path, "etc");
    path = fixDir(path, "system");
    path = fixDir(path, "local");
    return path + "/" + filename;
}

void appendFile(FILE *target, const vespalib::string &filename) {
    FILE *fp = fopen(filename.c_str(), "r");
    if (fp != NULL) {
        int c;
        while (EOF != (c = fgetc(fp))) {
            fputc(c, target);
        }
        fclose(fp);
    }
}

} // namespace <unnamed>

void SplunkStarter::gotConfig(const LogforwarderConfig& config) {
    vespalib::string path = cfFilePath(config.splunkHome, "deploymentclient.conf");
    LOG(debug, "got config, writing %s", path.c_str());
    vespalib::string tmpPath = path + ".new";
    FILE *fp = fopen(tmpPath.c_str(), "w");
    if (fp == NULL) {
        LOG(warning, "could not open '%s' for write", tmpPath.c_str());
        return;
    }

    fprintf(fp, "[deployment-client]\n");
    fprintf(fp, "clientName = %s\n", config.clientName.c_str());
    fprintf(fp, "phoneHomeIntervalInSecs = %i\n", config.phoneHomeInterval);
    fprintf(fp, "\n");
    fprintf(fp, "[target-broker:deploymentServer]\n");
    fprintf(fp, "targetUri = %s\n", config.deploymentServer.c_str());

    fclose(fp);
    rename(tmpPath.c_str(), path.c_str());

    if (getenv("VESPA_HOSTNAME") != NULL &&
        getenv("VESPA_TENANT") != NULL &&
        getenv("VESPA_APPLICATION")!= NULL &&
        getenv("VESPA_INSTANCE") != NULL &&
        getenv("VESPA_ENVIRONMENT") != NULL &&
        getenv("VESPA_REGION") != NULL)
    {
        path = cfFilePath(config.splunkHome, "inputs.conf");
        tmpPath = path + ".new";
        fp = fopen(tmpPath.c_str(), "w");
        if (fp != NULL) {
            fprintf(fp, "[default]\n");
            fprintf(fp, "host = %s\n", getenv("VESPA_HOSTNAME"));
            fprintf(fp, "_meta = vespa_tenant::%s vespa_app::%s.%s vespa_zone::%s.%s\n",
                    getenv("VESPA_TENANT"),
                    getenv("VESPA_APPLICATION"),
                    getenv("VESPA_INSTANCE"),
                    getenv("VESPA_ENVIRONMENT"),
                    getenv("VESPA_REGION"));
            fclose(fp);
            rename(tmpPath.c_str(), path.c_str());
        }
    }
    vespalib::string clientCert = clientCertFile();
    vespalib::string clientKey = clientKeyFile();
    if (!clientCert.empty() && !clientKey.empty()) {
        vespalib::string certPath = cfFilePath(config.splunkHome, "clientcert.pem");
        tmpPath = certPath + ".new";
        fp = fopen(tmpPath.c_str(), "w");
        appendFile(fp, clientCert);
        appendFile(fp, clientKey);
        appendFile(fp, "/etc/ssl/certs/ca-bundle.crt");
        fclose(fp);
        rename(tmpPath.c_str(), certPath.c_str());

        path = cfFilePath(config.splunkHome, "outputs.conf");
        tmpPath = path + ".new";
        fp = fopen(tmpPath.c_str(), "w");
        if (fp != NULL) {
            fprintf(fp, "[tcpout]\n");
            fprintf(fp, "clientCert = %s\n", certPath.c_str());
            fclose(fp);
            rename(tmpPath.c_str(), path.c_str());
        }
    }
    if (config.clientName.size() == 0 ||
        config.deploymentServer.size() == 0)
    {
        _childHandler.stopChild();
    } else {
        _childHandler.startChild(config.splunkHome);
    }
}

void SplunkStarter::stop() {
    _childHandler.stopChild();
}
