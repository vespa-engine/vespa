// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "authorization_mode.h"
#include "certificate_verification_callback.h"
#include "peer_policies.h"

namespace vespalib::net::tls {

std::shared_ptr<CertificateVerificationCallback> create_verify_callback_from(AuthorizedPeers authorized_peers);

}
