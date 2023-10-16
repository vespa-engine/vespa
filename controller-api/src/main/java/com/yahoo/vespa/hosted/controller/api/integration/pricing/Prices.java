// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.pricing;

import java.util.List;

public record Prices(List<PriceInformation> priceInformationApplications, PriceInformation totalPriceInformation) {

}
