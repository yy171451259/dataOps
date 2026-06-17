package com.dataops.dms.common.result;

import lombok.Data;

import java.util.List;

/**
 * 统一分页返回结果
 * 前端通过 data.list 访问数据，通过 data.total 访问总条数，
 * 通过 data.page 访问当前页码，通过 data.size 访问每页条数。
 */
@Data
public class PageResult<T> {

    private Integer page;
    private Integer size;
    private Long total;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(Integer page, Integer size, Long total, List<T> list) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.list = list;
    }

    public static <T> PageResult<T> of(Integer page, Integer size, Long total, List<T> list) {
        return new PageResult<>(page, size, total, list);
    }
}
