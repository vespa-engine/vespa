// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "message_codec_provider.h"
#include <vespa/storageapi/mbusprot/protocolserialization7.h>
#include <mutex>

namespace storage::rpc {

WrappedCodec::WrappedCodec(std::shared_ptr<const document::DocumentTypeRepo> doc_type_repo)
    : _doc_type_repo(std::move(doc_type_repo)),
      _codec(std::make_unique<mbusprot::ProtocolSerialization7>(_doc_type_repo))
{
}

WrappedCodec::~WrappedCodec() = default;

MessageCodecProvider::MessageCodecProvider(std::shared_ptr<const document::DocumentTypeRepo> doc_type_repo)
    : _rw_mutex(),
      _active_codec(std::make_shared<WrappedCodec>(std::move(doc_type_repo)))
{
}

MessageCodecProvider::~MessageCodecProvider() = default;

std::shared_ptr<const WrappedCodec> MessageCodecProvider::wrapped_codec() const noexcept {
    std::shared_lock r_lock(_rw_mutex);
    return _active_codec;
}

void MessageCodecProvider::update_atomically(std::shared_ptr<const document::DocumentTypeRepo> doc_type_repo)
{
    std::unique_lock w_lock(_rw_mutex);
    _active_codec = std::make_shared<WrappedCodec>(std::move(doc_type_repo));
}

}
