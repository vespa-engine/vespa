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


## Some useful links:

1. Plugin development documentation: https://plugins.jetbrains.com/docs/intellij/welcome.html

2. JetBrains official tutorials: https://plugins.jetbrains.com/docs/intellij/custom-language-support.html and
   https://plugins.jetbrains.com/docs/intellij/custom-language-support-tutorial.html
 
3. Grammar-Kit HOWTO: Helps to understand the BNF syntax.
   https://github.com/JetBrains/Grammar-Kit/blob/master/HOWTO.md

4. How to deal with left-recursion in the grammar (in SD for example it happens in expressions). Last answer here:
   https://intellij-support.jetbrains.com/hc/en-us/community/posts/360001258300-What-s-the-alternative-to-left-recursion-in-GrammarKit-

5. Great tutorial for a custom-language-plugin, but only for the basics (mainly the parser and lexer):
   https://medium.com/@shan1024/custom-language-plugin-development-for-intellij-idea-part-01-d6a41ab96bc9

6. Code of Dart (some custom language) plugin for IntelliJ:
   https://github.com/JetBrains/intellij-plugins/tree/0f07ca63355d5530b441ca566c98f17c560e77f8/Dart