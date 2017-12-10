package com.davidecavestro.apm.springbootsample;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

  private String helloMessage = "Greetings from Spring Boot!";
  private String failureMessage = "FAKE failure";

  @RequestMapping("/")
  public String index(@RequestParam(value = "fail", required = false, defaultValue = "false") final boolean fail) {
    if (fail) {
      throw new RuntimeException (failureMessage);
    }
    return helloMessage;
  }

  public String getHelloMessage () {
    return helloMessage;
  }

  public void setHelloMessage (String helloMessage) {
    this.helloMessage = helloMessage;
  }

  public String getFailureMessage () {
    return failureMessage;
  }

  public void setFailureMessage (String failureMessage) {
    this.failureMessage = failureMessage;
  }
}
