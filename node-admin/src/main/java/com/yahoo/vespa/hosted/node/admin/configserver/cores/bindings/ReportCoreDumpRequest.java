// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.cores.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Jackson class of JSON request, with names of fields verified in unit test.
 *
 * @author hakonhall
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportCoreDumpRequest {
    public List<String> backtrace;
    public List<String> backtrace_all_threads;
    public String bin_path;
    public String coredump_path;
    public String cpu_microcode_version;
    public String docker_image;
    public String kernel_version;
    public String vespa_version;

    public ReportCoreDumpRequest() {}
}
