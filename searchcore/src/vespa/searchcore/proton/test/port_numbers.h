// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton::test::port_numbers {

/*
 * The following port numbers are used by unit tests in the searchcore
 * module. They should all be in the range 9001..9499.
 */

constexpr int docsummary_tls_port = 9013;
constexpr int documentdb_tls_port = 9014;
constexpr int feedhandler_tls_port = 9016;
constexpr int persistenceconformance_tls_port = 9017;
constexpr int proton_disk_layout_tls_port = 9018;

}
