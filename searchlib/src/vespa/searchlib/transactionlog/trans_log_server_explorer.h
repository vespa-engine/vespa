// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace search::transactionlog {

class TransLogServer;

/**
 * Class used to explore the state of a transaction log server.
 */
class TransLogServerExplorer : public vespalib::StateExplorer
{
private:
    using TransLogServerSP = std::shared_ptr<TransLogServer>;
    TransLogServerSP _server;

public:
    TransLogServerExplorer(TransLogServerSP server) : _server(std::move(server)) {}
    ~TransLogServerExplorer() override;
    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    std::vector<vespalib::string> get_children_names() const override;
    std::unique_ptr<StateExplorer> get_child(vespalib::stringref name) const override;
};

}
