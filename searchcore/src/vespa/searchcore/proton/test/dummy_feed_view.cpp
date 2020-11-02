// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_feed_view.h"
#include <cassert>

namespace proton::test {

DummyFeedView::DummyFeedView()
    : _docTypeRepo()
{
}

DummyFeedView::DummyFeedView(std::shared_ptr<const document::DocumentTypeRepo> docTypeRepo)
    : _docTypeRepo(std::move(docTypeRepo))
{
}

DummyFeedView::~DummyFeedView() = default;

}
