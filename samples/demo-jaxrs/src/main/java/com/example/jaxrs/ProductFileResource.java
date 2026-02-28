package com.example.jaxrs;

import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

/**
 * 文件资源 JAX-RS 风格接口（与 test1 代码风格一致）.
 * 类上 @Path + @RestSchema，方法上 @Path + @GET/@POST，参数 @HeaderParam / @QueryParam / 无注解 body.
 */
@RestSchema(schemaId = "ProductFileResource")
@Path("/rest/sample/demo-jaxrs/v1/file")
public class ProductFileResource {

    /**
     * 下载文件
     *
     * @param httpRequest 请求
     * @param token       X-Auth-Token
     * @param operatorId  x-operator-id
     * @param req         请求体
     * @return 文件流
     */
    @Path("/download")
    @POST
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity<InputStream> downloadFile(HttpServletRequest httpRequest,
                                                      @HeaderParam("X-Auth-Token") String token,
                                                      @HeaderParam("x-operator-id") String operatorId,
                                                      DownloadReq req) {
        return ResponseEntity.ok(null);
    }

    /**
     * 上传文件
     *
     * @param token       X-Auth-Token
     * @param operatorId  x-operator-id
     * @return 上传结果
     */
    @Path("/upload")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ResponseEntity<UploadResultRsp> uploadFile(@HeaderParam("X-Auth-Token") String token,
                                                      @HeaderParam("x-operator-id") String operatorId) {
        return ResponseEntity.ok(new UploadResultRsp());
    }

    /**
     * 根据 id 查询文件信息（GET + PathParam + QueryParam）
     *
     * @param id   文件 id
     * @param lang 语言，query
     * @return 文件信息
     */
    @Path("/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseEntity<UploadResultRsp> getFileInfo(@PathParam("id") String id,
                                                      @QueryParam("lang") String lang) {
        return ResponseEntity.ok(new UploadResultRsp());
    }
}
