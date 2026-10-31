package com.tongji.uav.dto;

import lombok.Data;

import java.util.List;

/**
 * 审计日志分页结果。
 */
@Data
public class AuditPageVO {

    private Long total;

    private Integer page;

    private Integer size;

    private List<AuditLogVO> records;
}
