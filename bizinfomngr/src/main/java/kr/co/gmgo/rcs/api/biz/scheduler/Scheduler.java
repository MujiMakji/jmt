package kr.co.gmgo.rcs.api.biz.scheduler;

import kr.co.gmgo.rcs.api.biz.controller.*;
import kr.co.gmgo.rcs.api.biz.service.BrandService;
import kr.co.gmgo.rcs.api.biz.service.SchedulerService;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;
import java.io.IOException;

@Component
public class Scheduler {

    private Logger log = LoggerFactory.getLogger("log.rcsLog");

    @Autowired
    SchedulerService schedulerService;
    @Autowired
    UtilController util;
    @Autowired
    WebhookController web;
    @Autowired
    BizController biz;


    /**
     매일 23시에 동작하는 스케줄러로 현재날짜의 다음날짜의 파티션에 데이터를 쌓는다
     * @author Moon
     */
    @Scheduled(cron = "0 0 23 * * *")
    public void getBizData(){
        log.info("Scheduled name is {}", "getBizData");
        schedulerService.getBizData();
    }

    /**
     1. core file 생성 : 매일 24시에 동작하는 스케줄러로 현재날짜의 파티션에 있는 데이터와 어제 날짜에 있는 데이터를 비교하여
     다른점이 있으면 해당 ID값을 file로 만들어 core쪽에 전달
     2. 파티션 정리  : 해당일자의 파티션으로 부터 15일 이전에 쌓인 파티션의 데이터 delete

     * @author Moon
     */
    @Scheduled(cron = "0 0 00 * * *")//매일 24시 실행
    public void createCorefile(){
        log.info("Scheduled name is {}", "getBizData");
        schedulerService.createCorefile();
    }

}
