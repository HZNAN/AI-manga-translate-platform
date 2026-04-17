package com.hznan.mamgareader.config;

import com.hznan.mamgareader.util.JwtUtil;
import com.hznan.mamgareader.websocket.JwtWebSocketInterceptor;
import com.hznan.mamgareader.websocket.TranslateProgressHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TranslateProgressHandler translateProgressHandler;
    private final JwtUtil jwtUtil;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(translateProgressHandler, "/api/ws/translate")
                .addInterceptors(new JwtWebSocketInterceptor(jwtUtil))
                .setAllowedOriginPatterns("*");
    }
}
