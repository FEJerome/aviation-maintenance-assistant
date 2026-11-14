package cn.pandazi.aviation_maintenance_assistant.config;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Validates endpoint syntax and prevents accidental non-local connections in local-only mode.
 * This is a configuration guard, not an operating-system network sandbox.
 */
@Component
public class LocalOnlyEndpointGuard {

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final AddressResolver addressResolver;

    public LocalOnlyEndpointGuard() {
        this(InetAddress::getAllByName);
    }

    LocalOnlyEndpointGuard(AddressResolver addressResolver) {
        this.addressResolver = addressResolver;
    }

    public URI validateLlm(String baseUrl, AmaProperties.RuntimeMode runtimeMode) {
        return validate(baseUrl, runtimeMode, true);
    }

    public URI validateChroma(String baseUrl, AmaProperties.RuntimeMode runtimeMode) {
        return validate(baseUrl, runtimeMode, false);
    }

    private URI validate(String baseUrl, AmaProperties.RuntimeMode runtimeMode, boolean llm) {
        if (runtimeMode == null) {
            throw new IllegalArgumentException("Runtime mode must be configured");
        }

        URI uri = parseAndNormalize(baseUrl, llm ? "LLM" : "Chroma");
        InetAddress[] addresses = resolve(uri.getHost());
        boolean allLocal = true;
        for (InetAddress address : addresses) {
            if (!isAllowedLocalAddress(address)) {
                allLocal = false;
                break;
            }
        }

        if (runtimeMode == AmaProperties.RuntimeMode.LOCAL_ONLY && !allLocal) {
            throw new IllegalArgumentException((llm ? "LLM" : "Chroma")
                    + " endpoint resolves outside the allowed local address ranges");
        }

        if (llm
                && runtimeMode == AmaProperties.RuntimeMode.CLOUD_ALLOWED
                && "http".equalsIgnoreCase(uri.getScheme())
                && !allLocal) {
            throw new IllegalArgumentException("A non-local LLM endpoint must use HTTPS");
        }

        return uri;
    }

    private URI parseAndNormalize(String baseUrl, String endpointType) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(endpointType + " base URL must be configured");
        }

        String candidate = baseUrl.trim();
        while (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(endpointType + " base URL is invalid");
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(endpointType + " base URL must use HTTP or HTTPS");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(endpointType + " base URL must include a host");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    endpointType + " base URL must not contain user info, query, or fragment");
        }

        int port = uri.getPort();
        if (port == 0 || port > 65535) {
            throw new IllegalArgumentException(endpointType + " base URL contains an invalid port");
        }
        return uri;
    }

    private InetAddress[] resolve(String host) {
        InetAddress[] addresses;
        try {
            addresses = addressResolver.resolve(host);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Endpoint hostname could not be resolved");
        }
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("Endpoint hostname did not resolve to an address");
        }
        return addresses;
    }

    private boolean isAllowedLocalAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address.isLoopbackAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isPrivateIpv4(bytes, 0);
        }
        if (bytes.length == 16) {
            if (isIpv4Mapped(bytes)) {
                return isPrivateIpv4(bytes, 12);
            }
            return (bytes[0] & 0xfe) == 0xfc;
        }
        return false;
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private boolean isPrivateIpv4(byte[] bytes, int offset) {
        int first = bytes[offset] & 0xff;
        int second = bytes[offset + 1] & 0xff;
        return first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }
}
