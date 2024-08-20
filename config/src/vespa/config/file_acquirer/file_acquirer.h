// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <string>

class FRT_Supervisor;
class FNET_Transport;

namespace config {

/**
 * Interface used to wait for the availability of files and map file
 * references to concrete paths.
 **/
struct FileAcquirer {
    virtual std::string wait_for(const std::string &file_ref, double timeout_s) = 0;
    virtual ~FileAcquirer() {}
};

/**
 * File acquirer implementation using rpc to talk to an external rpc
 * server to wait for files to be ready.
 **/
class RpcFileAcquirer : public FileAcquirer
{
private:
    std::unique_ptr<FRT_Supervisor>   _orb;
    std::string                  _spec;
public:
    RpcFileAcquirer(FNET_Transport & transport, const std::string &spec);
    std::string wait_for(const std::string &file_ref, double timeout_s) override;
    ~RpcFileAcquirer() override;
};

} // namespace config
