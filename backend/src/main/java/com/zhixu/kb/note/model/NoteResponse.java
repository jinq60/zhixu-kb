package com.zhixu.kb.note.model;

import com.zhixu.kb.note.entity.FileInfo;
import com.zhixu.kb.note.entity.Note;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteResponse {
    private Note note;
    private List<FileInfo> files;
}
