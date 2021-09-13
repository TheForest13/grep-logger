package ru.liga.greplogger.dto;

import lombok.Data;

@Data
public class GrepDto {

    /**
     * Директория для поиска
     */
    private String path;

    /**
     * Имя файла для поиска
     */
    private String fileName;

    /**
     * Расширение файла для поиска
     */
    private String fileExtension;

    /**
     * От какого времени
     */
    private String aminFrom;

    /**
     * До какого времени
     */
    private String aminTo;

    /**
     * Слово для поиска
     */
    private String searchWord;
}
