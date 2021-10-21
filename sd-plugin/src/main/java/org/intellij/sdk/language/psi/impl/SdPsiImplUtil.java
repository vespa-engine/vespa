// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.sdk.language.SdIcons;
import org.intellij.sdk.language.SdReference;
import org.intellij.sdk.language.SdUtil;
import org.intellij.sdk.language.psi.SdAnnotationFieldDefinition;
import org.intellij.sdk.language.psi.SdArgumentDefinition;
import org.intellij.sdk.language.psi.SdDeclaration;
import org.intellij.sdk.language.psi.SdDeclarationType;
import org.intellij.sdk.language.psi.SdDocumentAnnotationDefinition;
import org.intellij.sdk.language.psi.SdDocumentDefinition;
import org.intellij.sdk.language.psi.SdDocumentFieldDefinition;
import org.intellij.sdk.language.psi.SdDocumentStructDefinition;
import org.intellij.sdk.language.psi.SdDocumentStructFieldDefinition;
import org.intellij.sdk.language.psi.SdDocumentSummaryDefinition;
import org.intellij.sdk.language.psi.SdElementFactory;
import org.intellij.sdk.language.psi.SdFile;
import org.intellij.sdk.language.psi.SdFirstPhaseDefinition;
import org.intellij.sdk.language.psi.SdIdentifier;
import org.intellij.sdk.language.psi.SdIdentifierVal;
import org.intellij.sdk.language.psi.SdFunctionDefinition;
import org.intellij.sdk.language.psi.SdIdentifierWithDashVal;
import org.intellij.sdk.language.psi.SdImportFieldDefinition;
import org.intellij.sdk.language.psi.SdItemRawScoreDefinition;
import org.intellij.sdk.language.psi.SdQueryDefinition;
import org.intellij.sdk.language.psi.SdRankProfileDefinition;
import org.intellij.sdk.language.psi.SdSchemaAnnotationDefinition;
import org.intellij.sdk.language.psi.SdSchemaFieldDefinition;
import org.intellij.sdk.language.psi.SdStructFieldDefinition;
import org.intellij.sdk.language.psi.SdSummaryDefinition;
import org.intellij.sdk.language.psi.SdTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * In this class there are implementations of methods of rules in the .bnf file. While generating the psi files
 * (classes and interfaces) the implementations would be taken from here.
 * @author shahariel
 */
public class SdPsiImplUtil {
    
    @NotNull
    public static PsiReference getReference(SdIdentifier element) {
        return new SdReference(element, new TextRange(0, element.getName().length()));
    }
    
    public static SdDeclarationType getType(SdDeclaration declaration) {
        if (declaration instanceof SdSchemaFieldDefinition) {
            return SdDeclarationType.SCHEMA_FIELD;
        } else if (declaration instanceof SdDocumentSummaryDefinition) {
            return SdDeclarationType.DOCUMENT_SUMMARY;
        } else if (declaration instanceof SdImportFieldDefinition) {
            return SdDeclarationType.IMPORTED_FIELD;
        } else if (declaration instanceof SdRankProfileDefinition) {
            return SdDeclarationType.RANK_PROFILE;
        } else if (declaration instanceof SdFunctionDefinition) {
            return SdDeclarationType.MACRO;
        } else if (declaration instanceof SdArgumentDefinition) {
            return SdDeclarationType.MACRO_ARGUMENT;
        } else if (declaration instanceof SdDocumentDefinition) {
            return SdDeclarationType.DOCUMENT;
        } else if (declaration instanceof SdDocumentStructDefinition) {
            return SdDeclarationType.STRUCT;
        } else if (declaration instanceof SdSchemaAnnotationDefinition ||
                   declaration instanceof SdDocumentAnnotationDefinition) {
            return SdDeclarationType.ANNOTATION;
        } else if (declaration instanceof SdDocumentStructFieldDefinition) {
            return SdDeclarationType.DOCUMENT_STRUCT_FIELD;
        } else if (declaration instanceof SdDocumentFieldDefinition) {
            return SdDeclarationType.DOCUMENT_FIELD;
        } else if (declaration instanceof SdStructFieldDefinition) {
            return SdDeclarationType.STRUCT_FIELD;
        } else if (declaration instanceof SdAnnotationFieldDefinition) {
            return SdDeclarationType.ANNOTATION_FIELD;
        } else if (declaration instanceof SdQueryDefinition) {
            return SdDeclarationType.QUERY;
        } else if (declaration instanceof SdItemRawScoreDefinition) {
            return SdDeclarationType.ITEM_RAW_SCORE;
        } else {
            return null;
        }
    }
    
