package org.example.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 搜索结果类
 */
@Setter
@Getter
public class SearchResult {
    private String id;
    private String content;
    private float score;
    private String metadata;

}
