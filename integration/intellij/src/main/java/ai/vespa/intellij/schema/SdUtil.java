// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import ai.vespa.intellij.schema.model.Function;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.collect;

/**
 * Util class for the plugin's code.
 *
 * @author Shahar Ariel
 * @author bratseth
 */
public class SdUtil {
    
    public static Map<String, List<Function>> functionsIn(SdFile file) {
        Map<String, List<Function>> functionsMap = new HashMap<>();
        for (SdRankProfileDefinition rankProfile : PsiTreeUtil.findChildrenOfType(file, SdRankProfileDefinition.class)) {
            for (SdFunctionDefinition function : PsiTreeUtil.findChildrenOfType(rankProfile, SdFunctionDefinition.class)) {
                functionsMap.computeIfAbsent(function.getName(), k -> new ArrayList<>()).add(Function.from(function));
            }
        }
        return functionsMap;
    }
    
    /**
     * Returns the profiles inherited by this.
     *
     * @param baseRankProfile the rank-profile node to find the parents of
     * @return the profiles this inherits from, empty if none
     */
    public static List<SdRankProfileDefinition> getRankProfileParents(SdRankProfileDefinition baseRankProfile) {
        if (baseRankProfile == null) return List.of();
        ASTNode inheritsNode = baseRankProfile.getNode().findChildByType(SdTypes.INHERITS);
        if (inheritsNode == null) return List.of();
        return inherits(baseRankProfile).stream()
                                        .map(parentIdentifierAST -> parentIdentifierAST.getPsi().getReference())
                                        .filter(reference -> reference != null)
                                        .map(reference -> (SdRankProfileDefinition)reference.resolve())
                                        .collect(Collectors.toList());
    }

    private static List<ASTNode> inherits(SdRankProfileDefinition baseRankProfile) {
        Tokens tokens = Tokens.of(baseRankProfile);
        tokens.require(SdTypes.RANK_PROFILE);
        tokens.requireWhitespace();
        tokens.require(SdTypes.IDENTIFIER_VAL, SdTypes.IDENTIFIER_WITH_DASH_VAL);
        if ( ! tokens.skipWhitespace()) return List.of();
        if ( ! tokens.skip(SdTypes.INHERITS)) return List.of();
        tokens.requireWhitespace();
        List<ASTNode> inherited = new ArrayList<>();
        do {
            inherited.add(tokens.require(SdTypes.IDENTIFIER_VAL, SdTypes.IDENTIFIER_WITH_DASH_VAL));
            tokens.skipWhitespace();
            if ( ! tokens.skip(SdTypes.COMMA)) break;
            tokens.skipWhitespace();
        } while (true);
        return inherited;
    }
    
    public static String createFunctionDescription(SdFunctionDefinition function) {
        SdRankProfileDefinition rankProfile = PsiTreeUtil.getParentOfType(function, SdRankProfileDefinition.class);
        String rankProfileName;
        if (rankProfile != null) {
            rankProfileName = rankProfile.getName();
            List<SdArgumentDefinition> args = function.getArgumentDefinitionList();
            StringBuilder text = new StringBuilder(rankProfileName + "." + function.getName() + "(");
            for (int i = 0; i < args.size(); i++) {
                text.append(args.get(i).getName());
                if (i < args.size() - 1) {
                    text.append(", ");
                }
            }
            text.append(")");
            return text.toString();
        } else {
            return function.getName();
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
        
        // If element is the function's name in the function definition, return the function definition
        if (element.getParent() instanceof SdFunctionDefinition) {
            result.add((SdDeclaration) element.getParent());
            return result;
        }
        
        // Check if element is inside a function body
        SdFunctionDefinition macroParent = PsiTreeUtil.getParentOfType(element, SdFunctionDefinition.class);
        if (macroParent != null) {
            for (SdArgumentDefinition arg : PsiTreeUtil.findChildrenOfType(macroParent, SdArgumentDefinition.class)) {
                if (name.equals(arg.getName())) { // if the element was declared as an argument of the function
                    result.add(arg);
                    return result;
                }
            }
        }
        
        // If element is a function's name, return the most specific declaration of the function
        if (((SdIdentifier) element).isFunctionName(file, name)) {
            var profile = (SdRankProfileDefinition)PsiTreeUtil.getParentOfType(element, SdRankProfileDefinition.class);
            Optional<SdFunctionDefinition> function = findFunction(name, profile);
            if (function.isPresent()) {
                result.add(function.get());
                return result;
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

    /**
     * Returns the first encountered function of the given name in the inheritance hierarchy
     * of the given profile, or empty if it is not present.
     *
     * NOTE: Only profiles in the same file is considered
     */
    private static Optional<SdFunctionDefinition> findFunction(String functionName, SdRankProfileDefinition profile) {
        Optional<SdFunctionDefinition> function = PsiTreeUtil.collectElementsOfType(profile, SdFunctionDefinition.class)
                                                             .stream()
                                                             .filter(f -> f.getName().equals(functionName))
                                                             .findAny();
        if (function.isPresent()) return function;
        for (var parent : getRankProfileParents(profile)) {
            function = findFunction(functionName, parent);
            if (function.isPresent()) return function;
        }
        return Optional.empty();
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

    /** A collection of tokens with a current index. */
    public static class Tokens {

        private final ASTNode[] nodes;
        private int i = 0;

        private Tokens(PsiElement element) {
            nodes = element.getNode().getChildren(null);
        }

        /**
         * Advances to the next token, if it is of the given type.
         *
         * @return true if the current token was of the given type and we advanced it, false
         *         if it was not and nothing was changed
         */
        public boolean skip(IElementType ... tokenTypes) {
            boolean is = is(tokenTypes);
            if (is)
                i++;
            return is;
        }

        /**
         * Advances beyond the next token, if it is whitespace.
         *
         * @return true if the current token was of the given type and we advanced it, false
         *         if it was not and nothing was changed
         */
        public boolean skipWhitespace() {
            boolean is = isWhitespace();
            if (is)
                i++;
            return is;
        }

        /** Returns whether the current token is of the given type */
        public boolean is(IElementType ... tokenTypes) {
            for (IElementType type : tokenTypes)
                if (current().getElementType() == type) return true;
            return false;
        }

        /** Returns whether the current token is whitespace */
        public boolean isWhitespace() {
            return current().getPsi() instanceof PsiWhiteSpace;
        }

        /** Returns the current token if it is of the required type and throws otherwise. */
        public ASTNode require(IElementType ... tokenTypes) {
            if ( ! is(tokenTypes) )
                throw new IllegalArgumentException("Expected " + toString(tokenTypes) + " but got " + current());
            ASTNode current = current();
            i++;
            return current;
        }

        public void requireWhitespace() {
            if ( ! isWhitespace() )
                throw new IllegalArgumentException("Expected whitespace, but got " + current());
            i++;
        }

        /** Returns the current token (AST node), or null if we have reached the end. */
        public ASTNode current() {
            if (i >= nodes.length) return null;
            return nodes[i];
        }

        private String toString(IElementType[] tokens) {
            return Arrays.stream(tokens).map(token -> token.getDebugName()).collect(Collectors.joining(", "));
        }

        public static Tokens of(PsiElement element) {
            return new Tokens(element);
        }

    }

}
