// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import ai.vespa.intellij.schema.psi.SdDeclaration;

import javax.swing.*;

/**
 * A group of elements in the "Find Usages" window.
 *
 * @author Shahar Ariel
 */
public class SdUsageGroup implements UsageGroup {

    private final VirtualFile myFile;
    private final SmartPsiElementPointer<SdDeclaration> myElementPointer;
    private final String myText;
    private final Icon myIcon;

    public SdUsageGroup(SdDeclaration element) {
        myFile = element.getContainingFile().getVirtualFile();
        myText = StringUtil.notNullize(element.getName());
        myElementPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
        ItemPresentation presentation = element.getPresentation();
        myIcon = presentation != null ? presentation.getIcon(true) : null;
    }
    
    @Override
    public boolean isValid() {
        SdDeclaration element = myElementPointer.getElement();
        return element != null && element.isValid();
    }
    
    @Override
    public void navigate(boolean requestFocus) {
        final SdDeclaration nameElement = myElementPointer.getElement();
        if (nameElement != null) {
            nameElement.navigate(requestFocus);
        }
    }
    
    @Override
    public boolean canNavigate() {
        return isValid();
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

    @SuppressWarnings("deprecation")
    @Override
    public int compareTo(UsageGroup usageGroup) {
//        return getPresentableGroupText().compareToIgnoreCase(usageGroup.getPresentableGroupText());
        return getText(null).compareTo(usageGroup.getText(null));
    }
    
    @Override
    public boolean equals(Object object) {
        if (object instanceof SdUsageGroup) {
            final SdUsageGroup other = (SdUsageGroup) object;
            return myFile.equals(other.myFile) && myText.equals(other.myText);
        }
        return false;
    }
    
    @Override
    public FileStatus getFileStatus() {
        return isValid() ? NavigationItemFileStatus.get(myElementPointer.getElement()) : null;
    }
    
    @Override
    public int hashCode() {
        return myText.hashCode();
    }

    @Override
    public String getPresentableGroupText() {
        return myText;
    }
    
    @Override
    public Icon getIcon() {
        return myIcon;
    }
    
    // here because JetBrains asked:
    
    @SuppressWarnings("deprecation")
    public Icon getIcon(boolean isOpen) {
        return myIcon;
    }
    
    @SuppressWarnings("deprecation")
    public String getText(UsageView view) {
        return myText;
    }
    
    @Override
    public void update() {} 

}
