package dev.crashteam.styx.controller;

import dev.crashteam.styx.model.proxy.CachedProxy;
import dev.crashteam.styx.model.web.Response;
import dev.crashteam.styx.service.proxy.ExternalSourceProxyService;
import dev.crashteam.styx.service.web.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final ConversationService conversationService;
    private final ExternalSourceProxyService externalSourceProxyService;

    @GetMapping("/proxy")
    public Mono<ResponseEntity<Response>> getProxiedResult(@RequestParam("url") String url,
                                                           @RequestHeader Map<String, String> headers,
                                                           WebSession webSession) {
        webSession.getAttributes().put(webSession.getId(), 3);
        webSession.getAttributes()
                .forEach((k, v) -> System.out.println("key " + k + " value " + v));
        return conversationService.getProxiedResponse(url, headers, webSession)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/save")
    public Flux<ResponseEntity<CachedProxy>> saveProxy(@RequestBody List<CachedProxy> cachedProxies) {
        return externalSourceProxyService.saveProxyFromExternalSource(cachedProxies)
                .map(ResponseEntity::ok);
    }
}
