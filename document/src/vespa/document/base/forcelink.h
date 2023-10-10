// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::ForceLink
 * \ingroup base
 *
 * \brief Class used to include some document functionality that programs that
 *        needs to get it linked, but doesn't use it can include.
 *
 * Many codebits include this, but who really depends on it?
 */
#pragma once

namespace document {

class ForceLink {
public:
    ForceLink();
};

} // document

