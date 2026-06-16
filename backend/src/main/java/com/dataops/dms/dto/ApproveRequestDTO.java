package com.dataops.dms.dto;

import lombok.Data;

@Data
public class ApproveRequestDTO {
    private Boolean approved;
    private String comment;
}
