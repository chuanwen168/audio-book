package com.chuanwen.audiobookclien.data

/** 服务端统一返回结构：{ code, msg, data } */
data class ApiResult<T>(
    val code: Int,
    val msg: String?,
    val data: T?
)

/** 专辑元信息（标题 / 封面 / 简介） */
data class AlbumNfo(
    val title: String,
    val cover: String,
    val desc: String
)

/** 专辑列表项 */
data class AlbumSimple(
    val folder_name: String,
    val nfo: AlbumNfo
)

/** 单集音频 */
data class AudioItem(
    val file_name: String,
    val file_url: String
)

/** 专辑详情（含分集列表） */
data class AlbumFull(
    val folder_name: String,
    val nfo: AlbumNfo,
    val audios: List<AudioItem>
)
