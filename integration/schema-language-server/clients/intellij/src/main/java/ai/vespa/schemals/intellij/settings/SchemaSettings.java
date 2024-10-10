package ai.vespa.schemals.intellij.settings;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This is the 'Model' part of the Settings interface per MVC.
 * It simply contains the current settings state.
 */
@State(
        name = "ai.vespa.schemals.intellij.settings.SchemaLspSettings",
        storages = @Storage("VespaSchemaSettings.xml")
)
public final class SchemaSettings implements PersistentStateComponent<SchemaSettings.State> {
    public static class State {
        @NonNls
        public String javaPath = "";
    }

    private State state = new State();

    public static SchemaSettings getInstance() {
        return ApplicationManager.getApplication()
                .getService(SchemaSettings.class);
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
}
