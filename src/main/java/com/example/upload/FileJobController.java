package com.example.upload;



import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.upload.dto.InitRequest;
import com.example.upload.dto.InitResponse;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class FileJobController {

    private final FileJobService fileJobService;

    public FileJobController(FileJobService fileJobService) {
        this.fileJobService = fileJobService;
    }


    @PostMapping(
            value = "/init",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<InitResponse> initUpload(
             @RequestBody InitRequest request) {

        InitResponse response = fileJobService.initUpload(request);
        return ResponseEntity.ok(response);
    }

  
    @PostMapping("/chunk")
    public ResponseEntity<Void> uploadChunk(
            @RequestParam("jobId") String jobId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("file") MultipartFile file) {

        fileJobService.uploadChunk(jobId, chunkIndex, file);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> completeUpload(
            @RequestParam("jobId") String jobId) {

        fileJobService.completeUpload(jobId);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/{id}")
    public ResponseEntity<String> getJobStatus(
            @PathVariable("id") String jobId) {

        return ResponseEntity.ok(fileJobService.getJobStatus(jobId));
    }


    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pauseJob(
            @PathVariable("id") String jobId) {

        fileJobService.pauseJob(jobId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resumeJob(
            @PathVariable("id") String jobId) {

        fileJobService.resumeJob(jobId);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelJob(
            @PathVariable("id") String jobId) {

        fileJobService.cancelJob(jobId);
        return ResponseEntity.ok().build();
    }
}