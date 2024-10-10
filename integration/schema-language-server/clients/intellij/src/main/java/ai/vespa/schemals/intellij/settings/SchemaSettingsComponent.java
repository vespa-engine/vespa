package ai.vespa.schemals.intellij.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.jetbrains.JBRFileDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * This is the 'View' part of the Settings interface per MVC
 * It defines the settings ui for the plugin.
 */
public class SchemaSettingsComponent {
    private final JPanel mainPanel;
    private final TextFieldWithBrowseButton javaPathTextField = new TextFieldWithBrowseButton();

    public SchemaSettingsComponent() {
        FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        fileChooserDescriptor.setTitle("Select Path to Java Home");
        javaPathTextField.addBrowseFolderListener("Java Path", "Select path to java home", null, fileChooserDescriptor);
        javaPathTextField.setToolTipText("Path to Java Home or directory containing a java executable. Will use the system installed version of java if not set.");

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Path to java home:"), javaPathTextField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return javaPathTextField;
    }

    @NotNull
    public String getJavaPathText() {
        return javaPathTextField.getText();
    }

    public void setJavaPathText(@NotNull String javaPath) {
        javaPathTextField.setText(javaPath);
    }
}
