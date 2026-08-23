package com.zhixu.kb.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
    private String storedName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime uploadTime;
}
