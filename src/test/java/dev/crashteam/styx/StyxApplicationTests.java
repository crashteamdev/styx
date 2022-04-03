package dev.crashteam.styx;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.crashteam.styx.model.proxy.ProxyLineResponse;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import dev.crashteam.styx.service.proxy.ExternalSourceProxyService;
import dev.crashteam.styx.service.proxy.ProxyLineService;
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
    ProxyLineService proxyLineService;

    @MockBean
    CachedProxyService cachedProxyService;

    @MockBean
    ConversationService conversationService;

    @Test
    @SneakyThrows
    public void testProxy6Model() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        ProxyLineResponse proxies = objectMapper.readValue(getStubJson("src/test/java/resources/stub/proxylineresponse.json"),
                ProxyLineResponse.class);
        Mockito.when(proxyLineService.getProxy()).thenReturn(Flux.just(proxies));
        Assertions.assertDoesNotThrow(() -> proxyLineService.getProxy());
    }

    @SneakyThrows
    private String getStubJson(String path) {
        return new String(Files.readAllBytes(Paths.get(path)));
    }
}
