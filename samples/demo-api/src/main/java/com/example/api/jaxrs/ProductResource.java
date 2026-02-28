package com.example.api.jaxrs;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

/**
 * JAX-RS 商品资源
 */
@Path("/api/v2/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    /**
     * 获取商品列表
     */
    @GET
    public String listProducts(@QueryParam("page") int page, @QueryParam("size") int size) {
        return "[]";
    }

    /**
     * 根据ID获取商品
     */
    @GET
    @Path("/{id}")
    public String getProduct(@PathParam("id") String id) {
        return "{}";
    }

    /**
     * 创建商品
     */
    @POST
    public String createProduct(CreateProductRequest request) {
        return "{}";
    }

    /**
     * 更新商品
     */
    @PUT
    @Path("/{id}")
    public String updateProduct(@PathParam("id") String id, UpdateProductRequest request) {
        return "{}";
    }

    /**
     * 删除商品
     */
    @DELETE
    @Path("/{id}")
    public void deleteProduct(@PathParam("id") String id) {
    }
}
