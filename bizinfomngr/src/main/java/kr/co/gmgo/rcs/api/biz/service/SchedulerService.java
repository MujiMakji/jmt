package kr.co.gmgo.rcs.api.biz.service;

import kr.co.gmgo.rcs.api.biz.common.Constants;
import kr.co.gmgo.rcs.api.biz.controller.UtilController;
import kr.co.gmgo.rcs.api.biz.mapper.SchedulerMapper;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;


@Service
public class SchedulerService {
    private Logger log = LoggerFactory.getLogger("log.rcsLog");
    @Autowired
    SchedulerMapper schedulerMapper;
    @Autowired
    BrandService brandService;
    @Autowired
    ChatbotService chatbotService;
    @Autowired
    TemplateService templateService;
    @Autowired
    UtilController util;


    /**
     * 매일 24시 실행
     * 1. BRAND, TEMPLATE, CHATBOT, TEMPLATE_FORM모든 정보를 API호출하여
     * 해당날짜의 파티션에 insert 작업
     * @author Moon
     */

    public void getBizData(){
        log.info("===Start getBiz Data scheduler===");
        int brandResult = 0;
        int tmplFormResult = 0;
        int chatResult = 0;
        int tmplResult = 0;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE,1);
        SimpleDateFormat format = new SimpleDateFormat ( "yyyy-MM-dd 00:00:00");
        String regDate = format.format(cal.getTime()); //오늘 날짜에서 하루 +한 날짜

        try {
            brandResult =  brandService.getBrandDetail("1",regDate);
            tmplFormResult = templateService.getTemplateForm("1", regDate);
            chatResult =  chatbotService.getChatbotList("1", regDate);
            tmplResult = templateService.getTemplateList("1", regDate);
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }

        //BIZ Center로 부터 성공적으로 데이터를 호출하여 insert/update성공했을 시 각 file을 생성하여 파일이 있을 시 core파일 생성작업 진행(file이 생성되지 않을 경우 core파일 생성작업 진행X)
        if(brandResult == 100){
            log.info("brand insert Success");
            dataGetFile("getBrandData");
        }
        if(tmplFormResult == 100){
            log.info("templateForm insert Success");
            dataGetFile("getTemplateFormData");
        }
        if(chatResult == 100){
            log.info("chatbot insert Success");
            dataGetFile("getChatbotData");
        }
        if(tmplResult == 100){
            log.info("template insert Success");
            dataGetFile("getTemplateData");
        }

        if((brandResult + tmplFormResult + chatResult+ tmplResult) == 400) {
            log.info("bizAll Inssert Success");
        }
        log.info("===End getBiz Data scheduler===");


