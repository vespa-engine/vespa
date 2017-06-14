// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

#include "fileprovider.h"

namespace filedistribution {

class FileDistributorRPC : public std::enable_shared_from_this<FileDistributorRPC>
{
    class Server;
public:
    using SP = std::shared_ptr<FileDistributorRPC>;
    FileDistributorRPC(const FileDistributorRPC &) = delete;
    FileDistributorRPC & operator = (const FileDistributorRPC &) = delete;
    FileDistributorRPC(const std::string& connectSpec, const FileProvider::SP & provider);

    void start();

    static int get_port(const std::string &spec);

    ~FileDistributorRPC();
private:
    std::unique_ptr<Server> _server;
};

} //namespace filedistribution

