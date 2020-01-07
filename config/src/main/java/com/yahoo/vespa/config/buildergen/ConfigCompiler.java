// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.buildergen;

/**
 * Interface towards compilers for compiling builders from a config class definition.
 *
 * @author Ulf Lilleengen
 */
public interface ConfigCompiler {

    CompiledBuilder compile(ConfigDefinitionClass defClass);

}
