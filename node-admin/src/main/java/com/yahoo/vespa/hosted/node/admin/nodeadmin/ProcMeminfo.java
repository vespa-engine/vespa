// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

/**
 * Represents /proc/meminfo, see proc(5).
 *
 * @param memTotalBytes     Total usable RAM (i.e., physical RAM minus a few reserved bits and the kernel binary code).
 * @param memAvailableBytes An estimate of how much memory is available for starting new applications, without swapping.
 *
 * @author hakon
 */
public record ProcMeminfo(long memTotalBytes, long memAvailableBytes) { }
