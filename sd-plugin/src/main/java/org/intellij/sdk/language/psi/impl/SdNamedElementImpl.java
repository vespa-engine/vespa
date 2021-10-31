// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.sdk.language.SdIcons;
import org.intellij.sdk.language.SdUtil;
import org.intellij.sdk.language.psi.SdAnnotationFieldDefinition;
import org.intellij.sdk.language.psi.SdArgumentDefinition;
import org.intellij.sdk.language.psi.SdDeclarationType;
import org.intellij.sdk.language.psi.SdDocumentAnnotationDefinition;
import org.intellij.sdk.language.psi.SdDocumentDefinition;
import org.intellij.sdk.language.psi.SdDocumentFieldDefinition;
import org.intellij.sdk.language.psi.SdDocumentStructDefinition;
import org.intellij.sdk.language.psi.SdDocumentStructFieldDefinition;
import org.intellij.sdk.language.psi.SdDocumentSummaryDefinition;
import org.intellij.sdk.language.psi.SdElementFactory;
import org.intellij.sdk.language.psi.SdFunctionDefinition;
import org.intellij.sdk.language.psi.SdIdentifier;
import org.intellij.sdk.language.psi.SdImportFieldDefinition;
import org.intellij.sdk.language.psi.SdItemRawScoreDefinition;
import org.intellij.sdk.language.psi.SdNamedElement;
import org.intellij.sdk.language.psi.SdQueryDefinition;
import org.intellij.sdk.language.psi.SdRankProfileDefinition;
import org.intellij.sdk.language.psi.SdSchemaAnnotationDefinition;
import org.intellij.sdk.language.psi.SdSchemaFieldDefinition;
import org.intellij.sdk.language.psi.SdStructFieldDefinition;
import org.intellij.sdk.language.psi.SdTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * This abstract class is used to wrap a Psi Element with SdNamedElement interface, which enables the element to be a
 * "name owner" (like an identifier). It allows the element to take a part in references, find usages and more.
 * @author Shahar Ariel
 */
public abstract class SdNamedElementImpl extends ASTWrapperPsiElement implements SdNamedElement {
    
    public SdNamedElementImpl(@NotNull ASTNode node) {
        super(node);
    }
    
    @NotNull
    public String getName() {
        ASTNode node;
        if (this instanceof SdImportFieldDefinition) {
            ASTNode asNode = this.getNode().findChildByType(SdTypes.AS);
            node = this.getNode().findChildByType(SdTypes.IDENTIFIER_VAL, asNode);
        } else if (this instanceof SdRankProfileDefinition || this instanceof SdDocumentSummaryDefinition
                   || this instanceof SdQueryDefinition) {
            node = this.getNode().findChildByType(SdTypes.IDENTIFIER_WITH_DASH_VAL);
        } else {
            node = this.getNode().findChildByType(SdTypes.IDENTIFIER_VAL);
        }
        if (node != null) {
            return node.getText();
        } else {
            return "";
        }
    }
    
    public String getTypeName() {
        return getType().toString();
    }
    
    public SdDeclarationType getType() {
        if (this instanceof SdSchemaFieldDefinition) {
            return SdDeclarationType.SCHEMA_FIELD;
        } else if (this instanceof SdDocumentSummaryDefinition) {
            return SdDeclarationType.DOCUMENT_SUMMARY;
        } else if (this instanceof SdImportFieldDefinition) {
            return SdDeclarationType.IMPORTED_FIELD;
        } else if (this instanceof SdRankProfileDefinition) {
            return SdDeclarationType.RANK_PROFILE;
        } else if (this instanceof SdFunctionDefinition) {
            return SdDeclarationType.MACRO;
        } else if (this instanceof SdArgumentDefinition) {
            return SdDeclarationType.MACRO_ARGUMENT;
        } else if (this instanceof SdDocumentDefinition) {
            return SdDeclarationType.DOCUMENT;
        } else if (this instanceof SdDocumentStructDefinition) {
            return SdDeclarationType.STRUCT;
        } else if (this instanceof SdSchemaAnnotationDefinition ||
                   this instanceof SdDocumentAnnotationDefinition) {
            return SdDeclarationType.ANNOTATION;
        } else if (this instanceof SdDocumentStructFieldDefinition) {
            return SdDeclarationType.DOCUMENT_STRUCT_FIELD;
        } else if (this instanceof SdDocumentFieldDefinition) {
            return SdDeclarationType.DOCUMENT_FIELD;
        } else if (this instanceof SdStructFieldDefinition) {
            return SdDeclarationType.STRUCT_FIELD;
        } else if (this instanceof SdAnnotationFieldDefinition) {
            return SdDeclarationType.ANNOTATION_FIELD;
        } else if (this instanceof SdQueryDefinition) {
            return SdDeclarationType.QUERY;
        } else if (this instanceof SdItemRawScoreDefinition) {
            return SdDeclarationType.ITEM_RAW_SCORE;
        } else {
            return null;
        }
    }
    
