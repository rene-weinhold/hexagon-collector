package com.weinhold.hexagon;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import com.weinhold.hexagon.model.HexagonDescriptor;

/**
 * Actuator endpoint exposed at {@code /actuator/hexagon}. It serves the pre-computed
 * {@link HexagonDescriptor} describing the service's ports and adapters.
 * <p>Expose it like any other actuator endpoint, e.g.:
 * 
 * <pre>
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health,hexagon
 * </pre>
 */
@Endpoint(id = "hexagon")
public class HexagonEndpoint {

    private final HexagonDescriptorFactory descriptorFactory;

    public HexagonEndpoint(HexagonDescriptorFactory descriptorFactory) {
        this.descriptorFactory = descriptorFactory;
    }

    @ReadOperation
    public HexagonDescriptor hexagon() {
        // The factory computes the descriptor once and caches it; this is a lookup thereafter.
        return this.descriptorFactory.get();
    }

}
