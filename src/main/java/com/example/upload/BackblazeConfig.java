package com.example.upload;



import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URI;

@Configuration
public class BackblazeConfig {

    @Value("${backblaze.endpoint}")
    private String endpoint;

    @Value("${backblaze.region}")
    private String region;

    @Value("${backblaze.access-key}")
    private String accessKey;

    @Value("${backblaze.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    // 2. CRT Async Client (High Performance for Uploads)
    @Bean
    public S3AsyncClient s3AsyncClient() {
        // USE crtBuilder() HERE!
        return S3AsyncClient.crtBuilder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                // .multipartEnabled(true) <-- REMOVED (Not needed, CRT does this automatically)
                .build();
    }

    // 3. Transfer Manager
    @Bean
    public S3TransferManager transferManager(S3AsyncClient s3AsyncClient) {
        return S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();
    }
}