    @NotNull
    public PsiElement setName(@NotNull String newName) {
        ASTNode node;
        if (this instanceof SdImportFieldDefinition) {
            ASTNode asNode = this.getNode().findChildByType(SdTypes.AS);
            node = this.getNode().findChildByType(SdTypes.IDENTIFIER_VAL, asNode);
        } else {
            node = this.getNode().findChildByType(SdTypes.IDENTIFIER_VAL);
        }
        SdIdentifier elementName = null;
        if (node != null) {
            elementName = SdElementFactory.createIdentifierVal(this.getProject(), newName);
        } else {
            node = this.getNode().findChildByType(SdTypes.IDENTIFIER_WITH_DASH_VAL);
        }
        if (node != null) {
            elementName = SdElementFactory.createIdentifierWithDashVal(this.getProject(), newName);
        }
        if (elementName != null) {
            ASTNode newNode = elementName.getFirstChild().getNode();
            this.getNode().replaceChild(node, newNode);
        }
        return this;
    }
    
    public PsiElement getNameIdentifier() {
        ASTNode keyNode = this.getNode().findChildByType(SdTypes.ID);
        if (keyNode != null) {
            return keyNode.getPsi();
        } else {
            return null;
        }
    }
    
    @Override
    public ItemPresentation getPresentation() {
        final SdNamedElement element = this;
        return new ItemPresentation() {
            @Nullable
            @Override
            public String getPresentableText() {
                if (element instanceof SdFunctionDefinition) {
                    return SdUtil.createFunctionDescription((SdFunctionDefinition) element);
                }
                SdRankProfileDefinition rankProfileParent = PsiTreeUtil.getParentOfType(element, SdRankProfileDefinition.class);
                if (rankProfileParent != null) {
                    if (element instanceof SdQueryDefinition || element instanceof SdItemRawScoreDefinition) {
                        return element.getName() + " in " + rankProfileParent.getName();
                    }
                    return rankProfileParent.getName() + "." + element.getName();
                }
                return element.getName();
            }
            
            @Nullable
            @Override
            public String getLocationString() {
                return element.getContainingFile() != null ? element.getContainingFile().getName() : null;
            }
            
            @Nullable
            @Override
            public Icon getIcon(boolean unused) {
                if (element instanceof SdSchemaFieldDefinition || element instanceof SdDocumentFieldDefinition || 
                    element instanceof SdAnnotationFieldDefinition || element instanceof SdQueryDefinition || 
                    element instanceof SdItemRawScoreDefinition) {
                    return AllIcons.Nodes.Field;
                } else if (element instanceof SdStructFieldDefinition  ||
                           element instanceof SdDocumentStructFieldDefinition) {
                    return SdIcons.STRUCT_FIELD;
                } else if (element instanceof SdImportFieldDefinition) {
                    return SdIcons.IMPORTED_FIELD;
                } else if (element instanceof SdFunctionDefinition) {
                    return AllIcons.Nodes.Method;
                    // Didn't use isOverride() here because it causes the Structure View to load too slow 
                    // return ((SdFunctionDefinition) element).isOverride() ? SdIcons.OVERRIDE_MACRO : AllIcons.Nodes.Method;
                } else if (element instanceof SdDocumentStructDefinition) {
                    return SdIcons.STRUCT;
                } else if (element instanceof SdRankProfileDefinition) {
                    return AllIcons.Nodes.Record;
                } else if (element instanceof SdDocumentSummaryDefinition) {
                    return SdIcons.DOCUMENT_SUMMARY;
                } else if (element instanceof SdDocumentDefinition) {
                    return SdIcons.DOCUMENT;
                } else if (element instanceof SdSchemaAnnotationDefinition ||
                           element instanceof SdDocumentAnnotationDefinition) {
                    return AllIcons.Nodes.ObjectTypeAttribute;
                }
                else {
                    return null;
                }
            }
        };
    }
}
