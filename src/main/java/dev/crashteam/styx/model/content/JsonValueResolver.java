package dev.crashteam.styx.model.content;

import lombok.Data;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Data
@Service
public class JsonValueResolver extends BaseResolver {

    public JsonValueResolver() {
        this.setMediaType(MediaType.APPLICATION_JSON_VALUE);
    }

    @Override
    public Object formObjectValue(Object object) {
        return base64toJsonString((String) object);
    }

    private String base64toJsonString(String value) {
        return new String(Base64.getDecoder().decode(value));
    }
}
