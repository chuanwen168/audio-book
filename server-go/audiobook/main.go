package main

import (
	"encoding/json"
	"fmt"
	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

var (
	mediaRoot    string
	listenAddr   string
	defaultCover = "/media/nocover.jpg"
)

type RawNfo struct {
	Title       string `json:"title"`
	Author      string `json:"author"`
	Narrator    string `json:"narrator"`
	Description string `json:"description"`
	Category    string `json:"category"`
	Year        string `json:"year"`
}

type AlbumNfo struct {
	Title string `json:"title"`
	Cover string `json:"cover"`
	Desc  string `json:"desc"`
}

type AlbumSimple struct {
	FolderName string   `json:"folder_name"`
	Nfo        AlbumNfo `json:"nfo"`
}

type AudioItem struct {
	FileName string `json:"file_name"`
	FileUrl  string `json:"file_url"`
}

type AlbumFull struct {
	FolderName string      `json:"folder_name"`
	Nfo        AlbumNfo    `json:"nfo"`
	Audios     []AudioItem `json:"audios"`
}

type ApiResult struct {
	Code int         `json:"code"`
	Msg  string      `json:"msg"`
	Data interface{} `json:"data"`
}

func main() {
	// 加载.env环境变量
	_ = godotenv.Load()
	mediaRoot = os.Getenv("MEDIA_ROOT")
	listenAddr = os.Getenv("LISTEN_ADDR")

	// 兜底默认值
	if mediaRoot == "" {
		mediaRoot = "D:/audiobook"
	}
	if listenAddr == "" {
		listenAddr = "0.0.0.0:8086"
	}

	r := gin.Default()

	// 静态资源目录
	r.Static("/static", "./static")
	// 网站首页路由：访问根地址展示index.html
	r.GET("/", func(c *gin.Context) {
		c.File("./static/index.html")
	})

	// 媒体文件静态访问
	r.Static("/media", mediaRoot)
	// 管理面板页面
	r.StaticFile("/admin", "./static/admin.html")
	r.StaticFile("/detail.html", "./static/detail.html")

	// 业务接口
	r.GET("/api/v1/albums", listAlbumSimple)
	r.GET("/api/v1/album/:folder", getAlbumFull)
	r.POST("/api/v1/edit_info/:folder", editAlbumInfo)
	r.POST("/api/v1/edit_title/:folder", editAlbumTitle)
	r.GET("/api/v1/album_nfo/:folder", getAlbumFullNfo)
	r.POST("/api/v1/edit_all/:folder", editAlbumAllInfo)
	r.POST("/api/v1/upload_cover/:folder", uploadCoverImage)

	_ = r.Run(listenAddr)
}

func hasMp3(dir string) bool {
	files, err := os.ReadDir(dir)
	if err != nil {
		return false
	}
	for _, f := range files {
		if f.IsDir() {
			continue
		}
		name := strings.ToLower(f.Name())
		if strings.HasSuffix(name, ".mp3") {
			return true
		}
	}
	return false
}

func coverExists(dir string) bool {
	path := filepath.Join(dir, "cover.jpg")
	_, err := os.Stat(path)
	return err == nil
}

// naturalLess 按自然顺序比较两个字符串：连续数字片段按数值比较，
// 其余字符按字典序比较。例如 "02" < "10" < "100"，"第2集" < "第10集"。
func naturalLess(a, b string) bool {
	i, j := 0, 0
	for i < len(a) && j < len(b) {
		ca, cb := a[i], b[j]
		da := ca >= '0' && ca <= '9'
		db := cb >= '0' && cb <= '9'
		if da && db {
			startA, startB := i, j
			for i < len(a) && a[i] >= '0' && a[i] <= '9' {
				i++
			}
			for j < len(b) && b[j] >= '0' && b[j] <= '9' {
				j++
			}
			numA, numB := a[startA:i], b[startB:j]
			trimA := strings.TrimLeft(numA, "0")
			trimB := strings.TrimLeft(numB, "0")
			if trimA == "" {
				trimA = "0"
			}
			if trimB == "" {
				trimB = "0"
			}
			if len(trimA) != len(trimB) {
				return len(trimA) < len(trimB)
			}
			if trimA != trimB {
				return trimA < trimB
			}
			// 数值相等时前导零少的排前面（"1" 在 "01" 前）
			if len(numA) != len(numB) {
				return len(numA) < len(numB)
			}
			continue
		}
		if ca != cb {
			return ca < cb
		}
		i++
		j++
	}
	return len(a)-i < len(b)-j
}

// paginationParams 解析分页参数 page / page_size。
// 两个参数都没传时返回 enabled=false，保持原有“一次性返回全部”的行为（兼容安卓客户端）。
func paginationParams(c *gin.Context) (page, pageSize int, enabled bool) {
	p := c.Query("page")
	s := c.Query("page_size")
	if p == "" && s == "" {
		return 0, 0, false
	}
	page = 1
	if v, err := strconv.Atoi(p); err == nil && v >= 1 {
		page = v
	}
	pageSize = 12
	if v, err := strconv.Atoi(s); err == nil && v >= 1 {
		pageSize = v
		if pageSize > 200 {
			pageSize = 200
		}
	}
	return page, pageSize, true
}

func listAlbumSimple(c *gin.Context) {
	dirs, err := os.ReadDir(mediaRoot)
	if err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 500, Msg: "读取媒体目录失败"})
		return
	}
	var list []AlbumSimple
	for _, d := range dirs {
		if !d.IsDir() {
			continue
		}
		folder := d.Name()
		fullDir := filepath.Join(mediaRoot, folder)
		if !hasMp3(fullDir) {
			continue
		}
		var coverUrl string
		if coverExists(fullDir) {
			coverUrl = fmt.Sprintf("/media/%s/cover.jpg", folder)
		} else {
			coverUrl = defaultCover
		}
		title := folder
		desc := ""
		nfoPath := filepath.Join(fullDir, "book.nfo")
		data, err := os.ReadFile(nfoPath)
		if err == nil {
			var raw RawNfo
			if err = json.Unmarshal(data, &raw); err == nil {
				if raw.Title != "" {
					title = raw.Title
				}
				desc = raw.Description
			}
		}
		albumNfo := AlbumNfo{
			Title: title,
			Cover: coverUrl,
			Desc:  desc,
		}
		list = append(list, AlbumSimple{
			FolderName: folder,
			Nfo:        albumNfo,
		})
	}
	page, pageSize, paged := paginationParams(c)
	if paged {
		total := len(list)
		start := (page - 1) * pageSize
		if start > total {
			start = total
		}
		end := start + pageSize
		if end > total {
			end = total
		}
		items := list[start:end]
		if items == nil {
			items = []AlbumSimple{}
		}
		c.JSON(http.StatusOK, ApiResult{Code: 200, Msg: "ok", Data: map[string]interface{}{
			"total":     total,
			"page":      page,
			"page_size": pageSize,
			"items":     items,
		}})
		return
	}
	c.JSON(http.StatusOK, ApiResult{Code: 200, Msg: "ok", Data: list})
}

