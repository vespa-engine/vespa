// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastos/app.h>
#include <string>
#include <vector>

namespace spoolmaster {
/**
 * main spoolmaster application class
 */
class Application : public FastOS_Application {
private:
    std::string _masterInbox;
    std::vector<std::string> _inboxFiles;

    std::string _outboxParentDir;
    std::vector<std::string> _outboxes;

    typedef std::vector<std::string>::iterator sviter_t;

    bool scanInbox();
    bool findOutboxes();
    void moveLinks();
public:
    Application();
    ~Application();
    int Main() override;
};

}
