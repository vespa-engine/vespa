package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;

import static com.yahoo.slime.SlimeUtils.optionalLong;

/**
 * Data class for application id, active session and last deployed session
 *
 * @author hmusum
 */
public class ApplicationData {

    private static final String APPLICATION_ID_FIELD = "applicationId";
    private static final String ACTIVE_SESSION_FIELD = "activeSession";
    private static final String LAST_DEPLOYED_SESSION_FIELD = "lastDeployedSession";

    private final ApplicationId applicationId;
    private final OptionalLong activeSession;
    private final OptionalLong lastDeployedSession;

    ApplicationData(ApplicationId applicationId, OptionalLong activeSession, OptionalLong lastDeployedSession) {
        this.applicationId = applicationId;
        this.activeSession = activeSession;
        this.lastDeployedSession = lastDeployedSession;
    }

    static ApplicationData fromBytes(byte[] data) {
        return fromSlime(SlimeUtils.jsonToSlime(data));
    }

    static ApplicationData fromSlime(Slime slime) {
        Cursor cursor = slime.get();
        return new ApplicationData(ApplicationId.fromSerializedForm(cursor.field(APPLICATION_ID_FIELD).asString()),
                                   optionalLong(cursor.field(ACTIVE_SESSION_FIELD)),
                                   optionalLong(cursor.field(LAST_DEPLOYED_SESSION_FIELD)));
    }

    public byte[] toJson() {
        try {
            Slime slime = new Slime();
            toSlime(slime.setObject());
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new RuntimeException("Serialization of application data to json failed", e);
        }
    }

    public ApplicationId applicationId() { return applicationId; }

    public Optional<Long> activeSession() {
        return Optional.of(activeSession)
            .filter(OptionalLong::isPresent)
            .map(OptionalLong::getAsLong);
    }

    public Optional<Long> lastDeployedSession() {
        return Optional.of(lastDeployedSession)
                .filter(OptionalLong::isPresent)
                .map(OptionalLong::getAsLong);
    }

    @Override
    public String toString() {
        return "application '" + applicationId + "', active session " + activeSession + ", last deployed session " + lastDeployedSession;
    }

    private void toSlime(Cursor object) {
        object.setString(APPLICATION_ID_FIELD, applicationId.serializedForm());
        activeSession.ifPresent(session -> object.setLong(ACTIVE_SESSION_FIELD, session));
        lastDeployedSession.ifPresent(session -> object.setLong(LAST_DEPLOYED_SESSION_FIELD, session));
    }

}
