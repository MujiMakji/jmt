
package kr.co.gmgo.rcs.api.biz;

import kr.co.gmgo.rcs.api.biz.controller.BizController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.text.SimpleDateFormat;
import java.util.*;

@EnableScheduling
@SpringBootApplication
public class Application {

    @Autowired
    BizController biz;

    public static void main(String args[]){
        SpringApplication.run(Application.class, args);
    }

}
