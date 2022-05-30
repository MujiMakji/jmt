package kr.co.gmgo.rcs.api.biz.controller;

import kr.co.gmgo.rcs.api.biz.common.ResponseBuilder;
import kr.co.gmgo.rcs.api.biz.mapper.BrandMapper;
import kr.co.gmgo.rcs.api.biz.mapper.ChatbotMapper;
import kr.co.gmgo.rcs.api.biz.mapper.TemplateMapper;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping(value="")
public class WebhookController {

    @Autowired
    TemplateController templateController;
    @Autowired
    BrandController brandController;
    @Autowired
    ChatbotController chatbotController;
    @Autowired
    UtilController util;

    @Autowired
    TemplateMapper templateMapper;
    @Autowired
    BrandMapper brandMapper;
    @Autowired
    ChatbotMapper chatbotMapper;

    private Logger log = LoggerFactory.getLogger("log.rcsLog");


    /**
     * bizCenter에서 webhook전달
     * 1. 변동 내용 확인하여 해당 테이블 업로드
     * 2. 변동 내용 core 파일 생성
     * 3. 로그 남기기
     * *
     * @author Moon
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     * @return bizCenter에 status값 전달(200: 정상, 400:기업이 대행사로 권한 위임을 하지 않았는데 webhook이 전달되었을 경우)
     */
    @RequestMapping("/biz-webhook")
    public ResponseEntity<Object> getData(@RequestBody(required = false) Map<String, Object> body) throws ParseException {
        log.info("method name is {}", "/biz-webhook");
        ResponseBuilder builder = new ResponseBuilder();
        log.info("==============WEBHOOK DATA  : {}", body);
        String Bizcode = "";
        String message ="";
        String code = "";
        String method="";
        Map<Object,String> delMap = new HashMap<>();

        if(body != null){
            if(body.get("notiType")!= null && body.get("notiType").equals("template")){//1. 템플릿
                String brandId =body.get("brandId").toString();
                String messagebaseId = body.get("messagebaseId").toString();
                method = body.get("method").toString();

                log.info("============WEBHOOK TEMPLATE ID :  {}", messagebaseId);
                log.info("============WEBHOOK TEMPLATE method : {}", method);

                if(method.equals("deleted")){//notiType : deleted(삭제)
                    //delMap.put("brand_id",brandId.replace(" ",""));
                    delMap.put("messagebaseId",messagebaseId.replace(" ",""));
                    delMap.put("partition",util.getTodayPartition(0));

                    //TEMPLATE 테이블 해당 값 del_yn값 Y 처리
                    int templateDel  = templateMapper.updateTemplateDelete(delMap);
                    //int templateBtbDel = templateMapper.updateTemplateBtnDelete(delMap);

                    if(templateDel > 0){
                        int coreFile = util.coreFile("TEMPLATE", messagebaseId, "delete");

                        if (coreFile == 100){
                            log.info("[SUCCESS_TEMPLATE_UPDATE_CORE_FILE] messagebaseId : {} ",messagebaseId);
                        }else{
                            log.info("[FAIL_TEMPLATE_UPDATE_CORE_FILE] messagebaseId : {} ",messagebaseId);
                        }

                        log.info("[SUCCESS] TEMPLATE "+ messagebaseId + "del_YN UPDATE : {}", " N ");
                    }else{
                        log.info("[FAIL] TEMPLATE "+ messagebaseId + "del_YN UPDATE : {}", " N ");
                    }
                }else{
                    //notiType : rejected(반려), created(승인/신규등록), modified(수정)
                    Map<String,String> response = templateController.getTemplateDetail(brandId, messagebaseId);
                    Bizcode = response.get("bizResult");
                    message = response.get("message");
                    code = response.get("result");

                    if(Bizcode.equals("200")){
                        if(!code.equals("100")){//실패
                            log.info("message :{}","TEMPLATE "+ message);
                            log.info("Bizcode :{}", Bizcode);
                        }else{
                            log.info("message :{}","TEMPLATE "+  message);
                            log.info("Bizcode :{}", Bizcode);
                        }
                    }else{
                        log.info("message :{}","TEMPLATE"+ message);
                        log.info("Bizcode :{}", Bizcode);
                    }
                }

            }else if(body.get("notiType")!= null && body.get("notiType").equals("contract")){//2. 등록 브랜드
                String brandId = body.get("brandId").toString();
                String agencyId = "gemtek";
                method = body.get("method").toString();

                if(method.equals("deleted")){//notiType : deleted(기업이 대행사 브랜드 권한위임 취소)
                    delMap.put("brandId",brandId);
                    delMap.put("partition",util.getTodayPartition(0));

                    //1.BRAND 테이블 해당 값 del_yn값 Y 처리
                    int brandDel  = brandMapper.updateBrandDelete(delMap);
                    if(brandDel > 0){
                        log.info("[SUCCESS] BRAND "+ brandId + "del_YN UPDATE : {}", " N ");
                    }else{
                        log.info("[FAIL] BRAND "+ brandId + "del_YN UPDATE : {}", " N ");
                    }

                  /*
                    현재 brand데이터 delete 되었을 때 core파일생성
                   if(brandDel > 0){
                        int coreFile = util.coreFile("BRAND", brandId, "delete");

                        if (coreFile == 100){
                            log.info("[SUCCESS_BRAND_UPDATE_CORE_FILE] brandId : {} ",brandId);
                        }else{
                            log.info("[FAIL_BRAND_UPDATE_CORE_FILE] brandId : {} ",brandId);
                        }

                        log.info("[SUCCESS] BRAND "+ brandId + "del_YN UPDATE : {}", " N ");
                    }else{
                        log.info("[FAIL] BRAND "+ brandId + "del_YN UPDATE : {}", " N ");
                    }*/

                    //2.delete 브랜드에 해당하는 CHATBOT del_yn N 처리
                    int ChatDel  = chatbotMapper.updateChatbotDelete(delMap);
                    log.info("[SUCCESS] BRAND "+ brandId + "in CHATBOT del_YN count: {}", ChatDel);
                    String delChatList = chatbotMapper.selectDelChatList(delMap);
                    if(delChatList != null){
                        int delChatMakeFile = makeFileChatbot(delMap, delChatList);
                        log.info("[SUCCESS] create FIle for core "+brandId+" in chatbot count : {}", delChatMakeFile);
                    }


                    //3.delete 브랜드에 해당하는 TMPLATE del_yn N 처리
                    int templateDel  = templateMapper.updateTemplateDelete(delMap);
                    log.info("[SUCCESS] BRAND "+ brandId + "in TEMPLATE del_YN count: {}", templateDel);
                    String delTmplList = templateMapper.selectDelTmplList(delMap);
                    if(delTmplList != null){
                        int delTemplateFile = makeFileTemplate(delMap,delTmplList);
                        log.info("[SUCCESS] create FIle for core "+brandId+" in template count : {}", delTemplateFile);
                    }

                    //3.delete 브랜드에 해당하는 TMPLATE_BTN del_yn N 처리
                    int templateBtnDel  = templateMapper.updateTemplateBtnDelete(delMap);
                    log.info("[SUCCESS] BRAND "+ brandId + "in TEMPLATE_BTN del_YN count: {}", templateDel);
                    String delTmplBtn = templateMapper.selectDelTmplBtnList(delMap);

                    if(delTmplBtn != null){
                        int delTemplateBtnFile = makeFileTemplateBtn(delMap,delTmplBtn);
                        log.info("[SUCCESS] create FIle for core "+brandId+" in template count : {}", delTemplateBtnFile);
                    }
                }else{
                    //notiType : rejected(반려), created(승인/신규등록), modified(수정)
                    Map<String,String> response = brandController.getBrandDetail(agencyId, brandId);
                    Bizcode = response.get("bizResult");
                    message = response.get("message");
                    code = response.get("result");

                    if(Bizcode.equals("200")){
                        if(!code.equals("100")){//실패
                            log.info("message :{}","BRAND " + message);
                            log.info("Bizcode :{}", Bizcode);
                        }else{
                            log.info("message :{}","BRAND " + message);
                            log.info("Bizcode :{}", Bizcode);
                        }
                    }else{
                        log.info("message :{}", message);
                        log.info("Bizcode :{}", Bizcode);
                    }

                    int chkId  = brandMapper.selectBrandIdInbrand(brandId,util.getTodayPartition(0));

                    // 브랜드 권한 재위임일 경우 해당 브랜드에 속해있는 Template, Chatbot 정보 insert, update처리 후
                    // core file 생성
                    if(method.equals("created") && chkId > 0){
                        templateController.reCreatedTemplateChk(brandId);//template
                        chatbotController.reCreatedChatbotChk(brandId);//chatbot
                    }
                }
            }else if(body.get("notiType")!= null && body.get("notiType").equals("chatbot")){//3. 챗봇

                String brandId = body.get("brandId").toString();
                String chatbotId = body.get("chatbotId").toString();
                method = body.get("method").toString();

                log.info("WEBHOOK BRAND ID :{}", brandId);
                log.info("WEBHOOK CHATBOT ID :{}", chatbotId);
                log.info("WEBHOOK CHATBOT method {}", method);

                if(method.equals("deleted")){
                    delMap.put("brandId",brandId);
                    delMap.put("chatbotId",chatbotId);
                    delMap.put("partition",util.getTodayPartition(0));
                    makeFileChatbot(delMap, brandId);
                }else{
                    //method : rejected(반려), created(승인/신규등록), modified(수정)
                    Map<String,String> response = chatbotController.getChatDetail(brandId, chatbotId);
                    Bizcode = response.get("bizResult");
                    message = response.get("message");
                    code = response.get("result");

                    if(Bizcode.equals("200")){
                        if(!code.equals("100")){//실패
                            log.info("message :{}","CHATBOT "+ message);
                            log.info("Bizcode :{}", Bizcode);
                        }else{
                            log.info("message :{}","CHATBOT "+  message);
                            log.info("Bizcode :{}", Bizcode);
                        }
                    }else{
                        log.info("message :{}","CHATBOT "+ message);
                        log.info("Bizcode :{}", Bizcode);
                    }
                }

            }else if(body.get("notiType")!= null && body.get("notiType").equals("messagebaseform")){//4. 템플릿양식(cell, description)
                String messagebaseformId = body.get("messagebaseformId").toString();
                method = body.get("method").toString();

                log.info("====================WEBHOOK TEMPLATE_FORM ID : {}", messagebaseformId);

                if(method.equals("deleted")){//method : deleted(삭제)
                    delMap.put("messagebaseId",messagebaseformId);
                    delMap.put("partition",util.getTodayPartition(0));

                    int temFormDel  = templateMapper.updateTemplateFormDelete(delMap);
                    //TEMPLATE 테이블 해당 값 del_yn값 Y 처리
                    int coreFile =  makeFileTemplateForm(delMap,messagebaseformId);

                    if (coreFile > 0){
                        log.info("[SUCCESS_TEMPLATE_FORM_DELETE_CORE_FILE] messagebaseId : {} ",messagebaseformId);
                    }else{
                        log.info("[FAIL_TEMPLATE_FORM_DELETE_CORE_FILE] messagebaseId : {} ",messagebaseformId);
                    }
                    log.info("[SUCCESS] TEMPLATE_FORM "+ messagebaseformId + "del_YN UPDATE : {}", " N ");

                }else{
                    //notiType : rejected(반려), created(승인/신규등록), modified(수정)
                    Map<String,String> response = templateController.getTemplateForm(messagebaseformId);
                    Bizcode = response.get("bizResult");
                    message = response.get("message");
                    code = response.get("result");

                    if(Bizcode.equals("200")){
                        if(!code.equals("100")){//실패
                            log.info("message :{}","TEMPLATEFORM "+ message);
                            log.info("Bizcode :{}", Bizcode);
                        }else{
                            log.info("message :{}","TEMPLATEFORM "+  message);
                            log.info("Bizcode :{}", Bizcode);
                        }
                    }else{
                        log.info("message :{}","TEMPLATEFORM "+ message);
                        log.info("Bizcode :{}", Bizcode);
                    }
                }

            }else if(body.get("notiType")!= null && body.get("notiType").equals("format")){//5. 포멧 (SMS, LMS, MMS)
                String messagebaseId =body.get("messagebaseId").toString();
                method = body.get("method").toString();

                log.info("====================WEBHOOK TEMPLATE_FORM SMS, LMS, MMS ID : {}", messagebaseId);

                if(method.equals("deleted")){//method : deleted(삭제)
                    delMap.put("messagebaseId",messagebaseId);
                    delMap.put("partition",util.getTodayPartition(0));

                    //TEMPLATE 테이블 해당 값 del_yn값 Y 처리
                    int temFormDel  = templateMapper.updateTemplateFormDelete(delMap);

                    int coreFile = makeFileTemplateForm(delMap,messagebaseId);

                    if (coreFile > 0){
                        log.info("[SUCCESS_TEMPLATE_FORM_DELETE_CORE_FILE] messagebaseId : {} ",messagebaseId);
                    }else{
                        log.info("[FAIL_TEMPLATE_FORM_DELETE_CORE_FILE] messagebaseId : {} ",messagebaseId);
                    }

                    log.info("[SUCCESS] TEMPLATE_FORM "+ messagebaseId + "del_YN UPDATE : {}", " N ");
                }else{
                    //method : rejected(반려), created(승인/신규등록), modified(수정)
                    Map<String,String> response = templateController.messageForm(messagebaseId);
                    Bizcode = response.get("bizResult");
                    message = response.get("message");
                    code = response.get("result");

                    if(Bizcode.equals("200")){
                        if(!code.equals("100")){//실패
                            log.info("message :{}","MESSAGEFORM "+ message);
                            log.info("Bizcode :{}", Bizcode);
                        }else{
                            log.info("message :{}","MESSAGEFORM "+  message);
                            log.info("Bizcode :{}", Bizcode);
                        }
                    }else{
                        log.info("message :{}","MESSAGEFORM "+ message);
                        log.info("Bizcode :{}", Bizcode);
                    }
                }
            }else{
                log.info("[FAIL] WEBHOOK DATA : " + body.toString());
                log.info(body.toString());
            }
            builder.setResult(ResponseBuilder.Result.BIZ_STAUTS_SUCCESS).build();
        }else {
            log.info("[FAIL] WEBHOOK DATA : " + body);
        }
        return builder.build();
    }

