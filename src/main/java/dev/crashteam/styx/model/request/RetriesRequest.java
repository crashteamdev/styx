package dev.crashteam.styx.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetriesRequest {
    private String requestId;
    private Integer retries;
    private long timeout;
}
