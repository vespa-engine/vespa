// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.psi;

import com.intellij.psi.tree.IElementType;
import org.intellij.sdk.language.SdLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This class represent a SdTokenType.
 * @author shahariel
 */
public class SdTokenType extends IElementType {
    
    public SdTokenType(@NotNull @NonNls String debugName) {
        super(debugName, SdLanguage.INSTANCE);
    }
    
    @Override
    public String toString() {
        return "SdTokenType." + super.toString();
    }
}
