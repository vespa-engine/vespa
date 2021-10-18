package org.intellij.sdk.language;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.intellij.sdk.language.psi.SdTypes;
import org.jetbrains.annotations.NotNull;

public class SdCompletionContributor extends CompletionContributor {
    
    
    public SdCompletionContributor() {
        extend(CompletionType.BASIC, 
               PlatformPatterns.psiElement(SdTypes.IDENTIFIER_VAL),
               new CompletionProvider<>() {
                   public void addCompletions(@NotNull CompletionParameters parameters, //completion parameters contain details of the cursor position
                                              @NotNull ProcessingContext context,
                                              @NotNull CompletionResultSet resultSet) { //result set contains completion details to suggest
                       resultSet.addElement(LookupElementBuilder.create(""));
                   }
               }
        );
    }
    
}
