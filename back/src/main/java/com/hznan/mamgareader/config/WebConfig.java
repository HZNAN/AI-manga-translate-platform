package com.hznan.mamgareader.config;

import com.hznan.mamgareader.interceptor.AuthInterceptor;
import com.hznan.mamgareader.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/mangas/*/pages/*/image",
                        "/api/mangas/*/pages/*/thumbnail",
                        "/api/mangas/*/pages/*/translated-image",
                        "/api/mangas/page-by-id/*/image",
                        "/api/mangas/page-by-id/*/thumbnail",
                        "/api/mangas/page-by-id/*/translated-image",
                        "/api/translate/records/*/image",
                        "/api/ws/**"
                );

        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/translate/page",
                        "/api/translate/page/json",
                        "/api/translate/batch"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
