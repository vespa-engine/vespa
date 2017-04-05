// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "protocolserialization5_2.h"
#include <vespa/messagebus/iprotocol.h>

namespace storage {
namespace mbusprot {

class StorageProtocol : public mbus::IProtocol
{
public:
    typedef std::shared_ptr<StorageProtocol> SP;

    static mbus::string NAME;

    StorageProtocol(const document::DocumentTypeRepo::SP,
                    const documentapi::LoadTypeSet& loadTypes);

    const mbus::string& getName() const override { return NAME; }
    mbus::IRoutingPolicy::UP createPolicy(const mbus::string& name,
                                          const mbus::string& param) const override;
    mbus::Blob encode(const vespalib::Version&, const mbus::Routable&) const override;
    mbus::Routable::UP decode(const vespalib::Version&, mbus::BlobRef) const override;

private:
    ProtocolSerialization5_0 _serializer5_0;
    ProtocolSerialization5_1 _serializer5_1;
    ProtocolSerialization5_2 _serializer5_2;
};

} // mbusprot
} // storage
