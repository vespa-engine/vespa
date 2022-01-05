// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::CountVisitor
 * @ingroup visitors
 *
 * @brief A count visitor is a visitor that sends documentid statistics
 * to the client.
 *
 */
#pragma once

#include "visitor.h"

namespace storage {

class CountVisitor : public Visitor {
public:
    CountVisitor(StorageComponent&,
                 const vdslib::Parameters& params);

    void completedVisiting(HitCounter&) override;

private:
    void handleDocuments(const document::BucketId& bucketId,
                         DocEntryList & entries,
                         HitCounter& hitCounter) override;

    bool _doScheme;
    std::map<std::string, int> _schemeCount;

    bool _doNamespace;
    typedef std::map<vespalib::string, int> NamespaceCountMap;
    NamespaceCountMap _namespaceCount;

    bool _doUser;
    std::map<uint64_t, int> _userCount;

    bool _doGroup;
    typedef std::map<vespalib::string, int> GroupCountMap;
    GroupCountMap _groupCount;
};

struct CountVisitorFactory : public VisitorFactory {

    VisitorEnvironment::UP
    makeVisitorEnvironment(StorageComponent&) override {
        return VisitorEnvironment::UP(new VisitorEnvironment);
    };

    Visitor*
    makeVisitor(StorageComponent& c, VisitorEnvironment&,
                const vdslib::Parameters& params) override
    {
        return new CountVisitor(c, params);
    }

};

}



