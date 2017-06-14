// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::IdealGroup
 *
 * Points to a Group object that has been picked for
 * distribution with a given redundancy.
 *
 *
 */
#pragma once

#include <vdslib/state/group.h>
#include <vespa/vespalib/objects/floatingpointtype.h>

namespace storage {
namespace lib {

class IdealGroup : public document::Printable
{
    const Group* _group;
    double _score;
    double _redundancy;

public:
    IdealGroup() {};

    IdealGroup(const Group& group, double score, double redundancy);

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    double getRedundancy() const {return _redundancy;}

    void setRedundancy(double redundancy) {_redundancy = redundancy;}

    double getScore() const {return _score;}

    static bool sortScore (IdealGroup ig1, IdealGroup ig2)
    {
        return (ig1._score<ig2._score);
    }

    static bool sortRedundancy (IdealGroup ig1, IdealGroup ig2)
    {
        return (ig1._redundancy<ig2._redundancy);
    }

    const std::vector<uint16_t>& getNodes() const
    {
        return _group->getNodes();
    }

    const Group& getGroup() const {
        return *_group;
    }

};

} // lib
} // storage

