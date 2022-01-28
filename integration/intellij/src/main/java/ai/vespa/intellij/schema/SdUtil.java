// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import ai.vespa.intellij.schema.psi.SdAnnotationFieldDefinition;
import ai.vespa.intellij.schema.psi.SdArgumentDefinition;
import ai.vespa.intellij.schema.psi.SdDeclaration;
import ai.vespa.intellij.schema.psi.SdDocumentAnnotationDefinition;
import ai.vespa.intellij.schema.psi.SdDocumentDefinition;
import ai.vespa.intellij.schema.psi.SdDocumentFieldDefinition;
import ai.vespa.intellij.schema.psi.SdDocumentStructDefinition;
import ai.vespa.intellij.schema.psi.SdDocumentStructFieldDefinition;
import ai.vespa.intellij.schema.psi.SdDocumentSummaryDefinition;
import ai.vespa.intellij.schema.psi.SdFieldTypeName;
import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdIdentifier;
import ai.vespa.intellij.schema.psi.SdImportFieldDefinition;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.psi.SdSchemaAnnotationDefinition;
import ai.vespa.intellij.schema.psi.SdSchemaFieldDefinition;
import ai.vespa.intellij.schema.psi.SdSummaryDefinition;
import ai.vespa.intellij.schema.psi.SdTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Util class for the plugin's code.
 *
 * @author Shahar Ariel
 */
public class SdUtil {
    
    public static HashMap<String, List<PsiElement>> createMacrosMap(SdFile file) {
        HashMap<String, List<PsiElement>> macrosMap = new HashMap<>();
        for (SdRankProfileDefinition rankProfile : PsiTreeUtil
            .findChildrenOfType(file, SdRankProfileDefinition.class)) {
            for (SdFunctionDefinition macro : PsiTreeUtil.findChildrenOfType(rankProfile, SdFunctionDefinition.class)) {
                macrosMap.computeIfAbsent(macro.getName(), k -> new ArrayList<>()).add(macro);
            }
        }
        return macrosMap;
    }
    
    /**
     * @param baseRankProfile the rank-profile node to find its parent
     * @return the rank-profile that the baseRankProfile inherits from, or null if it doesn't exist
     */
    public static PsiElement getRankProfileParent(SdRankProfileDefinition baseRankProfile) {
        if (baseRankProfile == null) {
            return null;
        }
        ASTNode inheritsNode = baseRankProfile.getNode().findChildByType(SdTypes.INHERITS);
        if (inheritsNode == null) {
            return null;
        }
        ASTNode ancestorAST = baseRankProfile.getNode().findChildByType(SdTypes.IDENTIFIER_VAL, inheritsNode);
        if (ancestorAST == null) {
            ancestorAST = baseRankProfile.getNode().findChildByType(SdTypes.IDENTIFIER_WITH_DASH_VAL, inheritsNode);
            if (ancestorAST == null) {
                return null;
            }
        }
        SdIdentifier ancestorIdentifier = (SdIdentifier) ancestorAST.getPsi();
        PsiReference ref = ancestorIdentifier.getReference();
        return ref != null ? ref.resolve() : null;
    }
    
    public static String createFunctionDescription(SdFunctionDefinition macro) {
        SdRankProfileDefinition rankProfile = PsiTreeUtil.getParentOfType(macro, SdRankProfileDefinition.class);
        String rankProfileName;
        if (rankProfile != null) {
            rankProfileName = rankProfile.getName();
            List<SdArgumentDefinition> args = macro.getArgumentDefinitionList();
            StringBuilder text = new StringBuilder(rankProfileName + "." + macro.getName() + "(");
            for (int i = 0; i < args.size(); i++) {
                text.append(args.get(i).getName());
                if (i < args.size() - 1) {
                    text.append(", ");
                }
            }
            text.append(")");
            return text.toString();
        } else {
            return macro.getName();
        }
    }
    
