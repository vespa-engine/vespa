// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
namespace logdemon {

class CmdBuf
{
private:
    int _size;
    char *_buf;
    char *_bp;
    int _left;
    void extend();

    CmdBuf(const CmdBuf& other);
    CmdBuf& operator= (const CmdBuf& other);
public:
    CmdBuf();
    ~CmdBuf();
    bool hasCmd();
    void doCmd(Performer &via);
    void maybeRead(int fd);
    bool readFile(int fd);
};

} // namespace
