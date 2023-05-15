package dev.crashteam.styx.configuration;


import dev.crashteam.styx.model.forbidden_proxy.ForbiddenProxy;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.request.RetriesRequest;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.ReactiveRedisLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@RequiredArgsConstructor
public class RedisConfiguration {

    @Bean
    public ReactiveRedisOperations<String, ProxyInstance> redisOperations(LettuceConnectionFactory connectionFactory) {
        RedisSerializationContext<String, ProxyInstance> serializationContext = RedisSerializationContext
                .<String, ProxyInstance>newSerializationContext(new StringRedisSerializer())
                .key(new StringRedisSerializer())
                .value(new GenericToStringSerializer<>(ProxyInstance.class))
                .hashKey(new StringRedisSerializer())
                .hashValue(new GenericJackson2JsonRedisSerializer())
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }

    @Bean
    public ReactiveRedisOperations<String, RetriesRequest> redisRetriesRequestOperations(LettuceConnectionFactory connectionFactory) {
        RedisSerializationContext<String, RetriesRequest> serializationContext = RedisSerializationContext
                .<String, RetriesRequest>newSerializationContext(new StringRedisSerializer())
                .key(new StringRedisSerializer())
                .value(new GenericToStringSerializer<>(RetriesRequest.class))
                .hashKey(new StringRedisSerializer())
                .hashValue(new GenericJackson2JsonRedisSerializer())
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }

    @Bean
    public ReactiveRedisOperations<String, ForbiddenProxy> redisForbiddenProxyOperations(LettuceConnectionFactory connectionFactory) {
        RedisSerializationContext<String, ForbiddenProxy> serializationContext = RedisSerializationContext
                .<String, ForbiddenProxy>newSerializationContext(new StringRedisSerializer())
                .key(new StringRedisSerializer())
                .value(new GenericToStringSerializer<>(ForbiddenProxy.class))
                .hashKey(new StringRedisSerializer())
                .hashValue(new GenericJackson2JsonRedisSerializer())
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }

    @Bean
    public LockProvider lockProvider(ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisLockProvider.Builder(connectionFactory)
                .build();
    }

}
