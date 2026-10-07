package com.prathamesh.jbsolar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.neon")
public class NeonStorageProperties {
    private String endpoint = "";
    private String bucket = "";
    private String accessKeyId = "";
    private String secretAccessKey = "";
    private String region = "";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public boolean isConfigured() {
        if (!hasText(endpoint) || !hasText(bucket) || !hasText(accessKeyId)
                || !hasText(secretAccessKey) || !hasText(region)) {
            return false;
        }
        try {
            var endpointUri = java.net.URI.create(endpoint);
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
