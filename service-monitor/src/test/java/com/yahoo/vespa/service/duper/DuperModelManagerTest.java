// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import ai.vespa.http.DomainName;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Stream;

import static com.yahoo.vespa.service.duper.DuperModelManager.configServerApplication;
import static com.yahoo.vespa.service.duper.DuperModelManager.controllerApplication;
import static com.yahoo.vespa.service.duper.DuperModelManager.proxyHostApplication;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class DuperModelManagerTest {
    private final SuperModelProvider superModelProvider = mock(SuperModelProvider.class);
    private final SuperModel superModel = mock(SuperModel.class);
    private final DuperModel duperModel = mock(DuperModel.class);

    private DuperModelManager manager;
    private SuperModelListener superModelListener;

    private void makeManager(boolean isController) {
        manager = new DuperModelManager(true, isController, superModelProvider, duperModel);

        when(superModelProvider.getSuperModel()).thenReturn(superModel);
        verify(duperModel, times(0)).add(any());

        ArgumentCaptor<SuperModelListener> superModelListenerCaptor = ArgumentCaptor.forClass(SuperModelListener.class);
        verify(superModelProvider, times(1)).registerListener(superModelListenerCaptor.capture());
        superModelListener = superModelListenerCaptor.getValue();
    }

    @Test
    public void testSuperModelAffectsDuperModel() {
        makeManager(false);

        verify(duperModel, times(0)).add(any());
        superModelListener.applicationActivated(superModel, mock(ApplicationInfo.class));
        verify(duperModel, times(1)).add(any());

        verify(duperModel, times(0)).remove(any());
        superModelListener.applicationRemoved(superModel, ApplicationId.from("tenant", "app", "default"));
        verify(duperModel, times(1)).remove(any());
    }

    @Test
    public void testInfraApplicationsAffectsDuperModel() {
        makeManager(false);

        ApplicationId id = proxyHostApplication.getApplicationId();
        List<DomainName> proxyHostHosts = Stream.of("proxyhost1", "proxyhost2").map(DomainName::of).toList();
        verify(duperModel, times(0)).add(any());
        manager.infraApplicationActivated(id, proxyHostHosts);
        verify(duperModel, times(1)).add(any());

        verify(duperModel, times(0)).remove(any());
        manager.infraApplicationRemoved(id);
        verify(duperModel, times(1)).remove(any());
    }

    @Test
    public void testEnabledConfigServerInfraApplications() {
        makeManager(false);
        testEnabledConfigServerLikeInfraApplication(
                configServerApplication.getApplicationId(),
                controllerApplication.getApplicationId());
    }

    @Test
    public void testEnabledControllerInfraApplications() {
        makeManager(true);
        testEnabledConfigServerLikeInfraApplication(
                controllerApplication.getApplicationId(),
                configServerApplication.getApplicationId());
    }

    private void testEnabledConfigServerLikeInfraApplication(ApplicationId firstId, ApplicationId secondId) {
        List<DomainName> hostnames1 = Stream.of("node11", "node12").map(DomainName::of).toList();
        manager.infraApplicationActivated(firstId, hostnames1);
        verify(duperModel, times(1)).add(any());

        // Adding the second config server like application will be ignored
        List<DomainName> hostnames2 = Stream.of("node21", "node22").map(DomainName::of).toList();
        assertThrows(IllegalArgumentException.class, () -> manager.infraApplicationActivated(secondId, hostnames2));
        verify(duperModel, times(1)).add(any());

        // Removing the second config server like application cannot be removed since it wasn't added
        verify(duperModel, times(0)).remove(any());
        assertThrows(IllegalArgumentException.class, () -> manager.infraApplicationRemoved(secondId));
        verify(duperModel, times(0)).remove(any());

        manager.infraApplicationRemoved(firstId);
        verify(duperModel, times(1)).remove(any());
    }

    private static void assertThrows(Class<? extends Throwable> clazz, Runnable runnable) {
        try {
            runnable.run();
            fail("Expected " + clazz);
        } catch (Throwable e) {
            if (!clazz.isInstance(e)) throw e;
        }
    }
}