func getAlbumFull(c *gin.Context) {
	folder := c.Param("folder")
	albumDir := filepath.Join(mediaRoot, folder)
	if _, err := os.Stat(albumDir); err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 404, Msg: "专辑不存在"})
		return
	}
	if !hasMp3(albumDir) {
		c.JSON(http.StatusOK, ApiResult{Code: 404, Msg: "无音频文件"})
		return
	}
	var coverUrl string
	if coverExists(albumDir) {
		coverUrl = fmt.Sprintf("/media/%s/cover.jpg", folder)
	} else {
		coverUrl = defaultCover
	}
	title := folder
	desc := ""
	nfoPath := filepath.Join(albumDir, "book.nfo")
	nfoByte, err := os.ReadFile(nfoPath)
	if err == nil {
		var raw RawNfo
		if err = json.Unmarshal(nfoByte, &raw); err == nil {
			if raw.Title != "" {
				title = raw.Title
			}
			desc = raw.Description
		}
	}
	albumNfo := AlbumNfo{
		Title: title,
		Cover: coverUrl,
		Desc:  desc,
	}
	files, err := os.ReadDir(albumDir)
	if err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 500, Msg: "读取音频失败"})
		return
	}
	var audios []AudioItem
	for _, f := range files {
		if f.IsDir() {
			continue
		}
		name := f.Name()
		if strings.HasSuffix(strings.ToLower(name), ".mp3") {
			audios = append(audios, AudioItem{
				FileName: name,
				FileUrl:  fmt.Sprintf("/media/%s/%s", folder, name),
			})
		}
	}
	sort.Slice(audios, func(i, j int) bool {
		return naturalLess(audios[i].FileName, audios[j].FileName)
	})
	full := AlbumFull{
		FolderName: folder,
		Nfo:        albumNfo,
		Audios:     audios,
	}
	c.JSON(http.StatusOK, ApiResult{Code: 200, Msg: "ok", Data: full})
}

