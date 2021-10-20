// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.intellij.sdk.language.psi.SdTypes;
import org.jetbrains.annotations.NotNull;

public class SdFormattingModelBuilder implements FormattingModelBuilder {

    private static SpacingBuilder createSpaceBuilder(CodeStyleSettings settings) {
        return new SpacingBuilder(settings, SdLanguage.INSTANCE)
            .around(SdTypes.SYMBOL)
            .spaceIf(settings.getCommonSettings(SdLanguage.INSTANCE.getID()).SPACE_AFTER_COLON)
            .before(SdTypes.DOCUMENT_FIELD_DEFINITION)
            .none();
    }

    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
        final CodeStyleSettings codeStyleSettings = formattingContext.getCodeStyleSettings();
        return FormattingModelProvider
            .createFormattingModelForPsiFile(formattingContext.getContainingFile(),
                                             new SdBlock(formattingContext.getNode(),
                                                             Wrap.createWrap(WrapType.NONE, false),
                                                             Alignment.createAlignment(),
                                                             createSpaceBuilder(codeStyleSettings)),
                                             codeStyleSettings);
    }

}
