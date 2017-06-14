// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "filedistributionmodel.h"
#include <algorithm>
#include <vector>

namespace filedistribution {

class MockFileDBModel : public FileDBModel {
    std::vector<std::string> _fileReferences;
public:
    bool hasFile(const std::string& fileReference) override {
        return std::find(_fileReferences.begin(), _fileReferences.end(), fileReference) != _fileReferences.end();
    }

    void addFile(const std::string& fileReference, const Buffer & buffer) override {
        (void)buffer;
        _fileReferences.push_back(fileReference);
    }

    Buffer getFile(const std::string& fileReference) override {
        (void)fileReference;
        const char* resultStr = "result";
        Buffer result(resultStr, resultStr + strlen(resultStr));
        return result;
    }

    void cleanFiles(const std::vector<std::string> &) override {}

    void setDeployedFilesToDownload(const std::string&, const std::string&,
                                    const std::vector<std::string> &) override {}
    void cleanDeployedFilesToDownload(const std::vector<std::string> &,
                                      const std::string&) override {}
    void removeDeploymentsThatHaveDifferentApplicationId(const std::vector<std::string> &,
                                                         const std::string&) override {}

    std::vector<std::string> getHosts() override {
        return std::vector<std::string>();
    }

    HostStatus getHostStatus(const std::string&) override {
        return HostStatus();
    }

    Progress getProgress(const std::string&, const std::vector<std::string>&) override {
        return Progress();
    }
};


} //namespace filedistribution

