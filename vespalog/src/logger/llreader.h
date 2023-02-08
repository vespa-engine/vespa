// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/llparser.h>
#include <stdexcept>

namespace ns_log {

class MsgException : public std::exception {
private:
    const char *_string;
    MsgException& operator = (const MsgException &);
public:
    MsgException(const MsgException &x) : std::exception(), _string(x._string) {}
    MsgException(const char *s) : _string(s) {}
    ~MsgException() override = default;
    const char *what() const noexcept override { return _string; }
};

class InputBuf
{
private:
    int _inputfd;
    int _size;
    char *_buf;
    char *_bp;
    int _left;
    void extend();
    InputBuf(const InputBuf& other);
    InputBuf& operator= (const InputBuf& other);
public:
    InputBuf(int fd);
    ~InputBuf();
    bool blockRead();
    bool hasInput();
    void doInput(LLParser &via);
    void doAllInput(LLParser &via);
};

} // namespace
