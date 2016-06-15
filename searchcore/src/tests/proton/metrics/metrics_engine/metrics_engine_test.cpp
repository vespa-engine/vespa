// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for metrics_engine.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("metrics_engine_test");

#include <vespa/searchcore/proton/metrics/metrics_engine.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace proton;

namespace {

TEST("require that the metric proton.diskusage is the sum of the documentDB "
     "diskusage metrics.") {
    MetricsEngine metrics_engine;

    DocumentDBMetricsCollection metrics1("type1", 1);
    DocumentDBMetricsCollection metrics2("type2", 1);
    metrics1.getMetrics().index.diskUsage.addValue(100);
    metrics2.getMetrics().index.diskUsage.addValue(1000);

    metrics_engine.addDocumentDBMetrics(metrics1);
    metrics_engine.addDocumentDBMetrics(metrics2);

    EXPECT_EQUAL(1100, metrics_engine.legacyRoot().diskUsage.getLongValue("value"));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
