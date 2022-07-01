// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "protocolserialization7.h"
#include <vespa/messagebus/iprotocol.h>

namespace storage::mbusprot {

class StorageProtocol final : public mbus::IProtocol
{
public:
    typedef std::shared_ptr<StorageProtocol> SP;

    static mbus::string NAME;

    explicit StorageProtocol(const std::shared_ptr<const document::DocumentTypeRepo>);
    ~StorageProtocol() override;

    const mbus::string& getName() const override { return NAME; }
    mbus::IRoutingPolicy::UP createPolicy(const mbus::string& name, const mbus::string& param) const override;
    mbus::Blob encode(const vespalib::Version&, const mbus::Routable&) const override;
    mbus::Routable::UP decode(const vespalib::Version&, mbus::BlobRef) const override;
private:
    ProtocolSerialization7   _serializer7_0;
};

}
