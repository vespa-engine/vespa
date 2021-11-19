### Open Issues

1. In some cases, the grammar prefers not to enforce bad syntax, because if the parser encounters bad syntax it stops 
and can't build the PSI tree. That means none of the features will work for a file like that. For example, in cases 
where an element supposes to have zero-to-one occurrences, the grammar will treat it as zero-to-many. 
2. In order to enable the grammar recognize some keywords as identifiers (e.g. "filter" as a field's name), the 
identifier rule (named "IdentifierVal") wraps the regex (ID_REG) and the KeywordOrIdentifier rule (which contains all 
the keywords in the language). 
3. The implementation of the GoTo Declaration feature is not exactly the same as IntelliJ. In IntelliJ if a reference
has several declarations, after clicking "Goto Declaration" there is a little window with all the declarations to choose 
from. It can be done by changing the method "multiResolve" in SdReference.java to return more than one declaration. The 
problem with that is that it causes the "Find Usages" feature to not work. For now, I decided to make the plugin 
"Goto Declaration" feature show only the most specific declaration by the right rank-profile scope.
4. The "Find Usages" window can group usages only under rank-profiles and document-summaries. Other usages appear 
directly under the .sd file. In order to create another group type of usages' group, you'll need to create 2 classes: 
one for the extension "fileStructureGroupRuleProvider" (e.g. SdRankProfileGroupingRuleProvider.java), and one for the 
grouping rule itself (e.g. SdRankProfileGroupingRule.java).
Another open problem is that the navigation isn't working in the current grouping rules. It means that when clicking on 
the group headline (e.g. some name of a rank-profile) the IDE doesn't "jump" to the matching declaration.
5. Goto declaration doesn't work for document's inherits. e.g. if document A inherits from document B, B doesn't have a 
reference to its declaration.
6. There aren't any tests for the plugin.

### Some useful links:
1. JetBrains official tutorials: https://plugins.jetbrains.com/docs/intellij/custom-language-support.html and
https://plugins.jetbrains.com/docs/intellij/custom-language-support-tutorial.html
2. Grammar-Kit HOWTO: Helps to understand the BNF syntax.
   https://github.com/JetBrains/Grammar-Kit/blob/master/HOWTO.md
3. How to deal with left-recursion in the grammar (in SD for example it happens in expressions). Last answer here: 
https://intellij-support.jetbrains.com/hc/en-us/community/posts/360001258300-What-s-the-alternative-to-left-recursion-in-GrammarKit-
4. Great tutorial for a custom-language-plugin, but only for the basics (mainly the parser and lexer):
   https://medium.com/@shan1024/custom-language-plugin-development-for-intellij-idea-part-01-d6a41ab96bc9
5. Code of Dart (some custom language) plugin for IntelliJ: 
https://github.com/JetBrains/intellij-plugins/tree/0f07ca63355d5530b441ca566c98f17c560e77f8/Dart