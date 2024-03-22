package org.jetlinks.community.device.entity;

import lombok.Data;

import java.util.List;

/**
 * @author Juzipi
 * @version 1.0
 * @date 2024-03-20 17:21
 */
@Data
public class SeriesData {
    private String name;
    private String type = "line";
    private List<String> data;
}