        //TEMPLATE, CHATBOT, TEMPLATE_FORM 추가, 변경, 삭제 내용 file생성(core에 전달)
       /* log.info("===Start create Core File===");
        coreFileTemplate(1);
        coreFileChatbot(1);
        coreFileTemplateForm(1);
        coreFileTemplateBtn(1);
        log.info("===End create Core File===");

        log.info("===Start Partition check===");
        updatePartition();
        log.info("===End Partition check===");*/
    }


    /**
     1. core file 생성 : 매일 24시에 동작하는 스케줄러로 현재날짜의 파티션에 있는 데이터와 어제 날짜에 있는 데이터를 비교하여
     다른점이 있으면 해당 ID값을 file로 만들어 core쪽에 전달
     2. 파티션 정리  : 해당일자의 파티션으로 부터 15일 이전에 쌓인 파티션의 데이터 delete

     * @author Moon
     * @param
     */
    public void createCorefile(){
        //TEMPLATE, CHATBOT, TEMPLATE_FORM 추가, 변경, 삭제 내용 file생성(core에 전달)
        log.info("===Start create Core File===");

        File Brand = new File(Constants.PATH_FILE_ROOT + "getBrandData");
        File Template = new File(Constants.PATH_FILE_ROOT + "getTemplateData");
        File Chatbot = new File(Constants.PATH_FILE_ROOT + "getChatbotData");
        File TemplateFoem = new File(Constants.PATH_FILE_ROOT + "getTemplateFormData");

        boolean isExistsBrand = Brand.exists();
        boolean isExistsTemplate = Template.exists();
        boolean isExistsChatbot = Chatbot.exists();
        boolean isExistsTemplateForm = TemplateFoem.exists();

        if(isExistsBrand){
            if(isExistsTemplate){
                coreFileTemplate(1);
                boolean temDelete =  Template.delete();
                System.out.println("temDelete :" +temDelete);
            }
            if(isExistsChatbot){
                coreFileChatbot(1);
                boolean chatDelete = Chatbot.delete();
                System.out.println("chatDelete :" +chatDelete);
            }
            if(isExistsTemplateForm){
                coreFileTemplateForm(1);
                boolean formDelete = TemplateFoem.delete();
                System.out.println("formDelete :" +formDelete);
            }
            boolean brandDelete = Brand.delete();
            System.out.println("brandDelete :" +brandDelete);
        }

        log.info("===End create Core File===");

        log.info("===Start Partition check===");
        updatePartition();
        log.info("===End Partition check===");
    }

    /**
     * BRAND, TEMPLATE, CHATBOT, TEMPLATE_FORM 테이블 파티션 check
     * 현재 일자로부터 15일 이내의 파티션을 제외한 파티션 DELTE작업
     * getBizData스케줄러 작업의 가장 마지막에 실행됨.
     *
     * @author Moon
     */
    public void updatePartition() {
        log.info("Scheduled name is {}", "updatePartition");
        SimpleDateFormat format = new SimpleDateFormat ( "yyyy-MM-d");

        Calendar cal = Calendar.getInstance();
        Calendar cal_past = Calendar.getInstance();
        cal_past.add(Calendar.DAY_OF_MONTH,-15);

        String format_today = format.format(cal.getTime());
        String format_past = format.format(cal_past.getTime());

        Date startDate = null;
        try {
            startDate = format.parse(format_past);
        } catch (java.text.ParseException e) {
            e.printStackTrace();
        }

        Date endDate = null;
        try {
            endDate = format.parse(format_today);
        } catch (java.text.ParseException e) {
            e.printStackTrace();
        }

        ArrayList<String> dates = new ArrayList<String>();

        Date currentDate = startDate;

        while (currentDate.compareTo(endDate) <= 0){
            dates.add(format.format(currentDate));
            Calendar ca = Calendar.getInstance();
            ca.setTime(currentDate);
            ca.add(Calendar.DAY_OF_MONTH,1);
            currentDate = ca.getTime();
        }

        String dateArray = "p1,p2,p3,p4,p5,p6,p7,p8,p9,p10,p11,p12,p13,p14,p15,p16,p17,p18,p19,p20,p21,p22,p23,p24,p25,p26,p27,p28,p29,p30,p31";
        String newArr = "";
        int chk = 0;
        for(int j = 0 ; j <dates.size(); j++){
            String[] date = dates.get(j).split("-");
            String getDate =  date[2];
            String thisDate = "p"+ getDate;

            if(getDate.equals("1")){
                thisDate = thisDate+",";
                newArr = dateArray.replace(thisDate,",");
                dateArray = newArr;
                chk = -1;
            }else if(getDate.equals("31")){
                thisDate=","+thisDate;
                newArr = dateArray.replace(thisDate,"");
                dateArray = newArr;
            }else{
                thisDate=","+thisDate+",";
                newArr = dateArray.replace(thisDate,",");
                dateArray = newArr;
            }
        }
        if(chk != 0){
            newArr = dateArray.replaceFirst(",","");
        }
        System.out.println("DELETE PARTITION  :" + newArr);
        log.info("DELETE PARTITION  :" +  newArr);
        int updateBrandPartition = schedulerMapper.updateBrand(newArr);
        int updateTemplatePartition = schedulerMapper.updateTemplate(newArr);
        int updateChatbotPartition = schedulerMapper.updateChatbot(newArr);
        int updateTemplateFormPartition = schedulerMapper.updateTemplateForm(newArr);

        System.out.println("updateBrandPartition Return Value :  " + updateBrandPartition);
        System.out.println("updateTemplatePartition Return Value :  " + updateTemplatePartition);
        System.out.println("updateChatbotPartition Return Value :  " + updateChatbotPartition);
        System.out.println("updateTemplateFormPartition Return Value :  " + updateTemplateFormPartition);

        log.info("updateBrandPartition Return Value :  " + updateBrandPartition);
        log.info("updateTemplatePartition Return Value :  " + updateTemplatePartition);
        log.info("updateChatbotPartition Return Value :  " + updateChatbotPartition);
        log.info("updateTemplateFormPartition Return Value :  " + updateTemplateFormPartition);

        //schedulerMapper.updateBrandFile(newArr);
        // schedulerMapper.updateTemplateBtn(dateArr2);
    }


    /**
     * TEMPLATE 관련하여 변동내용(insert, update, delete)정보 core file 생성
     *
     * @author Moon
     */
    public void coreFileTemplate(int date){
        log.info("Scheduled name is {}", "coreFileTemplate");
        Map<Object, String> map = new HashMap<>();

        // 1. 기존 데이터 삭제 (어제 not in 오늘) -deleteFile 어제 있었는데 오늘 없어
        String deleteTemplate = templateService.selectDelTmpl(1);
        if(deleteTemplate != null){
            int deleteResult =  util.coreFile("TEMPLATE", deleteTemplate, "delete");
            log.info("deleteTemplate ID List : {}",deleteTemplate);
            if(deleteResult == 100){
                log.info("[TEMPLATE] deleteFile create SUCCESS");
            }else{
                log.info("[TEMPLATE] deleteFile create FAIL");
            }
        }else{
            log.info("deleteTemplate ID List : {}", "null");
        }

        // 2. 새로운 데이터 등록 (오늘 not in 어제) - insertFile
        String newTemplate = templateService.selectNewTmpl(date);
        if(newTemplate != null){
            int insertResult =  util.coreFile("TEMPLATE", newTemplate, "insert");
            log.info("insertTemplate ID List : {}",newTemplate);
            if(insertResult == 100){
                log.info("[TEMPLATE] insertFile create SUCCESS");
            }else{
                log.info("[TEMPLATE] insertFile create FAIL");
            }
        }else{
            log.info("insertTemplate ID List : {}", "null");
        }


        String notInId = null;
        if(deleteTemplate != null && newTemplate != null){
            notInId = "'"+deleteTemplate.replace(",","','") +"','"+newTemplate.replace(",","','")+"'";
        }else if(deleteTemplate != null && newTemplate == null){
            notInId = "'"+deleteTemplate.replace(",","','")+"'";
        }else if(deleteTemplate == null && newTemplate != null) {
            notInId = "'" + newTemplate.replace(",", "','") + "'";
        }

        map.put("partition",util.getTodayPartition(0));
        map.put("notInId",notInId);
        List<Map<Object, String>> todayTemplatateList = templateService.todayTemplateList(map);

        String idArr = "";
        map.put("partition",util.getTodayPartition(date));
        map.put("dateChk",null);
        Set<String> setYesterTmplList = templateService.yesterdayTemplate(map);

        if(setYesterTmplList.size() > 0){
            for (Map<Object, String> objectStringMap : todayTemplatateList) {
                String thisColAll = objectStringMap.get("colAll");
                String thisId = objectStringMap.get("MESSAGEBASE_ID");
                if (setYesterTmplList.remove(thisColAll)) {

                } else {
                    if (idArr.equals("")) {
                        idArr += thisId;
                    } else {
                        idArr += "," + thisId;
                    }
                }
            }
            if(!idArr.equals("")){
                int updateResult =  util.coreFile("TEMPLATE", idArr, "update");

                log.info("updateTemplate ID List : {}", idArr);
                if(updateResult == 100){
                    log.info("[TEMPLATE] updateFile create SUCCESS");
                }else{
                    log.info("[TEMPLATE] updateFile create FAIL");
                }
            }else {
                log.info("updateTemplate ID List : {}", "null");
            }
        }
    }


    /**
     * CHATBOT 관련하여 변동내용(insert, update, delete)정보 core file 생성
     *
     * @author Moon
     */
    public void coreFileChatbot(int date){
        log.info("Scheduled name is {}", "coreFileChatbot");
        Map<Object, String> map = new HashMap<>();

        // 1. 기존 데이터 삭제 (어제 not in 오늘) -deleteFile
        String deleteChat = chatbotService.selectDelChat(1);
        if(deleteChat != null){
            int deleteResult =  util.coreFile("CHATBOT", deleteChat, "delete");
            log.info("deleteCHATBOT ID List : {}",deleteChat);
            if(deleteResult == 100){
                log.info("[CHATBOT] deleteFile create SUCC0ESS");
            }else{
                log.info("[CHATBOT] deleteFile create FAIL");
            }
        }else{
            log.info("deleteCHATBOT ID List : {}", "null");
        }

        // 2. 새로운 데이터 등록 (오늘 not in 어제) - insertFile
        String newChat = chatbotService.selectNewChat(date);
        if(newChat != null){
            int insertResult =  util.coreFile("CHATBOT", newChat, "insert");
            log.info("insertCHATBOT ID List : {}",newChat);
            if(insertResult == 100){
                log.info("[CHATBOT] insertFile create SUCCESS");
            }else{
                log.info("[CHATBOT] insertFile create FAIL");
            }
        }else{
            log.info("insertCHATBOT ID List : {}", "null");
        }

        String notInId =null;
        if(deleteChat != null && newChat != null){
            notInId = "'"+deleteChat.replace(",","','") +"','"+newChat.replace(",","','")+"'";
        }else if(deleteChat != null && newChat == null){
            notInId = "'"+deleteChat.replace(",","','")+"'";
        }else if(deleteChat == null && newChat != null) {
            notInId = "'" + newChat.replace(",", "','") + "'";
        }

        map.put("partition",util.getTodayPartition(0));
        map.put("notInId",notInId);
        List<Map<Object, String>> todayChatList = chatbotService.todayChatList(map);

        String idArr = "";
        map.put("partition",util.getTodayPartition(date));
        map.put("dateChk",null);
        Set<String> setYesterCgatList = chatbotService.yesterdayChat(map);

        if(setYesterCgatList.size() > 0){
            for (Map<Object, String> objectStringMap : todayChatList) {
                String thisColAll = objectStringMap.get("colAll");
                String thisId = objectStringMap.get("CHATBOT_ID");
                if (setYesterCgatList.remove(thisColAll)) {

                } else {
                    if (idArr.equals("")) {
                        idArr += thisId;
                    } else {
                        idArr += "," + thisId;
                    }
                }
            }

            if(!idArr.equals("")){
                int updateResult =  util.coreFile("CHATBOT", idArr, "update");

                log.info("updateCHATBOT ID List : {}", idArr);
                if(updateResult == 100){
                    log.info("[CHATBOT] updateFile create SUCCESS");
                }else{
                    log.info("[CHATBOT] updateFile create FAIL");
                }
            }else {
                log.info("updateCHATBOT ID List : {}", "null");
            }
        }
    }


    /**
     * TEMPLATE_FORM 관련하여 변동내용(insert, update, delete)정보 core file 생성
     *
     * @author Moon
     */
    public void coreFileTemplateForm(int date){
        log.info("Scheduled name is {}", "coreFileTemplateForm");
        Map<Object, String> map = new HashMap<>();

        // 1. 기존 데이터 삭제 (어제 not in 오늘) -deleteFile
        String deleteTmplForm = templateService.selectDelTmplForm(1);
        if(deleteTmplForm != null){
            int deleteResult =  util.coreFile("TEMPLATE_FORM", deleteTmplForm, "delete");
            log.info("deleteTEMPLATE_FORM ID List : {}",deleteTmplForm);
            if(deleteResult == 100){
                log.info("[TEMPLATE_FORM] deleteFile create SUCC0ESS");
            }else{
                log.info("[TEMPLATE_FORM] deleteFile create FAIL");
            }
        }else{
            log.info("deleteTEMPLATE_FORM ID List : {}", "null");
        }

        // 2. 새로운 데이터 등록 (오늘 not in 어제) - insertFile
        String newTmplForm = templateService.selectNewTmplForm(date);
        if(newTmplForm != null){
            int insertResult =  util.coreFile("TEMPLATE_FORM", newTmplForm, "insert");
            log.info("insertTEMPLATE_FORM ID List : {}",newTmplForm);
            if(insertResult == 100){
                log.info("[TEMPLATE_FORM] insertFile create SUCCESS");
            }else{
                log.info("[TEMPLATE_FORM] insertFile create FAIL");
            }
        }else{
            log.info("insertTEMPLATE_FORM ID List : {}", "null");
        }

        String notInId =null;
        if(deleteTmplForm != null && newTmplForm != null){//deleteFile/insertFile모두 데이터가 있을때
            notInId = "'"+deleteTmplForm.replace(",","','") +"','"+newTmplForm.replace(",","','")+"'";
        }else if(deleteTmplForm != null && newTmplForm == null){
            notInId = "'"+deleteTmplForm.replace(",","','")+"'";
        }else if(deleteTmplForm == null && newTmplForm != null) {
            notInId = "'" + newTmplForm.replace(",", "','") + "'";
        }

        map.put("partition",util.getTodayPartition(0));
        map.put("notInId",notInId);
        List<Map<Object, String>> todayChatList = templateService.todayTemplateFormList(map);

        String idArr = "";
        map.put("partition",util.getTodayPartition(date));
        map.put("dateChk",null);
        Set<String> setYesterCgatList = templateService.yesterdayTemplateForm(map);

        if(setYesterCgatList.size() > 0){
            for (Map<Object, String> objectStringMap : todayChatList) {
                String thisColAll = objectStringMap.get("colAll");
                String thisId = objectStringMap.get("FORM_ID");
                if (setYesterCgatList.remove(thisColAll)) {

                } else {
                    if (idArr == "") {
                        idArr += thisId;
                    } else {
                        idArr += "," + thisId;
                    }
                }
            }

            if(!idArr.equals("")){
                int updateResult =  util.coreFile("TEMPLATE_FORM", idArr, "update");

                log.info("updateTEMPLATE_FORM ID List : {}", idArr);
                if(updateResult == 100){
                    log.info("[TEMPLATE_FORM] updateFile create SUCCESS");
                }else{
                    log.info("[TEMPLATE_FORM] updateFile create FAIL");
                }
            }else{
                log.info("updateTEMPLATE_FORM ID List : {}", "null");
            }
        }
    }


    /**
     * TEMPLATE 관련하여 변동내용(insert, update, delete)정보 core file 생성
     *
     * @author Moon
     */
    public void coreFileTemplateBtn(int date){
        log.info("Scheduled name is {}", "coreFileTemplateBtn");
        Map<Object, String> map = new HashMap<>();

        // 1. 기존 데이터 삭제 (어제 not in 오늘) -deleteFile 어제 있었는데 오늘 없어
        String deleteTemplateBtn = templateService.selectDelTmplBtn(1);
        if(deleteTemplateBtn != null){
            int deleteResult =  util.coreFile("TEMPLATE_BTN", deleteTemplateBtn, "delete");
            log.info("deleteTemplateeBtn ID List : {}",deleteTemplateBtn);
            if(deleteResult == 100){
                log.info("[TEMPLATE_BTN] deleteFile create SUCCESS");
            }else{
                log.info("[TEMPLATE_BTN] deleteFile create FAIL");
            }
        }else{
            log.info("deleteTemplateBtn ID List : {}", "null");
        }

        // 2. 새로운 데이터 등록 (오늘 not in 어제) - insertFile
        String newTemplateBtn = templateService.selectNewTmplBtn(date);
        if(newTemplateBtn != null){
            int insertResult =  util.coreFile("TEMPLATE_BTN", newTemplateBtn, "insert");
            log.info("insertTemplateBtn ID List : {}",newTemplateBtn);
            if(insertResult == 100){
                log.info("[TEMPLATE_BTN] insertFile create SUCCESS");
            }else{
                log.info("[TEMPLATE_BTN] insertFile create FAIL");
            }
        }else{
            log.info("insertTemplateBtn ID List : {}", "null");
        }


        String notInId = null;
        if(deleteTemplateBtn != null && newTemplateBtn != null){
            notInId = "'"+deleteTemplateBtn.replace(",","','") +"','"+newTemplateBtn.replace(",","','")+"'";
        }else if(deleteTemplateBtn != null && newTemplateBtn == null){
            notInId = "'"+deleteTemplateBtn.replace(",","','")+"'";
        }else if(deleteTemplateBtn == null && newTemplateBtn != null) {
            notInId = "'" + newTemplateBtn.replace(",", "','") + "'";
        }

        map.put("partition",util.getTodayPartition(0));
        map.put("notInId",notInId);
        map.put("del_yn" ,"1");
        map.put("defChk","Y");
        List<Map<String, Object>> todayTemplatateList = templateService.todayTemplateBtnList(map);

        String idArr = "";
        map.put("partition",util.getTodayPartition(date));
        map.put("dateChk",null);
        Set<String> setYesterTmplList = templateService.yesterdayTemplateBtn(map);

        String beforeId = "";
        if(setYesterTmplList.size() > 0){
            for (Map<String, Object> stringObjectMap : todayTemplatateList) {
                String thisColAll = stringObjectMap.get("DEL_YN").toString();
                String thisId = stringObjectMap.get("MESSAGEBASE_ID").toString();
                if (setYesterTmplList.remove(thisColAll)) {

                } else {
                    if (!beforeId.equals(thisId)) {
                        if (idArr.equals("")) {
                            idArr += thisId;
                        } else {
                            idArr += "," + thisId;
                        }
                    }
                }
            }
            if(!idArr.equals("")){
                int updateResult =  util.coreFile("TEMPLATE_BTN", idArr, "update");
                log.info("updateTemplate ID List : {}", idArr);
                if(updateResult == 100){
                    log.info("[TEMPLATE_BTN] updateFile create SUCCESS");
                }else{
                    log.info("[TEMPLATE_BTN] updateFile create FAIL");
                }
            }
        }
    }


    public int selectBtnMaxIdx(){
        return schedulerMapper.selectBtnMaxIdx();
    }


    public void dataGetFile(String fileName){

        File file = new File(Constants.PATH_FILE_ROOT + fileName);
        try {
            BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file.getPath()), StandardCharsets.UTF_8));
            boolean isExists = file.exists();
            if(isExists) {
                log.info("find the file! file name is : "+ fileName);
                System.out.println("find the file! file name is : "+ fileName);
            }else{
                log.info("No, there is not a no file! file name is : "+ fileName);
                System.out.println("No, there is not a no file! file name is : "+ fileName);
            }
            br.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}