    public static String getTypeName(SdDeclaration declaration) {
        return declaration.getType().toString();
    }
    
    public static boolean isFunctionName(SdIdentifier identifier, PsiElement file) {
        String name = identifier.getName();
        for (SdFunctionDefinition macro : PsiTreeUtil.collectElementsOfType(file, SdFunctionDefinition.class)) {
            if (name.equals(macro.getName())) {
                return true;
            }
        }
        return false;
    }
    
    public static boolean isOverride(SdFunctionDefinition macroDeclaration) {
        String macroName = macroDeclaration.getName();
        
        SdRankProfileDefinition curRankProfile = PsiTreeUtil.getParentOfType(macroDeclaration, SdRankProfileDefinition.class);
        if (curRankProfile != null) {
            curRankProfile = (SdRankProfileDefinition) SdUtil.getRankProfileParent(curRankProfile);
        }
        while (curRankProfile != null) {
            for (SdFunctionDefinition macro : PsiTreeUtil.collectElementsOfType(curRankProfile, SdFunctionDefinition.class)) {
                if (macro.getName().equals(macroName)) {
                    return true;
                }
            }
            curRankProfile = (SdRankProfileDefinition) SdUtil.getRankProfileParent(curRankProfile);
        }
        return false;
        
    }
    
    // ################################### //
    // ##### getName implementations ##### //
    // ################################### //
    
    @NotNull
    public static String getName(SdIdentifier element) {
        if (element != null) {
            // IMPORTANT: Convert embedded escaped spaces to simple spaces
            return element.getText().replaceAll("\\\\ ", " ");
        } else {
            return "";
        }
    }
    
    @NotNull
    public static String getName(SdDeclaration declaration) {
        ASTNode node;
        if (declaration instanceof SdImportFieldDefinition) {
            ASTNode asNode = declaration.getNode().findChildByType(SdTypes.AS);
            node = declaration.getNode().findChildByType(SdTypes.IDENTIFIER_VAL, asNode);
        } else if (declaration instanceof SdRankProfileDefinition || declaration instanceof SdDocumentSummaryDefinition
                   || declaration instanceof SdQueryDefinition) {
            node = declaration.getNode().findChildByType(SdTypes.IDENTIFIER_WITH_DASH_VAL);
        } else {
            node = declaration.getNode().findChildByType(SdTypes.IDENTIFIER_VAL);
        }
        if (node != null) {
            return node.getText();
        } else {
            return "";
        }
    }
    
    @NotNull
    public static String getName(SdSummaryDefinition summary) {
        ASTNode node;
        node = summary.getNode().findChildByType(SdTypes.IDENTIFIER_WITH_DASH_VAL);
        if (node != null) {
            return node.getText();
        } else {
            return "";
        }
    }
    
    @NotNull
    public static String getName(SdFirstPhaseDefinition firstPhase) {
        SdRankProfileDefinition rankProfile = PsiTreeUtil.getParentOfType(firstPhase, SdRankProfileDefinition.class);
        if (rankProfile == null) {
            return "";
        }
        return "first-phase of " + rankProfile.getName();
    }
    
    // ################################### //
    // ##### setName implementations ##### //
    // ################################### //
    
