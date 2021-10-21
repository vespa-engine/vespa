// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.psi;

import com.intellij.psi.tree.IElementType;
import org.intellij.sdk.language.SdLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This class represent a SdElementType.
 * @author shahariel
 */
public class SdElementType extends IElementType {
    
    public SdElementType(@NotNull @NonNls String debugName) {
        super(debugName, SdLanguage.INSTANCE);
    }
}
