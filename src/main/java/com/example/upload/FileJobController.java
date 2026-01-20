package com.example.upload;



import com.example.upload.FileJobService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jobs")
public class FileJobController {

    private final FileJobService jobService;

    public FileJobController(FileJobService jobService) {
        this.jobService = jobService;
    }


    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        return jobService.uploadFile(file);
    }
    // Start a new job
    // POST /api/jobs/start?fileName=huge-data.csv
    @PostMapping("/start")
    public String startJob(@RequestParam String fileName) {
        return jobService.submitJob(fileName);
    }

    // Pause a job
    // POST /api/jobs/{id}/pause
    @PostMapping("/{id}/pause")
    public String pauseJob(@PathVariable String id) {
        jobService.pauseJob(id);
        return "Job pause requested";
    }

    // Resume a job
    // POST /api/jobs/{id}/resume
    @PostMapping("/{id}/resume")
    public String resumeJob(@PathVariable String id) {
        jobService.resumeJob(id);
        return "Job resumed";
    }

    // Cancel a job
    // POST /api/jobs/{id}/cancel
    @PostMapping("/{id}/cancel")
    public String cancelJob(@PathVariable String id) {
        jobService.cancelJob(id);
        return "Job cancelled";
    }

    // Get Status
    // GET /api/jobs/{id}
    @GetMapping("/{id}")
    public String getStatus(@PathVariable String id) {
        return jobService.getJobStatus(id);
    }
}