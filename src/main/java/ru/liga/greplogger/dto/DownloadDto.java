package ru.liga.greplogger.dto;

import lombok.Data;

import java.util.List;

@Data
public class DownloadDto {
    private String fileExtension;
    private List<String> files;
}
