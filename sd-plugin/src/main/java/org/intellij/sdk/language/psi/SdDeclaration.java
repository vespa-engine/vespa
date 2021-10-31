// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.psi;

import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;

/**
 * This interface represents a declaration in the SD language.
 * @author Shahar Ariel
 */
public interface SdDeclaration extends PsiElement, NavigationItem, SdNamedElement {}
