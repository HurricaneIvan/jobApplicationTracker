package com.tracker.application.document;

import com.tracker.application.dto.ApplicationDtos.PresignedGetResponse;
import com.tracker.application.dto.ApplicationDtos.PresignedPutResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * Owns all S3 interaction for application-copy documents. This is the ONLY class that
 * touches S3 — it is packaged separately (com.tracker.application.document) so it can be
 * lifted into a standalone document-service later without disturbing the domain code.
 */
@Service
public class DocumentService {

    private final S3Presigner presigner;
    private final String bucket;
    private final long ttlSeconds;

    public DocumentService(S3Presigner presigner,
                           @Value("${s3.bucket}") String bucket,
                           @Value("${s3.presign-ttl-seconds}") long ttlSeconds) {
        this.presigner = presigner;
        this.bucket = bucket;
        this.ttlSeconds = ttlSeconds;
    }

    /** Deterministic key layout: applications/{userId}/{applicationId}/copy. */
    public String buildKey(String userId, String applicationId) {
        return "applications/" + userId + "/" + applicationId + "/copy";
    }

    public PresignedPutResponse presignPut(String userId, String applicationId, String contentType) {
        String key = buildKey(userId, applicationId);
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(ttlSeconds))
                .putObjectRequest(put)
                .build();
        String url = presigner.presignPutObject(presignRequest).url().toString();
        return new PresignedPutResponse(url, key, ttlSeconds);
    }

    public PresignedGetResponse presignGet(String s3Key) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(ttlSeconds))
                .getObjectRequest(get)
                .build();
        String url = presigner.presignGetObject(presignRequest).url().toString();
        return new PresignedGetResponse(url, s3Key, ttlSeconds);
    }
}