    @NotNull
    public static PsiElement setName(SdIdentifierVal element, String newName) {
        ASTNode node =  element.getNode().getFirstChildNode();
        if (node != null) {
            SdIdentifierVal elementName = SdElementFactory.createIdentifierVal(element.getProject(), newName);
            ASTNode newNode = elementName.getFirstChild().getNode();
            element.getNode().replaceChild(node, newNode);
        }
        return element;
    }
    
    @NotNull
    public static PsiElement setName(SdIdentifierWithDashVal element, String newName) {
        ASTNode node =  element.getNode().getFirstChildNode();
        if (node != null) {
            SdIdentifierWithDashVal elementName = SdElementFactory.createIdentifierWithDashVal(element.getProject(), newName);
            ASTNode newNode = elementName.getFirstChild().getNode();
            element.getNode().replaceChild(node, newNode);
        }
        return element;
    }
    
    @NotNull
    public static PsiElement setName(SdDeclaration element, String newName) {
        ASTNode node;
        if (element instanceof SdImportFieldDefinition) {
            ASTNode asNode = element.getNode().findChildByType(SdTypes.AS);
            node = element.getNode().findChildByType(SdTypes.IDENTIFIER_VAL, asNode);
        } else {
            node = element.getNode().findChildByType(SdTypes.IDENTIFIER_VAL);
        }
        SdIdentifier elementName = null;
        if (node != null) {
            elementName = SdElementFactory.createIdentifierVal(element.getProject(), newName);
        } else {
            node = element.getNode().findChildByType(SdTypes.IDENTIFIER_WITH_DASH_VAL);
        }
        if (node != null) {
            elementName = SdElementFactory.createIdentifierWithDashVal(element.getProject(), newName);
        }
        if (elementName != null) {
            ASTNode newNode = elementName.getFirstChild().getNode();
            element.getNode().replaceChild(node, newNode);
        }
        return element;
    }
    
    // ##################################### //
    // # getNameIdentifier implementations # //
    // ##################################### //
    
    public static PsiElement getNameIdentifier(SdIdentifierVal element) {
        ASTNode keyNode = element.getNode().findChildByType(SdTypes.ID);
        if (keyNode != null) {
            return keyNode.getPsi();
        } else {
            return null;
        }
    }
    
    public static PsiElement getNameIdentifier(SdDeclaration element) {
        ASTNode keyNode = element.getNode().findChildByType(SdTypes.ID);
        if (keyNode != null) {
            return keyNode.getPsi();
        } else {
            return null;
        }
    }
    
    // ################################### //
    // # getPresentation implementations # //
    // ################################### //
    
    public static ItemPresentation getPresentation(final SdDeclaration element) {
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
                if (element instanceof SdFile) {
                    return SdIcons.FILE;
                } else if (element instanceof SdSchemaFieldDefinition || element instanceof SdDocumentFieldDefinition ||
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
    
    public static ItemPresentation getPresentation(final SdSummaryDefinition element) {
        return new ItemPresentation() {
            
            @Override
            public String getPresentableText() {
                return element.getName();
            }
            
            @Nullable
            @Override
            public String getLocationString() {
                return element.getContainingFile() != null ? element.getContainingFile().getName() : null;
            }
            
            @Override
            public Icon getIcon(boolean unused) {
                return SdIcons.SUMMARY;
            }
        };
    }
    
    public static ItemPresentation getPresentation(final SdFirstPhaseDefinition element) {
        return new ItemPresentation() {
            
            @Override
            public String getPresentableText() {
                SdRankProfileDefinition rankProfile = PsiTreeUtil.getParentOfType(element, SdRankProfileDefinition.class);
                if (rankProfile == null) {
                    return "";
                }
                return "first-phase of " + rankProfile.getName();
            }
            
            @Nullable
            @Override
            public String getLocationString() {
                return element.getContainingFile() != null ? element.getContainingFile().getName() : null;
            }
            
            @Override
            public Icon getIcon(boolean unused) {
                return SdIcons.FIRST_PHASE;
            }
        };
    }
    
}
