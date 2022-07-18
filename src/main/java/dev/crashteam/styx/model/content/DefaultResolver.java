package dev.crashteam.styx.model.content;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Data
@Component
public class DefaultResolver extends BaseResolver {

    private String mediaType;

    @Override
    public Object formObjectValue(Object object) {
        return base64ToString((String) object);
    }

    private String base64ToString(String value) {
        return new String(Base64.getDecoder().decode(value));
    }
}
