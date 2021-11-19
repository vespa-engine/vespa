<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

# SD Reader

IntelliJ plugin for working with Vespa application packages.

## Using the plugin

Download it from JetBrains Marketplace.

## Using a local build

Build (see below) and load it in IntelliJ by choosing 
Preferences -> Plugins -> Press the gear icon -> Install Plugin from Disk.

## Building the plugin

    gradle

This produces an installable plugin .zip in the directory build/distributions

*Prerequisite*: gradle 7.

Why gradle? Because it's what JetBrains supports for building plugins.
However, gradle is configured with a maven directory layout.

## Optional IntelliJ plugins for working with plugin development

1. Plugin DevKit 
2. Grammar-Kit: For reading the .bnf file.
3. PsiViewer: Helps testing the bnf grammar.

With the first (?), you can run the gradle task "intellij/runIde" (or "./gradlew runIde" in the command line), 
open a project with some sd file and see how the plugin works on it.