    public int makeFileChatbot(Map<Object,String> delMap, String chatbotId){
        log.info("method name is {}", "makeFileChatbot");
        //CHATBOT 테이블 해당 값 del_yn값 Y 처리
        int ChatDel  = chatbotMapper.updateChatbotDelete(delMap);

        if(ChatDel > 0){

            int coreFile = util.coreFile("CHATBOT", chatbotId, "delete");

            if (coreFile == 100){
                log.info("[SUCCESS_CHATBOT_UPDATE_CORE_FILE] chatbotId : {} ",chatbotId);
            }else{
                log.info("[FAIL_CHATBOT_UPDATE_CORE_FILE] chatbotId : {} ",chatbotId);
            }

            log.info("[SUCCESS] CHATBOT "+ chatbotId + "del_YN UPDATE : {}", " N ");
        }else{
            log.info("[FAIL] CHATBOT "+ chatbotId + "del_YN UPDATE ");
        }

        return ChatDel;
    }
    public int makeFileTemplate(Map<Object,String> delMap, String messagebaseformId){
        log.info("method name is {}", "makeFileTemplate");
        int templateFormDel  = templateMapper.updateTemplateDelete(delMap);

        if(templateFormDel > 0){
            int coreFile = util.coreFile("TEMPLATE", messagebaseformId, "delete");

            if (coreFile == 100){
                log.info("[SUCCESS_TEMPLATE_UPDATE_CORE_FILE] messagebaseformId : {} ",messagebaseformId);
            }else{
                log.info("[FAIL_TEMPLATE_UPDATE_CORE_FILE] messagebaseformId : {} ",messagebaseformId);
            }
            log.info("[SUCCESS] TEMPLATE "+ messagebaseformId + "del_YN UPDATE : {}", " N ");
        }else{
            log.info("[FAIL] TEMPLATE"+ messagebaseformId + "del_YN UPDATE : {}", " N ");
        }
        return templateFormDel;
    }

