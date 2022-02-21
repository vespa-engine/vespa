// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import ai.vespa.intellij.schema.psi.SdDeclaration;
import ai.vespa.intellij.schema.psi.SdFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class is used for the extension (in plugin.xml) to enable the "Go To Symbol" feature.
 *
 * @author Shahar Ariel
 */
public class SdChooseByNameContributor implements ChooseByNameContributor {
    
    @Override
    public String[] getNames(Project project, boolean includeNonProjectItems) {
        Collection<VirtualFile> virtualFiles = FileTypeIndex.getFiles(SdFileType.INSTANCE, GlobalSearchScope.allScope(project));
        
        List<SdDeclaration> declarations = new ArrayList<>();
        
        for (VirtualFile file : virtualFiles) {
            SdFile sdFile = (SdFile) PsiManager.getInstance(project).findFile(file);
            declarations.addAll(SdUtil.findDeclarations(sdFile));
        }
        
        List<String> names = new ArrayList<>(declarations.size());
        for (SdDeclaration declaration : declarations) {
            names.add(declaration.getName());
        }
        return names.toArray(new String[names.size()]);
    }
    
    @Override
    public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
        Collection<VirtualFile> virtualFiles = FileTypeIndex.getFiles(SdFileType.INSTANCE, GlobalSearchScope.allScope(project));
        
        List<SdDeclaration> declarations = new ArrayList<>();
        
        for (VirtualFile file : virtualFiles) {
            SdFile sdFile = (SdFile) PsiManager.getInstance(project).findFile(file);
            declarations.addAll(SdUtil.findDeclarationsByName(sdFile, name));
        }
        
        return declarations.toArray(new NavigationItem[declarations.size()]);
    }
    
}
