package dev.crashteam.styx.controller;

import dev.crashteam.styx.model.web.ProxyRequestParams;
import dev.crashteam.styx.model.web.Result;
import dev.crashteam.styx.service.web.AdvancedConversationService;
import dev.crashteam.styx.service.web.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final ConversationService conversationService;
    private final AdvancedConversationService advancedConversationService;

    @GetMapping("/proxy")
    @Deprecated
    public Mono<ResponseEntity<Result>> getProxiedResult(@RequestParam("url") String url,
                                                         @RequestParam(value = "timeout", required = false, defaultValue = "0") Long timeout,
                                                         @RequestHeader Map<String, String> headers) {
        return conversationService.getProxiedResponse(URLDecoder.decode(url, StandardCharsets.UTF_8), headers, timeout)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/v2/proxy")
    public Mono<ResponseEntity<Result>> getProxiedResultWithParams(@RequestBody ProxyRequestParams params) {
        return advancedConversationService.getProxiedResult(params)
                .map(ResponseEntity::ok);
    }
}
