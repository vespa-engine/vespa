// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "protocolserialization5_2.h"
#include "protocolserialization6_0.h"
#include <vespa/messagebus/iprotocol.h>

namespace storage::mbusprot {

class StorageProtocol final : public mbus::IProtocol
{
public:
    typedef std::shared_ptr<StorageProtocol> SP;

    static mbus::string NAME;

    StorageProtocol(const std::shared_ptr<const document::DocumentTypeRepo>,
                    const documentapi::LoadTypeSet& loadTypes,
                    bool activateBucketSpaceSerialization = false);
    ~StorageProtocol();

    const mbus::string& getName() const override { return NAME; }
    mbus::IRoutingPolicy::UP createPolicy(const mbus::string& name, const mbus::string& param) const override;
    mbus::Blob encode(const vespalib::Version&, const mbus::Routable&) const override;
    mbus::Routable::UP decode(const vespalib::Version&, mbus::BlobRef) const override;
    virtual bool requireSequencing() const override { return true; }
private:
    ProtocolSerialization5_0 _serializer5_0;
    ProtocolSerialization5_1 _serializer5_1;
    ProtocolSerialization5_2 _serializer5_2;
    ProtocolSerialization6_0 _serializer6_0;
    bool _activateBucketSpaceSerialization;
};

}