    public int makeFileTemplateBtn(Map<Object,String> delMap, String messagebaseId){
        log.info("method name is {}", "makeFileTemplate");
        int templateFormDel =0;

        int coreFile = util.coreFile("TEMPLATE_BTN", messagebaseId, "delete");

        if (coreFile == 100){
            log.info("[SUCCESS_TEMPLATE_BTN_UPDATE_CORE_FILE] messagebaseId : {} ",messagebaseId);
        }else{
            log.info("[FAIL_TEMPLATE_BTN_UPDATE_CORE_FILE] messagebaseId : {} ",messagebaseId);
        }
        log.info("[SUCCESS] TEMPLATE_BTN "+ messagebaseId + "del_YN UPDATE : {}", " N ");

        return templateFormDel;
    }

    public int makeFileTemplateForm(Map<Object,String> delMap, String messagebaseformId){
        log.info("method name is {}", "makeFileTemplateForm");
        int templateFormDel  = templateMapper.updateTemplateFormDelete(delMap);

        if(templateFormDel > 0){
            int coreFile = util.coreFile("TEMPLATE_FORM", messagebaseformId, "delete");

            if (coreFile == 100){
                log.info("[SUCCESS_TEMPLATE_FORM_UPDATE_CORE_FILE] messagebaseformId : {} ",messagebaseformId);
            }else{
                log.info("[FAIL_TEMPLATE_FORM_UPDATE_CORE_FILE] messagebaseformId : {} ",messagebaseformId);
            }

            log.info("[SUCCESS] TEMPLATE_FORM "+ messagebaseformId + "del_YN UPDATE : {}", " N ");
        }else{
            log.info("[FAIL] TEMPLATE_FORM "+ messagebaseformId + "del_YN UPDATE : {}", " N ");
        }
        return templateFormDel;
    }

}
