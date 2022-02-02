package com.gloombox.styx.controller;

import com.gloombox.styx.model.proxy.CachedProxy;
import com.gloombox.styx.model.web.ProxiedResponse;
import com.gloombox.styx.service.proxy.ExternalSourceProxyService;
import com.gloombox.styx.service.web.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    public Mono<ResponseEntity<ProxiedResponse>> getProxiedResult(@RequestParam("url") String url, @RequestHeader Map<String, String> headers) {
        return conversationService.getProxiedResponse(url, headers)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/save")
    public Flux<ResponseEntity<CachedProxy>> saveProxy(@RequestBody List<CachedProxy> cachedProxies) {
        return externalSourceProxyService.saveProxyFromExternalSource(cachedProxies)
                .map(ResponseEntity::ok);
    }
}
