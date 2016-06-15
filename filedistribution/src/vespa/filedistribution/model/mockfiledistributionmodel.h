// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "filedistributionmodel.h"

#include <algorithm>
#include <vector>

namespace filedistribution {

class MockFileDBModel : public FileDBModel {
    std::vector<std::string> _fileReferences;
public:
//Overrides
    bool hasFile(const std::string& fileReference) {
        return std::find(_fileReferences.begin(), _fileReferences.end(), fileReference) != _fileReferences.end();
    }

    void addFile(const std::string& fileReference, const Buffer & buffer) {
        (void)buffer;
        _fileReferences.push_back(fileReference);
    }

    Move<Buffer> getFile(const std::string& fileReference) {
        (void)fileReference;
        const char* resultStr = "result";
        Buffer result(resultStr, resultStr + strlen(resultStr));
        return move(result);
    }

    virtual void cleanFiles(
            const std::vector<std::string> &) {}


    virtual void setDeployedFilesToDownload(const std::string&,
            const std::string&,
            const std::vector<std::string> &) {}
    virtual void cleanDeployedFilesToDownload(
            const std::vector<std::string> &,
            const std::string&) {}
    virtual void removeDeploymentsThatHaveDifferentApplicationId(
            const std::vector<std::string> &,
            const std::string&) {}

    virtual std::vector<std::string> getHosts() {
        return std::vector<std::string>();
    }

    virtual HostStatus getHostStatus(const std::string&) {
        return HostStatus();
    }

    Progress getProgress(const std::string&,
                         const std::vector<std::string>&) {
        return Progress();
    }
};


} //namespace filedistribution

