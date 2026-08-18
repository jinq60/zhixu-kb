package com.zhixu.kb.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_section")
public class NoteSection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noteId;
    private Long parentId;
    private String title;
    private String content;
    private Integer level;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
