// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace config::sentinel {

class LineSplitter {
private:
    int _fd;
    int _size;
    char *_buffer;
    int _readPos;
    int _writePos;
    bool _eof;

    LineSplitter();
    LineSplitter& operator =(const LineSplitter&);
    LineSplitter(const LineSplitter&);

    bool resize();
    bool fill();

public:
    explicit LineSplitter(int fd);
    char *getLine();
    bool eof() const { return _eof && _readPos >= _writePos; }

    ~LineSplitter();
};

}

