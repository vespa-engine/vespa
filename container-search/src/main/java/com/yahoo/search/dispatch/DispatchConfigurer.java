package com.yahoo.search.dispatch;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.vespa.config.search.DispatchNodesConfig;

import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class DispatchConfigurer extends AbstractComponent {

    Map<ComponentId, DispatchNodesConfig> configById = new HashMap<>();

    @Inject
    public DispatchConfigurer(ComponentRegistry<Dispatcher> dispatchers, ComponentRegistry<DispatchNodesConfigHolder> configs) {
        Map<ComponentId, DispatchNodesConfig> configById = configs.allComponentsById().entrySet().stream()
                                                             .collect(toMap(entry -> new ComponentId(entry.getKey().getName().replace("-config", "")),
                                                                            entry -> entry.getValue().config()));
        // Throw out all unchanged config, then update dispatchers and out reference map with the updated entries.
        configById.keySet().removeIf(id -> configById.get(id).equals(this.configById.get(id)));
        configById.forEach((id, config) -> dispatchers.getComponent(id).updateWithNewConfig(config));
        this.configById.putAll(configById);
    }

}
