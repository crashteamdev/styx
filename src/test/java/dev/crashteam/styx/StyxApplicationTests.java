package dev.crashteam.styx;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.crashteam.styx.model.proxy.Proxy6Dto;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import dev.crashteam.styx.service.proxy.ExternalSourceProxyService;
import dev.crashteam.styx.service.proxy.Proxy6Service;
import dev.crashteam.styx.service.web.ConversationService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Paths;


@ExtendWith(SpringExtension.class)
@Import({ConversationService.class, ExternalSourceProxyService.class})
class StyxApplicationTests {

    @MockBean
    Proxy6Service proxy6Service;

    @MockBean
    CachedProxyService cachedProxyService;

    @MockBean
    ConversationService conversationService;

    @Test
    @SneakyThrows
    public void testProxy6Model() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        Proxy6Dto proxies = objectMapper.readValue(getStubJson("src/test/java/resources/stub/proxy6response.json"),
                Proxy6Dto.class);
        Mockito.when(proxy6Service.getProxy()).thenReturn(Flux.just(proxies));
        Assertions.assertDoesNotThrow(() -> proxy6Service.getProxy());
    }

    @SneakyThrows
    private String getStubJson(String path) {
        return new String(Files.readAllBytes(Paths.get(path)));
    }
}
