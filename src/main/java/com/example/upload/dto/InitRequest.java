package com.example.upload.dto;

public class InitRequest {


    private String fileName;


    private long fileSize;


    private long chunkSize;


    private int totalChunks;

    // REQUIRED for Spring / Jackson
    public InitRequest() {
    }

    public InitRequest(String fileName, long fileSize, long chunkSize, int totalChunks) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.totalChunks = totalChunks;
    }

    // --- Getters ---
    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    // --- Setters ---
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setChunkSize(long chunkSize) {
        this.chunkSize = chunkSize;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }
}
