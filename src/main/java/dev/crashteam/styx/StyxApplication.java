package dev.crashteam.styx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StyxApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(StyxApplication.class);
        application.setWebApplicationType(WebApplicationType.REACTIVE);
        application.run(StyxApplication.class, args);
    }

}
