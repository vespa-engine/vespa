### Open Issues

In some cases, the parser stops on bad syntax and can't build the PSI tree. 
That means no features will work in the file. 

To enable the grammar recognize some keywords as identifiers (e.g. "filter" as a field's name), 
the identifier rule (named "IdentifierVal") wraps the regex (ID_REG) and the KeywordOrIdentifier 
rule (which contains all the keywords in the language). 

The implementation of the GoTo Declaration feature is not exactly the same as IntelliJ. 
In IntelliJ if a reference has several declarations, after clicking "Goto Declaration"
there is a little window with all the declarations to choose from. 
It can be done by changing the method "multiResolve" in SdReference.java to return 
more than one declaration. The problem with that is that it causes the "Find Usages" 
feature to not work. For now the plugin "Goto Declaration" feature shows only the 
most specific declaration by the right rank-profile scope.

The "Find Usages" window can group usages only under rank-profiles and document-summaries.
Other usages appear directly under the .sd file. To create another group type of usages' group, 
you'll need to create 2 classes: one for the extension "fileStructureGroupRuleProvider" 
(e.g. SdRankProfileGroupingRuleProvider.java), and one for the 
grouping rule itself (e.g. SdRankProfileGroupingRule.java).
Another open problem is that the navigation isn't working in the current grouping rules. 
It means that when clicking on the group headline (e.g. some name of a rank-profile) 
the IDE doesn't "jump" to the matching declaration.

Goto declaration doesn't work for document and schema inherits. E.g. if document A inherits from 
document B, B doesn't have a reference to its declaration.

There are no tests for the call structure ("hierarchy") functionality, 
and it is not yet using the model classes.

Semicolons in indexing statements are marked with red background for some reason. 
They are not marked as errors.

Type suggestions should include all primitive types, not just annotations

Even if the parser continues, only the first error in a file is marked.

Aliases with dot does not work.


