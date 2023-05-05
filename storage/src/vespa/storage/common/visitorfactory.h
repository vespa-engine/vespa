// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::VisitorFactory
 *
 * New visitor implementations must implement this interface and register it in
 * the storage server, in order for the visitor threads to be able to create
 * instances of the visitor.
 */
#pragma once

#include <vespa/vdslib/container/parameters.h>

namespace storage {

class Visitor;

class VisitorEnvironment {
public:
    VisitorEnvironment() = default;
    virtual ~VisitorEnvironment() = default;
};

class VisitorFactory {
public:
    using SP = std::shared_ptr<VisitorFactory>;
    using Map = std::map<std::string, std::shared_ptr<VisitorFactory>>;

    virtual ~VisitorFactory() = default;

    virtual std::shared_ptr<VisitorEnvironment> makeVisitorEnvironment(StorageComponent&) = 0;

    virtual storage::Visitor *makeVisitor(
            StorageComponent&, VisitorEnvironment& env,
            const vdslib::Parameters& params) = 0;
};

} // storage

