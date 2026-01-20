package com.example.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileJobService {

    private static final Logger log = LoggerFactory.getLogger(FileJobService.class);

    private final S3Client s3Client;
    private final S3TransferManager transferManager; // Inject this!

    @Value("${backblaze.bucket}")
    private String bucketName;

    private final Map<String, JobContext> activeJobs = new ConcurrentHashMap<>();

    // Constructor Injection
    public FileJobService(S3Client s3Client, S3TransferManager transferManager) {
        this.s3Client = s3Client;
        this.transferManager = transferManager;
    }

    // --- Upload Method (Parallel Chunking) ---
    public String uploadFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        File tempFile = null;

        try {
            log.info("Starting Multipart upload for: {}", fileName);

            // Convert MultipartFile to temporary File for the TransferManager
            tempFile = File.createTempFile("upload-", fileName);
            file.transferTo(tempFile);

            UploadFileRequest uploadFileRequest = UploadFileRequest.builder()
                    .putObjectRequest(req -> req.bucket(bucketName).key(fileName))
                    .source(tempFile)
                    .build();

            FileUpload upload = transferManager.uploadFile(uploadFileRequest);
            CompletedFileUpload outcome = upload.completionFuture().join(); // Wait for finish

            log.info("Upload successful! ETag: {}", outcome.response().eTag());
            return fileName;

        } catch (Exception e) {
            log.error("Upload failed", e);
            throw new RuntimeException("Failed to upload file", e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete(); // Cleanup
            }
        }
    }

    // --- Control Methods ---
    public String submitJob(String fileName) {
        String jobId = UUID.randomUUID().toString();
        JobContext context = new JobContext(jobId, fileName);
        activeJobs.put(jobId, context);
        processFileAsync(context);
        return jobId;
    }

    public void pauseJob(String jobId) {
        JobContext ctx = activeJobs.get(jobId);
        if (ctx != null) ctx.setPaused(true);
    }

    public void resumeJob(String jobId) {
        JobContext ctx = activeJobs.get(jobId);
        if (ctx != null) {
            ctx.setPaused(false);
            synchronized (ctx) {
                ctx.notifyAll();
            }
        }
    }

    public void cancelJob(String jobId) {
        JobContext ctx = activeJobs.get(jobId);
        if (ctx != null) {
            ctx.setCancelled(true);
            resumeJob(jobId);
        }
    }

    public String getJobStatus(String jobId) {
        JobContext ctx = activeJobs.get(jobId);
        return ctx != null ? ctx.getStatus() : "NOT_FOUND";
    }

    // --- Processing Logic (Async) ---
    @Async
    protected void processFileAsync(JobContext ctx) {
        log.info("Starting job: {}", ctx.jobId);
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(ctx.fileName)
                    .build();

            // Using standard Client here for line-by-line reading
            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(request);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(s3Stream, StandardCharsets.UTF_8))) {

                String line;
                long lineCount = 0;

                while ((line = reader.readLine()) != null) {
                    if (ctx.isCancelled()) {
                        log.warn("Job {} cancelled by user.", ctx.jobId);
                        ctx.setStatus("CANCELLED");
                        return;
                    }
                    synchronized (ctx) {
                        while (ctx.isPaused()) {
                            ctx.setStatus("PAUSED");
                            log.info("Job {} is paused. Waiting...", ctx.jobId);
                            ctx.wait();
                        }
                    }
                    ctx.setStatus("RUNNING");
                    processLineWithRetry(line, ctx, 3);
                    lineCount++;
                    if (lineCount % 1000 == 0) {
                        log.info("Job {} processed {} lines...", ctx.jobId, lineCount);
                    }
                }
            }
            ctx.setStatus("COMPLETED");
            log.info("Job {} finished successfully.", ctx.jobId);

        } catch (Exception e) {
            log.error("Job failed", e);
            ctx.setStatus("FAILED: " + e.getMessage());
        }
    }

    private void processLineWithRetry(String line, JobContext ctx, int maxRetries) {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                if (line.contains("error")) throw new RuntimeException("Simulated data error");
                return;
            } catch (Exception e) {
                attempt++;
                log.warn("Error processing line in Job {}, attempt {}/{}", ctx.jobId, attempt, maxRetries);
                if (attempt >= maxRetries) log.error("Failed to process line: {}", line);
            }
        }
    }

    private static class JobContext {
        final String jobId;
        final String fileName;
        private volatile boolean paused = false;
        private volatile boolean cancelled = false;
        private volatile String status = "STARTING";

        public JobContext(String jobId, String fileName) {
            this.jobId = jobId;
            this.fileName = fileName;
        }

        public boolean isPaused() { return paused; }
        public void setPaused(boolean paused) { this.paused = paused; }
        public boolean isCancelled() { return cancelled; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}