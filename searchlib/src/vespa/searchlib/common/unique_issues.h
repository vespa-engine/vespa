// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/issue.h>
#include <memory>
#include <set>

namespace search {

/**
 * Keep track of all unique issues encountered.
 **/
class UniqueIssues : public vespalib::Issue::Handler
{
private:
    std::set<vespalib::string> _messages;
public:
    using UP = std::unique_ptr<UniqueIssues>;
    void handle(const vespalib::Issue &issue) override;
    template <class Function>
    void for_each_message(Function fun) const {
        for (const auto &msg: _messages) {
            fun(msg);
        }
    }
    size_t size() const { return _messages.size(); }
};

}
