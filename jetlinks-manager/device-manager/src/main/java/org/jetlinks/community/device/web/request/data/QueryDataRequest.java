package org.jetlinks.community.device.web.request.data;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * @author Juzipi
 * @version 1.0
 * @date 2024-03-20 17:09
 */
@Setter
@Getter
public class QueryDataRequest {
    //设备Id
    private String deviceId;

    //筛选数据参数
    private List<String> properties;



    //查询时间间隔  如 1min 2min
    private String rangeTime;

    //返回条数
    private Integer countSize = 3600;
}
