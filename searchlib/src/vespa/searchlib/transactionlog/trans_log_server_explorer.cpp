// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "trans_log_server_explorer.h"
#include "translogserver.h"
#include "domain.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/fastos/file.h>


using vespalib::slime::Inserter;
using vespalib::slime::Cursor;

namespace search::transactionlog {

namespace {

struct DomainExplorer : vespalib::StateExplorer {
    Domain::SP domain;
    DomainExplorer(Domain::SP domain_in) : domain(std::move(domain_in)) {}
    void get_state(const Inserter &inserter, bool full) const override {
        Cursor &state = inserter.insertObject();
        DomainInfo info = domain->getDomainInfo();
        state.setLong("from", info.range.from());
        state.setLong("to", info.range.to());
        state.setLong("numEntries", info.numEntries);
        state.setLong("byteSize", info.byteSize);
        if (full) {
            Cursor &array = state.setArray("parts");
            for (const PartInfo &part_in: info.parts) {
                Cursor &part = array.addObject();
                part.setLong("from", part_in.range.from());
                part.setLong("to", part_in.range.to());
                part.setLong("numEntries", part_in.numEntries);
                part.setLong("byteSize", part_in.byteSize);
                part.setString("file", part_in.file);
                {
                    FastOS_StatInfo stat_info;
                    FastOS_File::Stat(part_in.file.c_str(), &stat_info);
                    part.setString("lastModified", vespalib::to_string(stat_info._modifiedTime));
                }
            }
        }
    }
};

} // namespace search::transactionlog::<unnamed>

TransLogServerExplorer::~TransLogServerExplorer() = default;
void
TransLogServerExplorer::get_state(const Inserter &inserter, bool full) const
{
    (void) full;
    inserter.insertObject();
}

std::vector<vespalib::string>
TransLogServerExplorer::get_children_names() const
{
    return _server->getDomainNames();
}

std::unique_ptr<vespalib::StateExplorer>
TransLogServerExplorer::get_child(vespalib::stringref name) const
{
    Domain::SP domain = _server->findDomain(name);
    if (!domain) {
        return std::unique_ptr<vespalib::StateExplorer>(nullptr);
    }
    return std::unique_ptr<vespalib::StateExplorer>(new DomainExplorer(std::move(domain)));
}

}
