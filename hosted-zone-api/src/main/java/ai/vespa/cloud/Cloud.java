// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

/**
 * The cloud provider in which a cloud deployment may be running.
 *
 * This is "aws" when this runs in Amazon Web Services, "gcp" when this runs in Google Cloud Platform and
 * "azure" when this runs in Microsoft Azure.
 *
 * @author mpolden
 */
public record Cloud(String name) {
}
