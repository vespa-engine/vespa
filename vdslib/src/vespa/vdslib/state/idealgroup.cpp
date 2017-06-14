// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/state/idealgroup.h>

namespace storage {
namespace lib {

IdealGroup::IdealGroup(const Group& g, double score, double redundancy)
    : _group(&g),
      _score(score),
      _redundancy(redundancy)
{
}

void
IdealGroup::print(std::ostream& out, bool verbose,
             const std::string& indent) const {

    out << indent << "\nredundancy  : " << _redundancy << "\n";
    out << indent << "score  : " << _score << "\n";
    out << indent << "group : \n" ;
    _group->print(out, verbose, indent + "   ");
}

} // lib
} // storage
