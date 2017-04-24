// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/iprotocol.h>
#include <string>
#include <vespa/storageapi/mbusprot/protocolserialization5_0.h>
#include <vespa/storageapi/mbusprot/protocolserialization5_1.h>
#include <vespa/storageapi/mbusprot/protocolserialization5_2.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {
namespace mbusprot {

class StorageProtocol : public mbus::IProtocol
{
public:
    typedef std::shared_ptr<StorageProtocol> SP;

    static mbus::string NAME;

    StorageProtocol(const document::DocumentTypeRepo::SP,
                    const documentapi::LoadTypeSet& loadTypes);

    // Implements IProtocol.
    const mbus::string& getName() const override { return NAME; }

    // Implements IProtocol.
    mbus::IRoutingPolicy::UP createPolicy(const mbus::string& name,
                                          const mbus::string& param) const override;

    // Implements IProtocol.
    mbus::Blob encode(const vespalib::Version&, const mbus::Routable&) const override;

    // Implements IProtocol.
    mbus::Routable::UP decode(const vespalib::Version&, mbus::BlobRef) const override;

private:
    ProtocolSerialization5_0 _serializer5_0;
    ProtocolSerialization5_1 _serializer5_1;
    ProtocolSerialization5_2 _serializer5_2;
};

} // mbusprot
} // storage



