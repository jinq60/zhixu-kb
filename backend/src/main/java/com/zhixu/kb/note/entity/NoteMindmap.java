package com.zhixu.kb.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_mindmap")
public class NoteMindmap {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noteId;
    private String mapType;
    private String mapData;
    private String mapUrl;
    private String thumbnailUrl;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
