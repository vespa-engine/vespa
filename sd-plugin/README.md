<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
This directory holds the code for an IntteliJ plugin for reading SD files.

NOTE: This is the source code, not the plugin itself. In order to be able to use the plugin you'll need to download it from JetBrains Marketplace or create a zip file and load it to IntelliJ (details later).

Before cloning, you should download Gradle and create a Gradle project.
You should also download Grammar-Kit plugin from the Marketplace.

The grammar is defined in 2 files:
- sd.bnf
- sd.flex

After cloning, you should:
1. Right-click the sd.bnf file and press "Generate Parser Code" 
2. Right-click the sd.flex file and press "Run JFlex Generator"
 
Now you should have a "gen" folder next to the "java" folder, and it contains all the parser and lexer code.

Important note! After any change in one of this 2 files (bnf, flex) you'll need to generate again. The proper way is to delete the "gen" folder and then do 1-2 again.

Now, you can run the gradle task "intellij/runIde", open a project with some sd file and see how the plugin works on it.

In order to test the plugin locally (on you IDE, not by running the gradle task "runIde"), you can run the gradle task 
"intellij/buildPlugin". It would create a zip file in the directory build\distributions. You can load it to IntelliJ by 
clicking the "settings" in preferences/Plugins and click "Install Plugin from disk".