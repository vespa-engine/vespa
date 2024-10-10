package ai.vespa.schemals.intellij.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * This is the 'Controller' part of the Settings interface per MVC
 * It glues the ui and the state together and interacts with the IntelliJ platform.
 */
public class SchemaSettingsConfigurable implements Configurable {
    private SchemaSettingsComponent settingsComponent;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Vespa Schema Settings";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return settingsComponent.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        settingsComponent = new SchemaSettingsComponent();
        return settingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        SchemaSettings.State state =
                Objects.requireNonNull(SchemaSettings.getInstance().getState());
        return !settingsComponent.getJavaPathText().equals(state.javaPath);
    }

    @Override
    public void apply() {
        SchemaSettings.State state =
                Objects.requireNonNull(SchemaSettings.getInstance().getState());
        state.javaPath = settingsComponent.getJavaPathText();
    }

    @Override
    public void reset() {
        SchemaSettings.State state =
                Objects.requireNonNull(SchemaSettings.getInstance().getState());
        settingsComponent.setJavaPathText(state.javaPath);
    }

    @Override
    public void disposeUIResources() {
        settingsComponent = null;
    }
}
