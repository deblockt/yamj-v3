package com.moviejukebox.common.remote.service;

import com.moviejukebox.common.dto.FileDeletionDTO;
import com.moviejukebox.common.dto.FileImportDTO;

public interface FileImportService {

    void importFile(FileImportDTO fileImportDTO);

    void deleteFile(FileDeletionDTO fileDeletionDTO);
}