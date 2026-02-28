package com.example.jaxrs;

/**
 * 文件上传结果
 */
public class UploadResultRsp {
    private String fileId;
    private String url;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
