package com.davidecavestro.apm.springbootsample;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

  @RequestMapping("/")
  public String index(@RequestParam(value = "fail", required = false, defaultValue = "false") final boolean fail) {
    if (fail) {
      throw new RuntimeException ("FAKE failure");
    }
    return "Greetings from Spring Boot!";
  }
}
