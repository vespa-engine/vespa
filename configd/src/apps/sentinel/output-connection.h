// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "line-splitter.h"

namespace ns_log { class LLParser; }

namespace config::sentinel {

class OutputConnection {
private:
    int _fd;
    LineSplitter _lines;
    ns_log::LLParser *_parser;

    // Unused constructors/assignment operator:
    OutputConnection();
    OutputConnection(const OutputConnection&);
    OutputConnection& operator =(const OutputConnection&);

public:
    explicit OutputConnection(int fd, ns_log::LLParser *p);
    ~OutputConnection();
    bool isFinished() const;
    void handleOutput();
    int fd() const { return _fd; }
};

}
