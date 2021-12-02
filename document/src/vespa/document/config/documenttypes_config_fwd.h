// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/**
 * Forward-declaration of the types
 * DocumenttypesConfig and DocumenttypesConfigBuilder
 * (globally visible).
 **/

namespace document::config::internal {
class InternalDocumenttypesType;
}

using DocumenttypesConfigBuilder = document::config::internal::InternalDocumenttypesType;
using DocumenttypesConfig = const document::config::internal::InternalDocumenttypesType;
