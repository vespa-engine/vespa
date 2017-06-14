// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>
#include <vespa/messagebus/routing/iroutingpolicy.h>

namespace documentapi {

class SearchRowPolicy : public mbus::IRoutingPolicy {
private:
    SearchRowPolicy(const SearchRowPolicy &);
    SearchRowPolicy &operator=(const SearchRowPolicy &);

public:
    /**
     * Creates a search row policy that wraps the underlying search group policy in case the parameter is something
     * other than an empty string.
     *
     * @param param The number of minimum non-OOS replies that this policy requires.
     */
    SearchRowPolicy(const string &param);
    ~SearchRowPolicy();

    void select(mbus::RoutingContext &context) override;
    void merge(mbus::RoutingContext &context) override;
private:
    uint32_t _minOk; // Hide OUT_OF_SERVICE as long as this number of replies are something else.
};

}
