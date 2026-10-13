package com.example.notification.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.net.*;
import java.util.Set;

/**
 * Server-Side Request Forgery (SSRF) protection for outbound webhook URLs.
 *
 * Blocks:
 *  - Non-HTTP/HTTPS schemes
 *  - Loopback addresses (127.x.x.x, ::1)
 *  - RFC 1918 private ranges (10.x, 172.16-31.x, 192.168.x)
 *  - Link-local addresses (169.254.x, fe80::)
 *  - AWS/GCP/Azure metadata endpoints (169.254.169.254, etc.)
 *  - Multicast addresses
 *
 * In dev/test mode (allowLocalhost=true) loopback is permitted for mock receivers.
 */
@UtilityClass
@Slf4j
public class UrlSafetyChecker {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "169.254.169.254",   // AWS instance metadata
            "metadata.google.internal",
            "169.254.170.2"      // ECS task metadata
    );

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /**
     * Validates that a URL is safe to use as a webhook destination.
     *
     * @param urlString      the destination URL
     * @param allowLocalhost if true, loopback addresses are permitted (dev only)
     * @throws IllegalArgumentException with a safe message if the URL is unsafe
     */
    public void validate(String urlString, boolean allowLocalhost) {
        if (urlString == null || urlString.isBlank()) {
            throw new IllegalArgumentException("Destination URL is required");
        }
        URI uri;
        try {
            uri = new URI(urlString).normalize();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException("Only HTTP and HTTPS destinations are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must contain a valid hostname");
        }

        // Block known metadata endpoints
        if (BLOCKED_HOSTS.contains(host.toLowerCase())) {
            throw new IllegalArgumentException("Destination host is not allowed");
        }

        // Resolve DNS and check the resolved address
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                checkAddress(addr, allowLocalhost, host);
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Could not resolve destination hostname");
        }
    }

    private void checkAddress(InetAddress addr, boolean allowLocalhost, String host) {
        if (addr.isLoopbackAddress()) {
            if (!allowLocalhost) {
                throw new IllegalArgumentException("Loopback destinations are not allowed in this environment");
            }
            return; // dev mode: allow
        }

        if (addr.isSiteLocalAddress()) {
            throw new IllegalArgumentException("Private network destinations are not allowed");
        }

        if (addr.isLinkLocalAddress()) {
            throw new IllegalArgumentException("Link-local destinations are not allowed");
        }

        if (addr.isMulticastAddress()) {
            throw new IllegalArgumentException("Multicast destinations are not allowed");
        }

        if (addr.isAnyLocalAddress()) {
            throw new IllegalArgumentException("Wildcard address destinations are not allowed");
        }

        log.debug("URL safety check passed for host: {}, resolved: {}", host, addr.getHostAddress());
    }
}
