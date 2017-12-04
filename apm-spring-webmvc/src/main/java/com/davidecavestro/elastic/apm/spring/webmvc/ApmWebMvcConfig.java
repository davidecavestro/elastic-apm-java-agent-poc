package com.davidecavestro.elastic.apm.spring.webmvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class ApmWebMvcConfig extends WebMvcConfigurerAdapter {

  @Autowired
  ApmSpringService apmSpringService;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new HandlerInterceptorAdapter (){

      @Override
      public boolean preHandle (final HttpServletRequest request, final HttpServletResponse response, final Object handler) throws Exception {

        return true;
      }

      @Override
      public void afterCompletion (final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Exception ex) throws Exception {
        if (ex!=null) {
          apmSpringService.traceError (ex, request, response);
        }
      }
    });
  }

}
