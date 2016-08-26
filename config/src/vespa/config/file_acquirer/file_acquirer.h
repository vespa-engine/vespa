// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/fnet/frt/frt.h>

namespace config {

/**
 * Interface used to wait for the availability of files and map file
 * references to concrete paths.
 **/
struct FileAcquirer {
    virtual vespalib::string wait_for(const vespalib::string &file_ref, double timeout_s) = 0;
    virtual ~FileAcquirer() {}
};

/**
 * File acquirer implementation using rpc to talk to an external rpc
 * server to wait for files to be ready.
 **/
class RpcFileAcquirer : public FileAcquirer
{
private:
    FRT_Supervisor _orb;
    vespalib::string _spec;
public:
    RpcFileAcquirer(const vespalib::string &spec);
    vespalib::string wait_for(const vespalib::string &file_ref, double timeout_s) override;
    ~RpcFileAcquirer();
};

} // namespace config
