// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace logdemon {

class Performer;

class CmdBuf
{
private:
    int _size;
    char *_buf;
    char *_bp;
    int _left;
    void extend();
public:
    CmdBuf(const CmdBuf& other) = delete;
    CmdBuf& operator= (const CmdBuf& other) = delete;
    CmdBuf();
    ~CmdBuf();
    bool hasCmd();
    void doCmd(Performer &via);
    void maybeRead(int fd);
    bool readFile(int fd);
};

} // namespace
