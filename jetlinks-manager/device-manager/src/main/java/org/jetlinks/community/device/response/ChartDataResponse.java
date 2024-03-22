package org.jetlinks.community.device.response;

import lombok.Data;
import org.jetlinks.community.device.entity.SeriesData;

import java.util.List;

/**
 * @author Juzipi
 * @version 1.0
 * @date 2024-03-20 17:19
 */
@Data
public class ChartDataResponse {
    private List<String> xAxisData;
    private List<SeriesData> seriesData;
}
