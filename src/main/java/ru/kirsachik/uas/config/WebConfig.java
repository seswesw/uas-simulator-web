package ru.kirsachik.uas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.kirsachik.uas.security.AuditInterceptor;
import ru.kirsachik.uas.security.AuthorizationInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthorizationInterceptor authorizationInterceptor;
    private final AuditInterceptor auditInterceptor;

    public WebConfig(AuthorizationInterceptor authorizationInterceptor, AuditInterceptor auditInterceptor) {
        this.authorizationInterceptor = authorizationInterceptor;
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/monitor").setViewName("forward:/index.html");
        registry.addViewController("/drones").setViewName("forward:/index.html");
        registry.addViewController("/missions").setViewName("forward:/index.html");
        registry.addViewController("/admin").setViewName("forward:/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**");
    }
}
