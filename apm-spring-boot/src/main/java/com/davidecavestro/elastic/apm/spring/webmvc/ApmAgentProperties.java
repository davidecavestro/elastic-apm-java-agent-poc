package com.davidecavestro.elastic.apm.spring.webmvc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="elastic.apm")
public class ApmAgentProperties {
  private String appName;
  private String secretToken;
}
