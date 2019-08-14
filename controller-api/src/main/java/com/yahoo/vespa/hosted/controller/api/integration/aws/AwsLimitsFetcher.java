// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

/**
 * @author freva
 */
public interface AwsLimitsFetcher {

    /** Returns the AWS EC2 instance limits in the given AWS region */
    Ec2InstanceCounts getEc2InstanceLimits(String awsRegion);

    /** Returns the current usage of AWS EC2 instances in the given AWS region */
    Ec2InstanceCounts getEc2InstanceUsage(String awsRegion);
}
