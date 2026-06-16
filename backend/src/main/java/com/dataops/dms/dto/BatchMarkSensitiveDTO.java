package com.dataops.dms.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchMarkSensitiveDTO {
    private String instanceId;
    private String schemaName;
    private String tableName;
    private List<MarkSensitiveDTO> columns;
}
