package com.zhixu.kb.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_structure")
public class NoteStructure {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noteId;
    private String outlineJson;
    private String architectureImgUrl;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