// 获取完整原始nfo数据
func getAlbumFullNfo(c *gin.Context) {
	folder := c.Param("folder")
	nfoPath := filepath.Join(mediaRoot, folder, "book.nfo")
	var raw RawNfo
	data, err := os.ReadFile(nfoPath)
	if err == nil {
		_ = json.Unmarshal(data, &raw)
	}
	c.JSON(http.StatusOK, ApiResult{Code: 200, Msg: "ok", Data: raw})
}

// 修改简介旧接口
func editAlbumInfo(c *gin.Context) {
	folder := c.Param("folder")
	type Req struct {
		Desc string `json:"desc"`
	}
	var req Req
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 400, Msg: "参数错误"})
		return
	}
	nfoPath := filepath.Join(mediaRoot, folder, "book.nfo")
	byteData, err := os.ReadFile(nfoPath)
	if err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 500, Msg: "读取nfo失败"})
		return
	}
	var raw RawNfo
	_ = json.Unmarshal(byteData, &raw)
	raw.Description = req.Desc
	newByte, _ := json.MarshalIndent(raw, "", "  ")
	_ = os.WriteFile(nfoPath, newByte, 0644)
	c.JSON(http.StatusOK, ApiResult{Code: 200, Msg: "修改成功"})
}

// 修改书名旧接口
func editAlbumTitle(c *gin.Context) {
	folder := c.Param("folder")
	type Req struct {
		NewTitle string `json:"newTitle"`
	}
	var req Req
	if err := c.ShouldBindJSON(&req); err != nil || req.NewTitle == "" {
		c.JSON(http.StatusOK, ApiResult{Code: 400, Msg: "名称不能为空"})
		return
	}
	nfoPath := filepath.Join(mediaRoot, folder, "book.nfo")
	data, err := os.ReadFile(nfoPath)
	if err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 500, Msg: "无book.nfo，无法修改名称"})
		return
	}
	var raw RawNfo
	_ = json.Unmarshal(data, &raw)
	raw.Title = req.NewTitle
	newData, _ := json.MarshalIndent(raw, "", "  ")
	_ = os.WriteFile(nfoPath, newData, 0644)
	c.JSON(http.StatusOK, ApiResult{Code: 200, Msg: "书名修改完成"})
}

// 完整编辑所有nfo字段
func editAlbumAllInfo(c *gin.Context) {
	folder := c.Param("folder")
	type Req struct {
		Title       string `json:"title"`
		Author      string `json:"author"`
		Narrator    string `json:"narrator"`
		Category    string `json:"category"`
		Year        string `json:"year"`
		Description string `json:"description"`
	}
	var req Req
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 400, Msg: "参数错误"})
		return
	}
	nfoPath := filepath.Join(mediaRoot, folder, "book.nfo")
	var raw RawNfo
	byteData, err := os.ReadFile(nfoPath)
	if err == nil {
		_ = json.Unmarshal(byteData, &raw)
	}
	raw.Title = req.Title
	raw.Author = req.Author
	raw.Narrator = req.Narrator
	raw.Category = req.Category
	raw.Year = req.Year
	raw.Description = req.Description
	newByte, err := json.MarshalIndent(raw, "", "  ")
	if err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 500, Msg: "序列化nfo失败"})
		return
	}
	err = os.WriteFile(nfoPath, newByte, 0644)
	if err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 500, Msg: "写入book.nfo失败"})
		return
	}
	c.JSON(http.StatusOK, ApiResult{Code: 200, Msg: "全部资料保存成功，已自动创建book.nfo"})
}

// 上传封面接口
func uploadCoverImage(c *gin.Context) {
	folder := c.Param("folder")
	albumDir := filepath.Join(mediaRoot, folder)
	if err := os.MkdirAll(albumDir, 0755); err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 500, Msg: "创建专辑目录失败：" + err.Error()})
		return
	}
	file, err := c.FormFile("img")
	if err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 400, Msg: "未上传图片文件"})
		return
	}
	ext := strings.ToLower(filepath.Ext(file.Filename))
	if ext != ".jpg" && ext != ".jpeg" {
		c.JSON(http.StatusOK, ApiResult{Code: 400, Msg: "仅支持jpg/jpeg图片"})
		return
	}
	savePath := filepath.Join(albumDir, "cover.jpg")
	if err := c.SaveUploadedFile(file, savePath); err != nil {
		c.JSON(http.StatusOK, ApiResult{Code: 500, Msg: "封面保存失败：" + err.Error()})
		return
	}
	c.JSON(http.StatusOK, ApiResult{Code: 200, Msg: "封面上传替换成功"})
}
