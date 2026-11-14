package cn.pandazi.aviation_maintenance_assistant.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalOnlyEndpointGuardTest {

    @Test
    void acceptsLoopbackPrivateAndUniqueLocalAddresses() {
        for (String address : List.of(
                "127.0.0.1", "::1", "10.20.30.40", "172.16.5.4",
                "172.31.255.254", "192.168.1.20", "fc00::1", "fd12::8")) {
            LocalOnlyEndpointGuard guard = guardResolvingTo(address);
            assertDoesNotThrow(() -> guard.validateLlm(
                    "http://service.test:8080/v1",
                    AmaProperties.RuntimeMode.LOCAL_ONLY));
        }
    }

    @Test
    void acceptsServiceNamesOnlyWhenEveryResolvedAddressIsLocal() {
        LocalOnlyEndpointGuard local = guardResolvingTo("192.168.50.10", "10.0.0.8");
        assertDoesNotThrow(() -> local.validateChroma(
                "http://chromadb:8000", AmaProperties.RuntimeMode.LOCAL_ONLY));

        LocalOnlyEndpointGuard mixed = guardResolvingTo("192.168.50.10", "203.0.113.10");
        assertThrows(IllegalArgumentException.class, () -> mixed.validateChroma(
                "http://chromadb:8000", AmaProperties.RuntimeMode.LOCAL_ONLY));
    }

    @Test
    void rejectsPublicUnspecifiedMulticastAndUnresolvedEndpoints() {
        for (String address : List.of(
                "203.0.113.10", "2001:4860::8888", "0.0.0.0", "::",
                "224.0.0.1", "ff02::1")) {
            LocalOnlyEndpointGuard guard = guardResolvingTo(address);
            assertThrows(IllegalArgumentException.class, () -> guard.validateLlm(
                    "https://models.example.test/v1",
                    AmaProperties.RuntimeMode.LOCAL_ONLY));
        }

        LocalOnlyEndpointGuard unresolved = new LocalOnlyEndpointGuard(host -> {
            throw new UnknownHostException("synthetic resolution failure");
        });
        assertThrows(IllegalArgumentException.class, () -> unresolved.validateLlm(
                "https://models.example.test/v1", AmaProperties.RuntimeMode.LOCAL_ONLY));
    }

    @Test
    void explicitlyRejectsLinkLocalAddresses() {
        for (String address : List.of("169.254.10.20", "fe80::1")) {
            LocalOnlyEndpointGuard guard = guardResolvingTo(address);
            assertThrows(IllegalArgumentException.class, () -> guard.validateLlm(
                    "http://service.test:8080", AmaProperties.RuntimeMode.LOCAL_ONLY));
        }
    }

    @Test
    void localhostDoesNotBypassAddressValidation() {
        LocalOnlyEndpointGuard guard = guardResolvingTo("203.0.113.10");

        assertThrows(IllegalArgumentException.class, () -> guard.validateLlm(
                "http://localhost:8080", AmaProperties.RuntimeMode.LOCAL_ONLY));
    }

    @Test
    void cloudModeRequiresHttpsForPublicLlmButAllowsLocalHttp() {
        LocalOnlyEndpointGuard publicGuard = guardResolvingTo("203.0.113.10");
        assertDoesNotThrow(() -> publicGuard.validateLlm(
                "https://models.example.test/v1", AmaProperties.RuntimeMode.CLOUD_ALLOWED));
        assertThrows(IllegalArgumentException.class, () -> publicGuard.validateLlm(
                "http://models.example.test/v1", AmaProperties.RuntimeMode.CLOUD_ALLOWED));
        assertDoesNotThrow(() -> publicGuard.validateChroma(
                "http://vectors.example.test", AmaProperties.RuntimeMode.CLOUD_ALLOWED));

        LocalOnlyEndpointGuard localGuard = guardResolvingTo("127.0.0.1");
        assertDoesNotThrow(() -> localGuard.validateLlm(
                "http://localhost:11434/v1", AmaProperties.RuntimeMode.CLOUD_ALLOWED));
    }

    @Test
    void removesOnlyTrailingSlashesAndPreservesTheConfiguredPath() {
        URI result = guardResolvingTo("127.0.0.1").validateLlm(
                "http://localhost:11434/root/v1///",
                AmaProperties.RuntimeMode.CLOUD_ALLOWED);

        assertEquals("http://localhost:11434/root/v1", result.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "service.test/v1",
            "ftp://service.test/v1",
            "https://user:credential@service.test/v1",
            "https://service.test/v1?credential=value",
            "https://service.test/v1#fragment",
            "http://service.test:99999/v1"
    })
    void rejectsAmbiguousOrCredentialBearingUrls(String baseUrl) {
        LocalOnlyEndpointGuard guard = guardResolvingTo("127.0.0.1");

        assertThrows(IllegalArgumentException.class, () -> guard.validateLlm(
                baseUrl, AmaProperties.RuntimeMode.CLOUD_ALLOWED));
    }

    private LocalOnlyEndpointGuard guardResolvingTo(String... addresses) {
        return new LocalOnlyEndpointGuard(host -> {
            InetAddress[] resolved = new InetAddress[addresses.length];
            for (int index = 0; index < addresses.length; index++) {
                resolved[index] = InetAddress.getByName(addresses[index]);
            }
            return resolved;
        });
    }
}
