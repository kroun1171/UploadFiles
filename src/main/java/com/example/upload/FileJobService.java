package com.example.upload;

import com.example.upload.dto.InitRequest;
import com.example.upload.dto.InitResponse;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLOutput;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileJobService {

    private static final Logger log = LoggerFactory.getLogger(FileJobService.class);

    private final S3Client s3Client;
    private final S3TransferManager transferManager;
    private final Map<String, JobContext> completedJobs = new ConcurrentHashMap<>();

    @Value("${backblaze.bucket}")
    private String bucketName;

    private static final Path BASE_DIR =
            Paths.get(System.getProperty("java.io.tmpdir"), "uploads");

    private final Map<String, JobContext> activeJobs = new ConcurrentHashMap<>();

    public FileJobService(S3Client s3Client, S3TransferManager transferManager) {
        this.s3Client = s3Client;
        this.transferManager = transferManager;
    }

    /* ================= INIT ================= */
    public InitResponse initUpload(InitRequest req) {
        String jobId = UUID.randomUUID().toString();
        try {
            Files.createDirectories(BASE_DIR.resolve(jobId));
            activeJobs.put(jobId,
                    new JobContext(jobId, req.getFileName(), req.getTotalChunks()));
            return new InitResponse(jobId, "UPLOADING", Instant.now().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* ================= CHUNK UPLOAD ================= */
    public void uploadChunk(String jobId, int chunkIndex, MultipartFile file) {
        JobContext ctx = getContext(jobId);

        waitIfPaused(ctx);
        if (ctx.cancelled) throw new RuntimeException("Cancelled");

        try {
            Path chunkPath = BASE_DIR.resolve(jobId)
                    .resolve("chunk_" + chunkIndex + ".part");
            System.out.println(chunkPath.toString());
            Files.copy(file.getInputStream(), chunkPath,
                    StandardCopyOption.REPLACE_EXISTING);

            ctx.receivedChunks.add(chunkIndex);
            log.info("Chunk {} uploaded for {}", chunkIndex, jobId);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* ================= COMPLETE ================= */
    public void completeUpload(String jobId) {
        JobContext ctx = getContext(jobId);

        if (!ctx.allChunksReceived()) {
            throw new RuntimeException("Missing chunks");
        }

        try {
            Path jobDir = BASE_DIR.resolve(jobId);
            Path merged = jobDir.resolve(ctx.fileName);

            try (OutputStream os = Files.newOutputStream(merged)) {
                for (int i = 0; i < ctx.totalChunks; i++) {
                    waitIfPaused(ctx);
                    if (ctx.cancelled) {
                        ctx.status = "CANCELLED";
                        return;
                    }
                    Files.copy(jobDir.resolve("chunk_" + i + ".part"), os);
                }
            }

            uploadToS3(merged, ctx.fileName);
            ctx.status = "UPLOADED";

            processFileAsync(ctx); // async starts here

        } catch (Exception e) {
            ctx.status = "FAILED";
            throw new RuntimeException(e);
        }
    }

    /* ================= S3 ================= */
    private void uploadToS3(Path file, String key) {
        UploadFileRequest req = UploadFileRequest.builder()
                .putObjectRequest(b -> b.bucket(bucketName).key(key))
                .source(file)
                .build();

        transferManager.uploadFile(req).completionFuture().join();
        log.info("S3 upload done: {}", key);
    }

    /* ================= CONTROL ================= */
    public void pauseJob(String jobId) {
        getContext(jobId).paused = true;
    }

    public void resumeJob(String jobId) {
        JobContext ctx = getContext(jobId);
        synchronized (ctx) {
            ctx.paused = false;
            ctx.notifyAll();
        }
    }

    public void cancelJob(String jobId) {
        JobContext ctx = getContext(jobId);
        ctx.cancelled = true;
        resumeJob(jobId);
    }

    public String getJobStatus(String jobId) {
        return getContext(jobId).status;
    }

    /* ================= ASYNC PROCESSING ================= */
    @Async
    public void processFileAsync(JobContext ctx) {
        ctx.status = "RUNNING";

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(ctx.fileName)
                    .build();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(s3Client.getObject(request)))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    waitIfPaused(ctx);
                    if (ctx.cancelled) {
                        ctx.status = "CANCELLED";
                        return;
                    }
                }
            }

            ctx.status = "COMPLETED";

        } catch (Exception e) {
            ctx.status = "FAILED";
        } finally {

                activeJobs.remove(ctx.jobId);
                completedJobs.put(ctx.jobId, ctx);

        }
    }



    /* ================= UTIL ================= */
    private void waitIfPaused(JobContext ctx) {
        synchronized (ctx) {
            while (ctx.paused) {
                try {
                    ctx.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted");
                }
            }
        }
    }

    private JobContext getContext(String jobId) {
        JobContext ctx = activeJobs.get(jobId);
        if (ctx == null) throw new RuntimeException("Job not found");
        return ctx;
    }

    /* ================= CONTEXT ================= */
    static class JobContext {
        final String jobId;
        final String fileName;
        final int totalChunks;
        final Set<Integer> receivedChunks = ConcurrentHashMap.newKeySet();

        volatile boolean paused;
        volatile boolean cancelled;
        volatile String status = "UPLOADING";

        JobContext(String jobId, String fileName, int totalChunks) {
            this.jobId = jobId;
            this.fileName = fileName;
            this.totalChunks = totalChunks;
        }

        boolean allChunksReceived() {
            return receivedChunks.size() == totalChunks;
        }
    }
}
