package com.prathamesh.jbsolar.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.oracle")
public class ObjectStorageProperties {
    private String endpoint = "";
    private String namespace = "";
    private String bucket = "";
    private String accessKeyId = "";
    private String secretAccessKey = "";
    private String region = "";
    private long maxBytes = 20_000_000_000L;

    public String getEndpoint() {
        if (hasText(endpoint)) {
            return endpoint;
        }
        if (hasText(namespace) && hasText(region)) {
            return "https://" + namespace + ".compat.objectstorage." + region + ".oci.customer-oci.com";
        }
        return "";
    }

    public String getEndpointHost() {
        try {
            return URI.create(getEndpoint()).getHost();
        } catch (IllegalArgumentException exception) {
            return "invalid-endpoint";
        }
    }

    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }

    public boolean isConfigured() {
        if (!hasText(getEndpoint()) || !hasText(bucket) || !hasText(accessKeyId)
                || !hasText(secretAccessKey) || !hasText(region) || maxBytes < 1) {
            return false;
        }
        try {
            URI endpointUri = URI.create(getEndpoint());
            String path = endpointUri.getPath();
            return "https".equalsIgnoreCase(endpointUri.getScheme())
                    && endpointUri.getHost() != null && endpointUri.getUserInfo() == null
                    && (path == null || path.isEmpty() || path.equals("/"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
