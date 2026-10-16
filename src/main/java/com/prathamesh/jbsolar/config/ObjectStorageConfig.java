package com.prathamesh.jbsolar.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(NeonStorageProperties.class)
public class ObjectStorageConfig {
    @Bean
    S3Client s3Client(NeonStorageProperties properties) {
        var builder = S3Client.builder()
                .region(region(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(credentialsProvider(properties));
        if (!properties.getEndpoint().isBlank()) {
            builder.endpointOverride(java.net.URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(NeonStorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(region(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(credentialsProvider(properties));
        if (!properties.getEndpoint().isBlank()) {
            builder.endpointOverride(java.net.URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentialsProvider(
            NeonStorageProperties properties) {
        if (!properties.getAccessKeyId().isBlank() && !properties.getSecretAccessKey().isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.getAccessKeyId(), properties.getSecretAccessKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    private Region region(NeonStorageProperties properties) {
        return Region.of(properties.getRegion().isBlank() ? "us-east-1" : properties.getRegion());
    }
}
