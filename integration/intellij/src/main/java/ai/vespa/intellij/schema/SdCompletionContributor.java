// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import ai.vespa.intellij.schema.psi.SdTypes;

/**
 * This class is used for the extension (in plugin.xml) to enables Auto-Complete. Partially works for now.
 *
 * @author Shahar Ariel
 */
public class SdCompletionContributor extends CompletionContributor {
    
    public SdCompletionContributor() {
        extend(CompletionType.BASIC, 
               PlatformPatterns.psiElement(SdTypes.IDENTIFIER_VAL),
               new CompletionProvider<>() {
                   public void addCompletions(CompletionParameters parameters, //completion parameters contain details of the cursor position
                                              ProcessingContext context,
                                              CompletionResultSet resultSet) { // result set contains completion details to suggest
                       resultSet.addElement(LookupElementBuilder.create(""));
                   }
               }
        );
    }
    
}
