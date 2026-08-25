package com.zhixu.kb.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_info")
public class FileInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long noteId;
    private String originalName;
    /** 服务器存储名与绝对路径不对 API 暴露：防止泄露部署目录结构 */
    @JsonIgnore
    private String storedName;
    @JsonIgnore
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime uploadTime;
}
