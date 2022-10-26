// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool;

import org.apache.commons.cli.Option;

import java.util.List;

/**
 * Used by Tool subclasses to describe their options and calling semantics via
 * the "--help" output from the Main program.
 *
 * @author vekterli
 */
public record ToolDescription(String helpArgSuffix,
                              String helpHeader,
                              String helpFooter,
                              List<Option> cliOptions) {

}
