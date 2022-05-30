package kr.co.gmgo.rcs.api.biz.controller;

import kr.co.gmgo.rcs.api.biz.common.Constants;
import kr.co.gmgo.rcs.api.biz.common.ResponseBuilder;
import kr.co.gmgo.rcs.api.biz.service.BrandService;
import kr.co.gmgo.rcs.api.biz.service.ChatbotService;
import kr.co.gmgo.rcs.api.biz.service.SchedulerService;
import kr.co.gmgo.rcs.api.biz.service.TemplateService;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import javax.servlet.http.HttpServletRequest;


import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

@Controller
@RequestMapping(value="biz")
public class BizController {
    @Autowired
    BrandService brandService;
    @Autowired
    ChatbotService chatbotService;
    @Autowired
    TemplateService templateService;

    @Autowired
    UtilController util;

    @Autowired
    SchedulerService schedulerService;
    private Logger log = LoggerFactory.getLogger("log.rcsLog");


    /**
     * Biz center 등록브랜드, 템플릿, 챗봇, 템플릿 폼 API호출 DB Insert(수동)
     * file로 생성 후 insert
     * @param compareDate coreFile 생성시 비교할 이전 날짜 ex)date= 1 > 현재 insert하는 데이터와 어제 파티션의 데이터 비교하여 corefile생성
     * @param regDate 서버 재 구동하여 호출 시 넘겨주는 날짜 값으로 해당 값이 null이 아니면 등록날짜에 해당 값이 들어간다.
     *                22시-24시 사이에 재구동하여 호출 시 오늘로부터 내일날짜값이 넘어오고
     *                그 외의 시간에는 오늘날짜를 넘겨주고있음.
     * @author Moon
     * @exception IOException
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    @GetMapping("/all")
    public ResponseEntity<Object> bizAll(@RequestParam(value = "compareDate", required = false)String compareDate,
                                         @RequestParam(value = "regDate", required = false)String regDate,
                                         HttpServletRequest req,
                                         @RequestParam(value = "type", required = false, defaultValue = "all")String type){
        log.info("method name is {}", "/biz/all");

        ResponseBuilder builder = new ResponseBuilder();


        boolean ipCheck = util.ipCheck(req);
        if(ipCheck){
            int brandResult = -99;
            int tmplFormResult = -99;
            int chatResult =  -99;
            int tmplResult = -99;

            try {

                if(compareDate == null){
                    compareDate = "1";
                }
                if(regDate == null || regDate.equals("")){//오늘 날짜
                    Calendar cal = Calendar.getInstance();
                    SimpleDateFormat format = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss");
                    regDate =format.format(cal.getTime());
                }

                int insertInt = 0;
                if(compareDate != null && !compareDate.equals("0")){
                    insertInt = Integer.parseInt(compareDate);
                }else if(compareDate == null){
                    insertInt = 1;
                }

                if(type.equals("template")){
                    brandResult =  brandService.getBrandDetail(compareDate, regDate);
                    tmplResult = templateService.getTemplateList(compareDate, regDate);
                }else if(type.equals("chatbot")){
                    brandResult =  brandService.getBrandDetail(compareDate, regDate);
                    chatResult =  chatbotService.getChatbotList(compareDate, regDate);
                }else if(type.equals("brand")){
                    brandResult =  brandService.getBrandDetail(compareDate, regDate);
                }else if(type.equals("templateForm")){
                    brandResult =  brandService.getBrandDetail(compareDate, regDate);
                    tmplFormResult = templateService.getTemplateForm(compareDate, regDate);
                }else{//all
                    brandResult =  brandService.getBrandDetail(compareDate, regDate);
                    tmplFormResult = templateService.getTemplateForm(compareDate, regDate);
                    chatResult =  chatbotService.getChatbotList(compareDate, regDate);
                    tmplResult = templateService.getTemplateList(compareDate, regDate);
                }
                File Brand = null;
                File Template = null;
                File Chatbot = null;
                File TemplateFoem = null;

                boolean isExistsBrand = false;
                boolean isExistsTemplate = false;
                boolean isExistsChatbot = false;
                boolean isExistsTemplateForm = false;

                if(brandResult == 100) {
                    log.info("brand insert Success");
                    schedulerService.dataGetFile("getBrandData");
                    Brand = new File(Constants.PATH_FILE_ROOT + "getBrandData");
                    isExistsBrand = Brand.exists();
                    log.info("brandAll Success");
                    builder.setMessage("SUCCESS");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                }

                if(tmplFormResult == 100){
                    log.info("templateForm insert Success");
                    schedulerService.dataGetFile("getTemplateFormData");
                    TemplateFoem = new File(Constants.PATH_FILE_ROOT + "getTemplateFormData");
                    isExistsTemplateForm = TemplateFoem.exists();
                    log.info("templateFormAll Success");
                    builder.setMessage("SUCCESS");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                }
                if(chatResult == 100){
                    log.info("chatbot insert Success");
                    schedulerService.dataGetFile("getChatbotData");
                    Chatbot = new File(Constants.PATH_FILE_ROOT + "getChatbotData");
                    isExistsChatbot = Chatbot.exists();
                    log.info("chatbotAll Success");
                    builder.setMessage("SUCCESS");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                }

                if(tmplResult == 100){
                    log.info("template insert Success");
                    schedulerService.dataGetFile("getTemplateData");
                    Template = new File(Constants.PATH_FILE_ROOT + "getTemplateData");
                    isExistsTemplate = Template.exists();
                    log.info("templateAll Success");
                    builder.setMessage("SUCCESS");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                }


                if(isExistsTemplate){
                    schedulerService.coreFileTemplate(insertInt);
                    boolean temDelete =  Template.delete();
                    System.out.println("temDelete :" +temDelete);
                }
                if(isExistsChatbot){
                    schedulerService.coreFileChatbot(insertInt);
                    boolean chatDelete = Chatbot.delete();
                    System.out.println("chatDelete :" +chatDelete);
                }
                if(isExistsTemplateForm){
                    schedulerService.coreFileTemplateForm(insertInt);
                    boolean formDelete = TemplateFoem.delete();
                    System.out.println("formDelete :" +formDelete);
                }

                if(isExistsTemplate){
                    boolean BrandDelete =  Brand.delete();
                }

            } catch (IOException e) {
                e.printStackTrace();
                log.info("IOException - bizAll FAIL");
            } catch (ParseException e) {
                e.printStackTrace();
                log.info("ParseException - bizAll FAIL");
            }

        }else{
            builder.setResult(ResponseBuilder.Result.FAIL).setMessage("No permission").build();
            log.info("ipCheck NO PERMISSION");
        }
        return builder.build();
    }

}
