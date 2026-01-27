package com.example.upload.dto;



import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;



public class InitResponse {

    private String jobId;
    private String status;
    private String createdAt;

    public InitResponse() {}

    public InitResponse(String jobId, String status, String createdAt) {
        this.jobId = jobId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getJobId() {
        return jobId;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}


