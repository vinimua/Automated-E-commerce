package com.tk.ai.video.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private int page;
    private int pageSize;
    private long total;
    private int totalPages;

    public static <T> PageResult<T> from(IPage<T> iPage) {
        return new PageResult<>(
                iPage.getRecords(),
                (int) iPage.getCurrent(),
                (int) iPage.getSize(),
                iPage.getTotal(),
                (int) iPage.getPages()
        );
    }
}
