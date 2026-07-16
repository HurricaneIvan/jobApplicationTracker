package com.tracker.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 wiring. When s3.endpoint is set (LocalStack) we override the endpoint and force
 * path-style access. Leaving it blank targets real AWS. Credentials come from the
 * standard AWS env vars (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY).
 */
@Configuration
public class S3Config {

    @Value("${s3.endpoint:}")
    private String endpoint;

    @Value("${s3.region}")
    private String region;

    private boolean hasEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(hasEndpoint())
                        .build());
        if (hasEndpoint()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(hasEndpoint())
                        .build());
        if (hasEndpoint()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
