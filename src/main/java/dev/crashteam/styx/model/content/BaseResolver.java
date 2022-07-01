package dev.crashteam.styx.model.content;

import lombok.Data;
import org.springframework.stereotype.Service;

@Data
@Service
public abstract class BaseResolver implements ValueResolver {

    private String mediaType;
}
