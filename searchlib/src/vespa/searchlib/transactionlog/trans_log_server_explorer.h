// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "translogserver.h"
#include <vespa/vespalib/net/state_explorer.h>

namespace search {
namespace transactionlog {

/**
 * Class used to explore the state of a transaction log server.
 */
class TransLogServerExplorer : public vespalib::StateExplorer
{
private:
    TransLogServer::SP _server;

public:
    TransLogServerExplorer(TransLogServer::SP server) : _server(std::move(server)) {}
    virtual void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    virtual std::vector<vespalib::string> get_children_names() const override;
    virtual std::unique_ptr<StateExplorer> get_child(vespalib::stringref name) const override;
};

} // namespace search::transactionlog
} // namespace search
