// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/***
 * Represents a list of customer RPM services to run on a host.
 *
 * @author Sigve Røkenes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class CustomerRpmServiceList {

    @JsonProperty("services")
    private final List<CustomerRpmService> services;

    @JsonCreator
    public CustomerRpmServiceList(@JsonProperty("services") List<CustomerRpmService> services) {
        this.services = services;
    }

    public List<CustomerRpmService> services() {
        return services;
    }

    public static CustomerRpmServiceList empty() {
        return new CustomerRpmServiceList(List.of());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerRpmServiceList serviceList = (CustomerRpmServiceList) o;
        return Objects.equals(services, serviceList.services);
    }

    @Override
    public int hashCode() {
        return Objects.hash(services);
    }

}
