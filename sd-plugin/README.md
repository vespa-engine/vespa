<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
This directory holds the code for an IntelliJ plugin for reading SD files.

NOTE: This is the source code, not the plugin itself. In order to be able to use the plugin you'll need to download it 
from JetBrains Marketplace or create a zip file and load it to IntelliJ by choosing "Install Plugin from Disk".

Before starting, you should:
1. Download Gradle 7 (if you don't have it already). 
2. Make sure that the bundled Plugin DevKit plugin is enabled (inside IntelliJ). 
3. Optional- Download Grammar-Kit plugin from JetBrains Marketplace (inside IntelliJ). It helps with reading the .bnf file.
4. Optional- Download PsiViewer plugin from JetBrains Marketplace (inside IntelliJ). It helps to test the grammar defined
in the .bnf file.

### Working Process
The grammar is defined in 2 files:
- sd.bnf
- sd.flex

In order to generate the lexer and parser's code, you need to run in the command line:

    ./gradlew generateSdParser
    ./gradlew generateSdLexer

You should now have a "gen" folder next to the "java" folder, and it contains all the parser and lexer code.

NOTE- Running those tasks would reset the "gen" folder, and all the previous generated files would be deleted before the
new ones would be generated.

Now, you can run the gradle task "intellij/runIde" (or "./gradlew runIde" in the command line), open a project with some sd file and see how the plugin works on it.

### Build the Plugin
In order to build the plugin and create a zip file from it, you should run the command:
    
    ./gradlew buildPlugin

Or since it's a default task you can just run:

    ./gradlew

This task also invokes the tasks generateSdParser and generateSdLexer as a part of the building process.

Now, you'll have a zip file in the directory build\distributions.
