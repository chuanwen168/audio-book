package com.chuanwen.audiobookclien.data

import retrofit2.http.GET
import retrofit2.http.Path

/** 服务端 HTTP 接口定义（对应 audiobook-server 的 API 路由） */
interface ApiService {

    /** 专辑列表 */
    @GET("api/v1/albums")
    suspend fun albums(): ApiResult<List<AlbumSimple>>

    /** 专辑详情（含分集音频列表） */
    @GET("api/v1/album/{folder}")
    suspend fun album(@Path("folder") folder: String): ApiResult<AlbumFull>
}
