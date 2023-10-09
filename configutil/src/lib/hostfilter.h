// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <unordered_set>
#include <string>

/**
 * Simple host filter which in its default empty state implicitly includes all
 * hosts, or only an explicit subset iff at least one host has been provided
 * to the filter as part of construction.
 */
class HostFilter {
public:
    using HostSet = std::unordered_set<std::string>;
private:
    HostSet _hosts;
public:
    /**
     * Empty host filter; all hosts are implicitly included.
     */
    HostFilter() : _hosts() {}

    /**
     * Explicitly given host set; only the hosts whose name exactly match
     * one of the provided names will pass the includes(name) check.
     */
    explicit HostFilter(const std::unordered_set<std::string>& hosts)
        : _hosts(hosts)
    {
    }

    explicit HostFilter(std::unordered_set<std::string>&& hosts)
        : _hosts(std::move(hosts))
    {
    }

    HostFilter(HostFilter&&) = default;
    HostFilter& operator=(HostFilter&&) = default;

    HostFilter(const HostFilter&) = default;
    HostFilter& operator=(const HostFilter&) = default;

    bool includes(const std::string& candidate) const {
        if (_hosts.empty()) {
            return true;
        }
        return (_hosts.find(candidate) != _hosts.end());
    }
};
