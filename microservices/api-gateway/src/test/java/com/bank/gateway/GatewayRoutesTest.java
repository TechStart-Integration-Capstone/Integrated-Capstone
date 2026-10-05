package com.bank.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the route table in application.yml without starting the gateway. */
class GatewayRoutesTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> routes() throws Exception {
        try (InputStream in = GatewayRoutesTest.class.getResourceAsStream("/application.yml")) {
            Map<String, Object> yml = new Yaml().load(in);
            Map<String, Object> gateway = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) yml.get("spring")).get("cloud")).get("gateway");
            return (List<Map<String, Object>>) gateway.get("routes");
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean routed(String path) throws Exception {
        AntPathMatcher matcher = new AntPathMatcher();
        return routes().stream()
                .flatMap(r -> ((List<String>) r.get("predicates")).stream())
                .filter(p -> p.startsWith("Path="))
                .flatMap(p -> List.of(p.substring(5).split(",")).stream())
                .anyMatch(pattern -> matcher.match(pattern.trim(), path));
    }

    @Test
    @DisplayName("TC-GW-01: the internal transfer endpoint has no gateway route (→ 404)")
    void internalTransferEndpoint_isNotRouted() throws Exception {
        assertThat(routed("/internal/remittance/transfer")).isFalse();
        assertThat(routed("/api/v1/internal/remittance/transfer")).isFalse();
    }

    @Test
    @DisplayName("/api/v1/loans/** is routed to loan-service:8091 with /api/v1 stripped")
    @SuppressWarnings("unchecked")
    void loansRoute_stripsApiV1() throws Exception {
        Map<String, Object> loans = routes().stream().filter(r -> "loan-service".equals(r.get("id"))).findFirst().orElseThrow();

        assertThat((String) loans.get("uri")).endsWith(":8091");
        assertThat((List<Object>) loans.get("predicates")).containsExactly("Path=/api/v1/loans/**");
        assertThat((List<Object>) loans.get("filters")).contains("StripPrefix=2");
        assertThat(routed("/api/v1/loans/applications")).isTrue();
    }
}
