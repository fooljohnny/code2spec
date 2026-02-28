package com.example.api.test1;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * test1 风格：ServiceComb + JAX-RS
 */
@RestSchema(schemaId = "FileDelegateImpl")
@Path("/rest/cbc/xxxservice/v1/csbproduct/file")
public class FileDelegateImpl {

    @Path("/download")
    @POST
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity<byte[]> downloadFile(HttpServletRequest httpRequest,
            @HeaderParam("X-Auth-Token") String token,
            @HeaderParam("x-operator-id") String operatorId,
            DownloadReq req) {
        return null;
    }

    @Path("/upload")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ResponseEntity<UploadResultRsp> uploadFile(HttpServletRequest httpRequest,
            @HeaderParam("X-Auth-Token") String token,
            UploadReq req) {
        return null;
    }
}