    public static List<SdDeclaration> findDeclarationsByName(PsiElement file, String name) {
        List<SdDeclaration> result = new ArrayList<>();
        
        for (SdDeclaration declaration : PsiTreeUtil.collectElementsOfType(file, SdDeclaration.class)) {
            if (name.equals(declaration.getName())) {
                result.add(declaration);
            }
        }
        return result;
    }
    
    
    public static List<SdDeclaration> findDeclarationsByScope(PsiElement file, PsiElement element, String name) {
        List<SdDeclaration> result = new ArrayList<>();
        
        // If element is a field declared in another file (to be imported), return the declaration from the other file
        // if found, else return an empty result list
        if (element.getParent() instanceof SdImportFieldDefinition &&
            element.getNextSibling().getNextSibling().getText().equals("as")) {
            Project project = file.getProject();
            
            PsiReference docFieldRef = element.getPrevSibling().getPrevSibling().getReference();
            PsiElement docField = docFieldRef != null ? docFieldRef.resolve() : null;
            SdFieldTypeName fieldType = docField != null ? PsiTreeUtil.findChildOfType(docField, SdFieldTypeName.class) : null;
            SdIdentifier docIdentifier = fieldType != null ? PsiTreeUtil.findChildOfType(fieldType, SdIdentifier.class) : null;
            String docName = docIdentifier != null ? ((PsiNamedElement) docIdentifier).getName() : null;
            if (docName == null) {
                return result;
            }
    
            Collection<VirtualFile> virtualFiles = FileTypeIndex.getFiles(SdFileType.INSTANCE, GlobalSearchScope.allScope(project));

            for (VirtualFile vfile : virtualFiles) {
                SdFile sdFile = (SdFile) PsiManager.getInstance(project).findFile(vfile);
                if (sdFile != null &&
                    ( !sdFile.getName().equals(docName + ".sd") && !sdFile.getName().equals(docName + ".profile"))) {
                    continue;
                }
                result.addAll(SdUtil.findDeclarationsByName(sdFile, name));
            }
            return result;
        }
        
        // If element is the macro's name in the macro definition, return the macro definition
        if (element.getParent() instanceof SdFunctionDefinition) {
            result.add((SdDeclaration) element.getParent());
            return result;
        }
        
        // Check if element is inside a macro body
        SdFunctionDefinition macroParent = PsiTreeUtil.getParentOfType(element, SdFunctionDefinition.class);
        if (macroParent != null) {
            for (SdArgumentDefinition arg : PsiTreeUtil.findChildrenOfType(macroParent, SdArgumentDefinition.class)) {
                if (name.equals(arg.getName())) { // if the element was declared as an argument of the macro
                    result.add(arg);
                    return result;
                }
            }
        }
        
        // If element is a macro's name, return the most specific declaration of the macro
        if (((SdIdentifier) element).isFunctionName(file, name)) {
            PsiElement curRankProfile = PsiTreeUtil.getParentOfType(element, SdRankProfileDefinition.class);
            while (curRankProfile != null) {
                for (SdFunctionDefinition macro : PsiTreeUtil.collectElementsOfType(curRankProfile, SdFunctionDefinition.class)) {
                    if (macro.getName() != null && macro.getName().equals(name)) {
                        result.add(macro);
                        return result;
                    }
                }
                curRankProfile = getRankProfileParent((SdRankProfileDefinition) curRankProfile);
            }
        }
        
        for (PsiElement declaration : PsiTreeUtil.collectElements(file, psiElement ->
            psiElement instanceof SdDeclaration && !(psiElement instanceof SdArgumentDefinition))) {
            if (name.equals(((SdDeclaration) declaration).getName())) {
                result.add((SdDeclaration) declaration);
                break;
            }
        }
        
        return result;
    }
    
    public static List<SdDeclaration> findDeclarations(PsiElement element) {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(element, SdDeclaration.class));
    }
    
    public static List<PsiElement> findSchemaChildren(PsiElement element) {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(element, new Class[]{SdDocumentDefinition.class,
                                                                                      SdSchemaFieldDefinition.class,
                                                                                      SdImportFieldDefinition.class,
                                                                                      SdSchemaAnnotationDefinition.class,
                                                                                      SdDocumentSummaryDefinition.class,
                                                                                      SdRankProfileDefinition.class}));
    }
    
    public static List<PsiElement> findAnnotationChildren(PsiElement element) {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(element, SdAnnotationFieldDefinition.class));
    }
    
    public static List<PsiElement> findDocumentChildren(PsiElement element) {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(element, new Class[]{SdDocumentStructDefinition.class,
                                                                                      SdDocumentAnnotationDefinition.class,
                                                                                      SdDocumentFieldDefinition.class}));
    }
    
    public static List<PsiElement> findDocumentStructChildren(PsiElement element) {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(element, SdDocumentStructFieldDefinition.class));
    }
    
    public static List<PsiElement> findRankProfileChildren(PsiElement element) {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(element, SdFunctionDefinition.class));
    }
    
    public static List<PsiElement> findDocumentSummaryChildren(PsiElement element) {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(element, SdSummaryDefinition.class));
    }
    
}
