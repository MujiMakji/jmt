package kr.co.gmgo.rcs.api.biz.service;


import kr.co.gmgo.rcs.api.biz.common.Constants;
import kr.co.gmgo.rcs.api.biz.controller.UtilController;
import kr.co.gmgo.rcs.api.biz.mapper.TemplateMapper;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Service
public class TemplateService {

    private Logger log = LoggerFactory.getLogger("log.rcsLog");

    @Autowired
    TemplateMapper templateMapper;

    @Autowired
    TemplateService templateService;

    @Autowired
    BrandService brandService;

    @Autowired
    UtilController util;


    private String MESSAGE_URL = Constants.BIZ_API+"messagebase/common";
    private String MESSAGE_DETAIL_URL = Constants.BIZ_API+"messagebase/common/";
    private String TEMPLATEFORM_URL = Constants.BIZ_API+"messagebase/messagebaseform?1=1";
    private String TEMPLATEFORM_DETAIL_URL = Constants.BIZ_API+"messagebase/messagebaseform/";


    /**
     * 등록 템플릿 리스트 DB insert
     * 스케줄러를 통해 자동 insert (매일 24시)
     *
     * (index_text, card_type, spec, product_code, agency_id,btn_idx 값들은
     * 'getBrandTemplateDetail'(템플릿 정보 상세 조회)을 통해 받아올 수 있음)
     * @param compareDate coreFile 생성시 비교할 이전 날짜 ex)date= 1 > 현재 insert하는 데이터와 어제 파티션의 데이터 비교하여 corefile생성
     * @param regDate 서버 재 구동하여 호출 시 넘겨주는 날짜 값으로 해당 값이 null이 아니면 등록날짜에 해당 값이 들어간다.
     *                22시-24시 사이에 재구동하여 호출 시 오늘로부터 내일날짜값이 넘어오고
     *                그 외의 시간에는 오늘날짜를 넘겨주고있음.
     * @author Moon
     * @exception IOException
     * @exception ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */

    public int getTemplateList(String compareDate, String regDate) throws ParseException, IOException{
        log.info("Call Template API");
        int returnVal = -99;

        boolean duplicate = util.compareDate(regDate);
        String getToken = util.getTokenVal();
        String getDate = UtilController.getDate();
        String fileNameTemplate = "templateList_"+getDate+".txt";
        File fileTem = new File(Constants.PATH_FILE_ROOT+fileNameTemplate);
        BufferedWriter fwTem = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileTem.getPath()), "UTF-8"));

        if(fileTem.exists()){
            fileTem.createNewFile();
        }

        //List<Map<String,String>> btnInsertList = new ArrayList<>();
        //int btnInsertCnt = 0;

        int chkNum = 0;
        Map<String,String> brData =  brandService.selectBrandIdList();
        Map<Object, String> map = new HashMap<>();
        Set<String> setYesterTmplList = null;
        if(compareDate.equals("0")){
            map.put("partition",util.getTodayPartition(0));
            map.put("notInId",null);
            map.put("del_yn" ,"1");
            map.put("dateChk","0");
            setYesterTmplList = templateService.yesterdayTemplate(map);
        }
        if(brData != null){
            List<String> idArr = Arrays.asList(brData.get("brandId").split(","));

            for (int i = 0; idArr.size() > i; i++) {
                String brandId = idArr.get(i);
                String brand_key = brandService.selectBrandKey(brandId);
                String getUrl =  Constants.BIZ_API+"brand/" + brandId + "/messagebase";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("authorization", getToken);
                headers.set("x-rcs-brandkey", brand_key);
                HttpEntity entity = new HttpEntity(headers);

                RestTemplate rest = new RestTemplate();
                ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET, entity, String.class);

                if(result.getStatusCodeValue() == 200){
                    String body = result.getBody();

                    JSONParser jsonParser = new JSONParser();

                    Object obj = jsonParser.parse(body);
                    JSONObject jsonObj = (JSONObject) obj;
                    String resultObj = jsonObj.get("result").toString();

                    obj = jsonParser.parse(resultObj);
                    jsonObj = (JSONObject) obj;
                    JSONArray templateList = (JSONArray) jsonObj.get("tmplts");


                    for (int j = 0; templateList.size() > j; j++) {
                        JSONObject thisTmpt = (JSONObject) templateList.get(j);

                        Map<Object,String> insertTemplate = formatTemplateList(thisTmpt);
                        log.info("insertTemplate {} :", insertTemplate);

                        int chkCnt;
                        if(!duplicate){
                            chkCnt  =0;
                        }else{
                            chkCnt =templateService.selectTemplateId(insertTemplate.get("messagebase_id"));
                        }

                       /* if(insertTemplate.get("approval_result").equals("1")){
                            Map<String,String> btnInfo =new HashMap<>();
                            btnInfo.put("brandId",insertTemplate.get("brand_id"));
                            btnInfo.put("messagebaseId",insertTemplate.get("messagebase_id"));
                            btnInsertList.add(btnInsertCnt,btnInfo);
                            btnInsertCnt++;
                        }*/

                        if(chkCnt == 0){
                            Map<Object, String> writeTxt = UtilController.writeTxt(insertTemplate);
                            String colList = writeTxt.get("colListBtn");
                            String valList = writeTxt.get("valListBtn");

                            if(chkNum == 0 && j == 0 && i == 0){
                                fwTem.write(colList);
                                fwTem.write(valList);
                                chkNum += 1;
                            }else{
                                fwTem.write(valList);
                            }

                            if(compareDate.equals("0")){
                                setYesterTmplList.remove(insertTemplate.get("messagebase_id"));
                                util.coreFile("TEMPLATE", insertTemplate.get("messagebase_id"), "insert");
                            }

                        }else{
                            String concat = insertTemplate.get("messagebase_form_id")+insertTemplate.get("brand_id")+insertTemplate.get("approval_result")+insertTemplate.get("status");

                            int chkDefaultData = templateMapper.selectTemplateUpdateChk(insertTemplate.get("messagebase_id"),concat,util.getTodayPartition(0));
                            if(compareDate.equals("0")){
                                setYesterTmplList.remove(insertTemplate.get("messagebase_id"));
                            }
                            if(chkDefaultData == 0){
                                util.coreFile("TEMPLATE", insertTemplate.get("messagebase_id"), "update");
                                updatetemplate(insertTemplate);
                            }

                        }
                    }
                }
            }

            fwTem.close();
            if(compareDate.equals("0")){
                Map<Object,String> delMap = new HashMap<>();
                if(setYesterTmplList != null && setYesterTmplList.size() > 0){
                    String messagebaseIdList = setYesterTmplList.toString().replace(",", "','").replace("[", "'").replace("]", "'").replaceAll(" ", "");
                    util.coreFile("TEMPLATE", (setYesterTmplList.toString()).replace("[", "").replace("]", "").replace(" ", ""), "delete");
                    delMap.put("dateChk", "0");
                    delMap.put("partition", util.getTodayPartition(0));
                    delMap.put("messagebaseId", messagebaseIdList);

                    int vla = templateMapper.updateTemplateDelete(delMap);
                }
            }

            String templatePath = Constants.PATH_FILE_ROOT+ fileNameTemplate;

            Map<String, Object> insertMap = new HashMap<>();
            insertMap.put("data",templatePath);
            insertMap.put("compareDate",compareDate);
            insertMap.put("regDate",regDate);
            int templateListCount = insertTemplateAuto(insertMap);

            /*if(btnInsertList.size() >0){
                //버튼 insesrt작업
                insertTemplateBtn(btnInsertList,date);
            }*/

            if(templateListCount >= 1) {
                log.info("[template_list] Insert Success");
                //INSERT성공시에만 해당 파일 삭제
                fileTem.delete();
                returnVal = 100;
                log.info("[template_list] returnVal : {}", returnVal);
            }else if(compareDate.equals("0")){
                fileTem.delete();
            }else{
                log.info("[template_list] Insert Fail or NULL");
                returnVal = -99;
                log.info("[template_list] returnVal : {}",returnVal);
            }
        }else{
            log.info("template Data empty");
            returnVal = -98;
            log.info("[template_list] returnVal : {}",returnVal);
        }
        return returnVal;
    }




    public int insertTemplateBtn(List<Map<String,String>> btnInsertList, String date){

        int returnCode = -99;
        String getToken = util.getTokenVal();
        btnInsertList.size();
        String getDate = UtilController.getDate();
        String fileTemplateBtn = "templateBtn_"+getDate+".txt";
        File fileTemBtn = new File(Constants.PATH_FILE_ROOT+fileTemplateBtn);
        BufferedWriter fwTem = null;
        try {
            fwTem = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileTemBtn.getPath()), StandardCharsets.UTF_8));
            if(fileTemBtn.exists()){
                fileTemBtn.createNewFile();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        String insertBtnList = "";
        String beforeId="";
        String updateIdList ="";
        Set<String> btnList = null;
        Map<Object, String> map = new HashMap<>();
        map.put("partition",util.getTodayPartition(0));
        btnList = templateService.getOldTmplBtnList(map);

        for(int i = 0; btnInsertList.size() > i; i++){
            Map<String, String> thisTemp = btnInsertList.get(i);
            String brandId = thisTemp.get("brandId");
            String messagebaseId = thisTemp.get("messagebaseId");

            int chkBtn = templateMapper.selectBtnChk(messagebaseId,util.getTodayPartition(0));


            String brandKey = brandService.selectBrandKey(brandId);
            String getUrl = Constants.BIZ_API+"brand/"+brandId+"/messagebase/"+messagebaseId;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization",getToken);
            headers.set("x-rcs-brandkey",brandKey.toString());
            HttpEntity entity = new HttpEntity(headers);

            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET,entity,String.class);
            Object bizResult = result.getStatusCodeValue();
            Map<String,String> returnVal = new HashMap<>();

            if(result.getStatusCodeValue() == 200) {
                String body = result.getBody();

                JSONParser jsonParser = new JSONParser();

                Object obj = null;
                try {
                    obj = jsonParser.parse(body);
                    JSONObject jsonObj = (JSONObject) obj;
                    String resultObj = jsonObj.get("result").toString();

                    obj = jsonParser.parse(resultObj);
                    jsonObj = (JSONObject) obj;
                    resultObj = jsonObj.get("formattedString").toString();

                    obj = jsonParser.parse(resultObj);
                    jsonObj = (JSONObject) obj;
                    resultObj = jsonObj.get("RCSMessage").toString();

                    obj = jsonParser.parse(resultObj);
                    jsonObj = (JSONObject) obj;
                    resultObj = jsonObj.get("openrichcardMessage").toString();

                    obj = jsonParser.parse(resultObj);
                    jsonObj = (JSONObject) obj;

                    Iterator<String> keys = (jsonObj).keySet().iterator();
                    while(keys.hasNext()) {
                        String thisKey = keys.next();
                        if (thisKey.equals("suggestions")) {//버튼 여부

                            if(btnList.size()>0 && btnList.remove(messagebaseId)) {//update

                                if (updateIdList.equals("")) {
                                    updateIdList += messagebaseId;
                                } else {
                                    updateIdList += "," + messagebaseId;
                                }
                            }else{
                                resultObj = jsonObj.get("suggestions").toString();

                                JSONArray actionArr =(JSONArray) jsonObj.get("suggestions");
                                int actionArrSize = actionArr.size();
                                for(int j = 0 ; actionArrSize > j; j++){
                                    JSONObject thisActionOb = (JSONObject) actionArr.get(j);
                                    String thisAction = thisActionOb.get("action").toString();
                                    Object thisObj = jsonParser.parse(thisAction);
                                    thisActionOb = (JSONObject)thisObj;
                                    Map<Object, String> writeTxt = new HashMap<>();
                                    Iterator<String> keysSecond = (thisActionOb).keySet().iterator();

                                    Map<Object, String> insertMap = new HashMap<>();
                                    String btn_type ="";
                                    String btn_name ="";
                                    int btn_num = 0;
                                    String btn_link = "";


                                    while(keysSecond.hasNext()){
                                        String thisKeySecond = keysSecond.next();
                                        if(thisKeySecond.equals("clipboardAction")){//1.복사하기
                                            btn_type ="clipboardAction";
                                            btn_name =(thisActionOb).get("displayText").toString();
                                            btn_num = j+1;


                                            String clipboardArr =((JSONObject) thisObj).get("clipboardAction").toString();
                                            Object linkObj = jsonParser.parse(clipboardArr);
                                            JSONObject linkActionOb = (JSONObject)linkObj;
                                            btn_link =((JSONObject) linkActionOb).get("copyToClipboard").toString();
                                            linkObj = jsonParser.parse(btn_link);
                                            linkActionOb = (JSONObject)linkObj;
                                            btn_link =((JSONObject) linkActionOb).get("text").toString();

                                        }else if(thisKeySecond.equals("dialerAction")){//2.전화걸기
                                            btn_type ="dialerAction";
                                            btn_name =(thisActionOb).get("displayText").toString();
                                            btn_num = j+1;


                                            String dialerArr =((JSONObject) thisObj).get("dialerAction").toString();
                                            Object dialerObj = jsonParser.parse(dialerArr);
                                            JSONObject dialerActionOb = (JSONObject)dialerObj;
                                            btn_link =(dialerActionOb).get("dialPhoneNumber").toString();
                                            dialerObj = jsonParser.parse(btn_link);
                                            dialerActionOb = (JSONObject)dialerObj;
                                            btn_link =(dialerActionOb).get("phoneNumber").toString();


                                        }else if(thisKeySecond.equals("mapAction")) {//3.지도보여주기(좌표, 쿼리)
                                            btn_type ="mapAction";
                                            btn_name =(thisActionOb).get("displayText").toString();
                                            btn_num = j+1;

                                            String mapArr =((JSONObject) thisObj).get("mapAction").toString();
                                            Object mapObj = jsonParser.parse(mapArr);
                                            JSONObject mapActionOb = (JSONObject)mapObj;
                                            btn_link =(mapActionOb).get("showLocation").toString();
                                            mapObj = jsonParser.parse(btn_link);
                                            mapActionOb = (JSONObject)mapObj;
                                            btn_link =(mapActionOb).get("fallbackUrl").toString();


                                        }else if(thisKeySecond.equals("urlAction")) {//4.URL연결
                                            btn_type ="urlAction";
                                            btn_name =(thisActionOb).get("displayText").toString();
                                            btn_num = j+1;

                                            String mapArr =((JSONObject) thisObj).get("urlAction").toString();
                                            Object mapObj = jsonParser.parse(mapArr);
                                            JSONObject mapActionOb = (JSONObject)mapObj;
                                            btn_link =(mapActionOb).get("openUrl").toString();
                                            mapObj = jsonParser.parse(btn_link);
                                            mapActionOb = (JSONObject)mapObj;
                                            btn_link =(mapActionOb).get("url").toString();
                                        }
                                    }
                                    insertMap.put("btn_type",btn_type);
                                    insertMap.put("btn_link",btn_link);
                                    insertMap.put("btn_name",btn_name);
                                    insertMap.put("messagebase_id",messagebaseId);
                                    insertMap.put("btn_num", String.valueOf(btn_num));
                                    insertMap.put("brand_id", String.valueOf(brandId));


                                    if(!btn_type.equals("")){
                                        if(!beforeId.equals(messagebaseId)){
                                            if(insertBtnList.equals("")){
                                                insertBtnList = messagebaseId;
                                                beforeId = messagebaseId;
                                            }else{
                                                insertBtnList +=","+messagebaseId;
                                                beforeId = messagebaseId;
                                            }
                                        }

                                        writeTxt = UtilController.writeTxt(insertMap);
                                        String colList = writeTxt.get("colListBtn");
                                        String valList = writeTxt.get("valListBtn");
                                        try {
                                            if(j == 0 && i == 0){
                                                fwTem.write(colList);
                                                fwTem.write("\r\n");
                                                fwTem.write(valList);
                                            }else{
                                                fwTem.write("\r\n");
                                                fwTem.write(valList);
                                            }

                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                }
                            }
                        }

                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            fwTem.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String templateBtnPath = Constants.PATH_FILE_ROOT+ fileTemplateBtn;
        int templateBtnCount =insertTemplateBtn(templateBtnPath);

        if(!updateIdList.equals("")){
            Map<Object,String> btnMap = new HashMap<>();

            btnMap.put("messagebase_id","'"+updateIdList.replace(",","','")+"'");
            btnMap.put("partition", util.getTodayPartition(0));
            int updateBtnCount = updateTemplateBtnList(btnMap);
            if(updateBtnCount>0){
                util.coreFile("TEMPLATE_BTN",updateIdList,"update");
            }
        }
        if (date.equals("0") && templateBtnCount > 0) {
            util.coreFile("TEMPLATE_BTN",insertBtnList,"insert");
            returnCode = 100;
        }
        return returnCode;
    }


    //템플릿 리스트 포맷
    public Map<Object,String> formatTemplateList(JSONObject templateJson){
        Map<Object,String> temList = new HashMap<>();

        if(templateJson.get("approvalReason") != null && !templateJson.get("approvalReason").equals("") && !templateJson.get("approvalReason").equals("null")){
            temList.put("approval_reason", templateJson.get("approvalReason").toString());
        }else{
            String strNull = null;
            temList.put("approval_reason", null);
        }

        temList.put("messagebase_id",templateJson.get("messagebaseId").toString());
        temList.put("template_name",templateJson.get("tmpltName").toString());
        temList.put("messagebase_form_id",templateJson.get("messagebaseformId").toString());
        temList.put("brand_id", templateJson.get("brandId").toString());


        if(templateJson.get("approvalDate") != null && templateJson.get("approvalDate") != "" ){
            temList.put("approval_dt", templateJson.get("approvalDate").toString().replace("T", " ").substring(0, 19));
        }else{
            temList.put("approval_dt",  null);
        }

        if(templateJson.get("registerDate") != null && templateJson.get("registerDate") != "" ){
            temList.put("template_reg_dt",templateJson.get("registerDate").toString().replace("T", " ").substring(0, 19));
        }else{
            temList.put("template_reg_dt", null);
        }

        if(templateJson.get("updateId") != null && templateJson.get("updateId") != "" ){
            temList.put("update_id", templateJson.get("updateId").toString());
        }else{
            temList.put("update_id", null);
        }

        if(templateJson.get("updateDate") != null && templateJson.get("updateDate") != "" ){
            temList.put("update_dt", templateJson.get("updateDate").toString().replace("T", " ").substring(0, 19));
        }else{
            temList.put("update_dt", null);
        }

        temList.put("reg_id",templateJson.get("registerId").toString());

        String approval_result = templateJson.get("approvalResult").toString();
        if(approval_result.equals("승인대기")){
            temList.put("approval_result", "0");
        }else if(approval_result.equals("승인")){
            temList.put("approval_result", "1");
        }else if(approval_result.equals("반려")){
            temList.put("approval_result", "2");
        }else if(approval_result.equals("검수완료")){
            temList.put("approval_result", "3");
        }else if(approval_result.equals("저장")){
            temList.put("approval_result", "4");
        }

        String status = templateJson.get("status").toString();

        if(status.equals("ready")){
            temList.put("status", "0");
        }else if(status.equals("pause")){
            temList.put("status", "1");
        }
        return temList;
    }

    /**
     * 등록 템플릿 양식 DB insert
     * 스케줄러를 통해 자동 insert (매일 24시)
     * @param compareDate coreFile 생성시 비교할 이전 날짜 ex)date= 1 > 현재 insert하는 데이터와 어제 파티션의 데이터 비교하여 corefile생성
     * @param regDate 서버 재 구동하여 호출 시 넘겨주는 날짜 값으로 해당 값이 null이 아니면 등록날짜에 해당 값이 들어간다.
     *                22시-24시 사이에 재구동하여 호출 시 오늘로부터 내일날짜값이 넘어오고
     *                그 외의 시간에는 오늘날짜를 넘겨주고있음.
     *                (null로 넘어올 경우 "empty"로 치환해주고 있음)
     *
     * @author Moon
     * @exception IOException
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    public int getTemplateForm (String compareDate, String regDate) throws IOException, ParseException {
        log.info("Call TemplateForm API");
        int returnValTmpl = -99;
        int returnVal = -99;
        int returnAddVal = -99;
        String getToken = util.getTokenVal();
        String getFromId = getFromId(getToken);
        boolean duplicate = util.compareDate(regDate);
        //1.cell,description insert
        List<String> formList = Arrays.asList(getFromId.split(","));
        int getTemplatFormCnt = insertFromDeatil(getToken, formList, compareDate, duplicate, regDate);
        if(formList.size() == getTemplatFormCnt){
            System.out.println("Template Form insert Success");
            returnValTmpl =100;
            log.info("Template Form insert Success");
            log.info("[Template Form] returnVal : {}",returnValTmpl);
        }else{
            returnValTmpl =-99;
            log.info("[Template Form] returnVal : {}",returnValTmpl);
        }

        //2.SMS,LMS,MMs insert
        String getMessagebaseId = getMessagebaseId(getToken);

        List<String> baseIdList = Arrays.asList(getMessagebaseId.split(","));

        int insertMessageCnt = insertMessageForm(getToken, baseIdList, compareDate, duplicate, regDate);
        if(baseIdList.size() == insertMessageCnt){
            System.out.println("insertMessage Form insert성공");
            returnVal =100;
            log.info("insertMessage insert Success");
            log.info("[Message Form] returnVal : {}",returnVal);
        }else{
            returnVal =-99;
            log.info("[Message Form] returnVal : {}",returnVal);
        }
        if((returnValTmpl + returnVal) == 200){
            returnAddVal = 100;
        }else{
            returnAddVal = -99;
        }

        return returnAddVal;
    }

    private String getFromId (String getToken) throws ParseException {
        String getUrl = TEMPLATEFORM_URL;
        String formId ="";
        Map<Object,String> formList= new HashMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", getToken);
        HttpEntity entity = new HttpEntity(headers);

        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET,entity,String.class);

        String body = result.getBody();

        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(body);
        JSONObject jsonObject = (JSONObject) obj;
        String resultObj =jsonObject.get("result").toString();
        resultObj = resultObj.replace("[{","").replace("}]","").replace("},{","_!@#split!@#_");
        List<String> items = Arrays.asList(resultObj.split("_!@#split!@#_"));
        int itemLen = items.size();
        JSONArray ja = (JSONArray) jsonObject.get("result");
        for(int i = 0; ja.size()> i ; i++){
            JSONObject getItem = (JSONObject) ja.get(i);
            if(i == 0){
                formId = getItem.get("messagebaseformId").toString();
            }else {
                formId += "," + getItem.get("messagebaseformId").toString();
            }
        }
        return formId;
    }

    /**
     * 템플릿 양식(CELL, DESCRIPTION) DB insert
     * 스케줄러를 통해 자동 insert (매일 24시)
     *
     * @param getToken API 호출 시 필요한 토큰값
     * @param formList 탬플릿 양식의 ID값 list
     * @param compareDate coreFile 생성시 비교할 이전 날짜 ex)date= 1 > 현재 insert하는 데이터와 어제 파티션의 데이터 비교하여 corefile생성
     * @param regDate 서버 재 구동하여 호출 시 넘겨주는 날짜 값으로 해당 값이 null이 아니면 등록날짜에 해당 값이 들어간다.
     *                22시-24시 사이에 재구동하여 호출 시 오늘로부터 내일날짜값이 넘어오고
     *                그 외의 시간에는 오늘날짜를 넘겨주고있음.
     * @author Moon
     * @exception IOException
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    private int insertFromDeatil(String getToken, List<String> formList, String compareDate, boolean duplicate, String regDate) throws ParseException, IOException {
        String getDate = UtilController.getDate();
        String formTemplate = "formTemplate_"+getDate+".txt";
        File fileTemForm = new File(Constants.PATH_FILE_ROOT+ formTemplate);

        if (fileTemForm.exists()) {
            fileTemForm.createNewFile();
        }
        BufferedWriter fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileTemForm.getPath()), StandardCharsets.UTF_8));
        int chkNum = 0;
        int insertCnt = 0;
        Map<Object,String> insertForm = new HashMap<>();

        Set<String> setYesterCgatList = null;
        if(compareDate != null && compareDate.equals("0")){
            Map<Object, String> map = new HashMap<>();
            map.put("partition",util.getTodayPartition(0));
            map.put("notInId",null);
            map.put("dateChk","0");
            map.put("product","4");
            setYesterCgatList = templateService.yesterdayTemplateForm(map);
        }

        log.info("================baseIdList.size()"+formList.size()+"======================================");
        for (String formId : formList) {
            String getUrl = TEMPLATEFORM_DETAIL_URL + formId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", getToken);
            HttpEntity entity = new HttpEntity(headers);

            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET, entity, String.class);

            if (result.getStatusCodeValue() == 200) {
                String body = result.getBody();
                JSONParser jsonParser = new JSONParser();

                Object obj = jsonParser.parse(body);
                JSONObject jsonObj = (JSONObject) obj;
                String resultObj = jsonObj.get("result").toString();

                obj = jsonParser.parse(resultObj);
                jsonObj = (JSONObject) obj;

                if (jsonObj.get("productCode").toString().equals("tmplt")) {
                    insertForm.put("product_code", "4");
                } else {
                    insertForm.put("product_code", "0");
                }

                insertForm.put("card_type", jsonObj.get("cardType").toString());
                insertForm.put("form_id", formId);
                insertForm.put("media", null);
                insertForm.put("title", null);
                insertForm.put("description", null);
                insertForm.put("ad_header_allowed", null);


                if (!insertForm.get("card_type").equals("cell")) {
                    Iterator<String> keys = ((JSONObject) obj).keySet().iterator();
                    while (keys.hasNext()) {
                        String thisKey = keys.next();
                        if (thisKey.equals("params")) {//param,isMandatorymedia,verification chk
                            String paramObject = jsonObj.get("params").toString();
                            Object paramObj = jsonParser.parse(paramObject);
                            List<Object> paramList = (List<Object>) paramObj;

                            for (Object thisParam : paramList) {
                                Iterator<String> pkList = ((JSONObject) thisParam).keySet().iterator();
                                while (pkList.hasNext()) {
                                    String paramKey = pkList.next();

                                    if (paramKey.equals("param")) {
                                        String param = ((JSONObject) thisParam).get("param").toString();

                                        if (param.equals("description")) {
                                            String mandatory = ((JSONObject) thisParam).get("isMandatory").toString();
                                            insertForm.put("description", mandatory);
                                        }

                                        if (param.equals("media")) {
                                            String mandatory = ((JSONObject) thisParam).get("isMandatory").toString();
                                            insertForm.put("media", mandatory);
                                        }

                                        if (param.equals("title")) {
                                            String mandatory = ((JSONObject) thisParam).get("isMandatory").toString();
                                            insertForm.put("title", mandatory);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                String policyInfo = jsonObj.get("policyInfo").toString();
                //cardCount,maxTitleSize,maxDescriptionSize,maxButtonCount,buttonsAllowed,adHeaderAllowed,adBodyAllowed

                Object policyObj = jsonParser.parse(policyInfo);
                JSONObject policyJson = (JSONObject) policyObj;
                insertForm.put("card_cnt", policyJson.get("cardCount").toString());
                insertForm.put("description_size", policyJson.get("maxDescriptionSize").toString());
                insertForm.put("buttons_allowed", policyJson.get("buttonsAllowed").toString());
                insertForm.put("ad_header_allowed", policyJson.get("adHeaderAllowed").toString());
                insertForm.put("ad_body_allowed", policyJson.get("adBodyAllowed").toString());
                insertForm.put("max_btn_cnt", policyJson.get("maxButtonCount").toString());


                String guideInfo = jsonObj.get("guideInfo").toString();
                //maxLineCount,maxDisplayText,maxDescriptionSize
                Object guideObj = jsonParser.parse(guideInfo);
                JSONObject guideJson = (JSONObject) guideObj;

                insertForm.put("guide_line_cnt", guideJson.get("maxLineCount").toString());
                insertForm.put("btn_name_size", guideJson.get("maxDisplayText").toString());
                insertForm.put("card_description_size", guideJson.get("maxDescriptionSize").toString());

                String buttons_allowed = insertForm.get("buttons_allowed");
                if (buttons_allowed.equals("true")) {
                    insertForm.put("buttons_allowed", "0");
                } else if (buttons_allowed.equals("false")) {
                    insertForm.put("buttons_allowed", "1");
                }

                String ad_header_allowed = insertForm.get("ad_header_allowed");
                if (ad_header_allowed.equals("true")) {
                    insertForm.put("ad_header_allowed", "0");
                } else if (ad_header_allowed.equals("false")) {
                    insertForm.put("ad_header_allowed", "1");
                }

                String ad_body_allowed = insertForm.get("ad_body_allowed");
                if (ad_body_allowed.equals("true")) {
                    insertForm.put("ad_body_allowed", "0");
                } else if (ad_body_allowed.equals("false")) {
                    insertForm.put("ad_body_allowed", "1");
                }

                 /*cell 타입에서는 title, media, description이 아닌
                    cell1...cell11과같은 형식으로 데이터를 내려주기때문에
                    해당 key값을 체크하여 description에 값을 담아주고있음*/
                if (insertForm.get("card_type").equals("cell")) {
                    insertForm.put("description", "0");
                    insertForm.put("media", "0");
                    insertForm.put("title", "0");
                } else {
                    String media = insertForm.get("media");
                    if (media != null) {
                        if (media.equals("false")) {
                            insertForm.put("media", "0");
                        } else {
                            insertForm.put("media", "1");
                        }
                    } else {
                        insertForm.put("media", "0");
                    }

                    String title = insertForm.get("title");
                    if (title != null) {
                        if (title.equals("false")) {
                            insertForm.put("title", "0");
                        } else {
                            insertForm.put("title", "1");
                        }
                    } else {
                        insertForm.put("title", "0");
                    }

                    String description = insertForm.get("description");
                    if (description != null) {
                        if (description.equals("false")) {
                            insertForm.put("description", "0");
                        } else {
                            insertForm.put("description", "1");
                        }
                    } else {
                        insertForm.put("description", "0");
                    }
                }
                insertForm.put("max_title_size", policyJson.get("maxTitleSize").toString());
            }

            int chkCnt;
            if (!duplicate) {
                chkCnt = 0;
            } else {
                chkCnt = templateService.selectTemplateFormId(formId);
            }

            if (chkCnt == 0) {
                Map<Object, String> writeTxt = UtilController.writeTxt(insertForm);
                String colListBtn = writeTxt.get("colListBtn");
                String valListBtn = writeTxt.get("valListBtn");

                if (chkNum == 0) {
                    fw.write(colListBtn);
                    fw.write(valListBtn);
                    chkNum += 1;
                } else {
                    fw.write(valListBtn);
                }
                if ("0".equals(compareDate)) {
                    util.coreFile("TEMPLATE_FORM", formId, "insert");
                    setYesterCgatList.remove(formId);
                }
            } else {
                String concat = insertForm.get("product_code") + insertForm.get("card_type") + insertForm.get("media")
                        + insertForm.get("title") + insertForm.get("description") + insertForm.get("card_cnt") + insertForm.get("approval_result") + insertForm.get("status")+ insertForm.get("max_title_size");
                log.info("=======concat" + concat);
                int chkDefaultData = templateMapper.selectTemplateFormUpdateChk(formId, concat, util.getTodayPartition(0));
                log.info("=======chkDefaultData" + chkDefaultData);
                if (compareDate.equals("0")) {
                    setYesterCgatList.remove(formId);
                }
                if (chkDefaultData == 0) {
                    util.coreFile("TEMPLATE_FORM", formId, "update");
                    templateService.updateTemplateForm(insertForm);
                }
            }
        }
        if(insertCnt == formList.size()){
            System.out.println("cell, description형식의 템플릿 insert 성공");
        }
        fw.close();


        if("0".equals(compareDate)){
            Map<Object,String> delMap = new HashMap<>();
            if(setYesterCgatList != null && setYesterCgatList.size() > 0){
                String messagebaseIdList = setYesterCgatList.toString().replace(",","','").replace("[","'").replace("]","'").replaceAll(" ","");
                util.coreFile("TEMPLATE_FORM", (setYesterCgatList.toString()).replace("[","").replace("]","").replace(" ",""), "delete");
                delMap.put("dateChk","0");
                delMap.put("partition",util.getTodayPartition(0));
                delMap.put("formId",messagebaseIdList);
                delMap.put("product","4");
                int vla = templateMapper.updateTemplateFormDelete(delMap);
                if(vla > 0){
                    log.info("[TEMPLATE_FORM UPDATE SUCCESS]");
                }else {
                    log.info("[TEMPLATE_FORM UPDATE FAIL] List : " , delMap);
                }
            }
        }

        String templatePath =Constants.PATH_FILE_ROOT + formTemplate;

        Map<String, Object> insertMap = new HashMap<>();
        insertMap.put("data",templatePath);//데이터 파일 경로
        insertMap.put("compareDate",compareDate);// corefile비교날짜
        insertMap.put("regDate",regDate);
        insertCnt = insertTemplateFormAuto(insertMap);

        if(insertCnt >= 1) {
            log.info("[TEMPLATE_FORM] Insert Success");
            //INSERT성공시에만 해당 파일 삭제
            fileTemForm.delete();
        }else if(compareDate.equals("0")){
            fileTemForm.delete();
        }else{
            log.info("[TEMPLATE_FORM] Insert Fail or NULL");
        }
        return insertCnt;
    }

    /**
     * messagebaseID 리스트 가져오기
     * *
     * @author Moon
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    private Map<Object, String> getmessagebaseId(String token, List<String> brandList) throws ParseException {
        String messagebaseIdArr = "replace";
        String brandArr = "replace";
        Map<Object, String> getData = new HashMap<>();
        for (String brandId : brandList) {
            String brandKey = brandService.selectBrandKey(brandId);
            String getUrl = Constants.BIZ_API + "brand/" + brandId + "/messagebase";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", token);
            headers.set("x-rcs-brandkey", brandKey);
            HttpEntity entity = new HttpEntity(headers);

            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET, entity, String.class);

            String body = result.getBody();

            JSONParser jsonParser = new JSONParser();
            Object obj = jsonParser.parse(body);
            JSONObject jsonObj = (JSONObject) obj;
            String resultObj = jsonObj.get("result").toString();

            obj = jsonParser.parse(resultObj);
            jsonObj = (JSONObject) obj;
            JSONArray templateList = (JSONArray) jsonObj.get("tmplts");

            for (Object o : templateList) {
                JSONObject thisTmlt = (JSONObject) o;
                messagebaseIdArr += "," + thisTmlt.get("messagebaseId");
                brandArr += "," + brandId;
            }
        }
        getData.put("messagebaseIdArr", messagebaseIdArr.replace("replace,", ""));
        getData.put("brandArr", brandArr.replace("replace,", ""));

        return getData;
    }

    /**
     * 템플릿 양식(SMS, LMS, MMS) DB insert
     * 스케줄러를 통해 자동 insert (매일 24시)
     * @param getToken API 호출 시 필요한 토큰값
     * @param baseIdList 탬플릿 양식의 ID값
     * @param compareDate coreFile 생성시 비교할 이전 날짜 ex)date= 1 > 현재 insert하는 데이터와 어제 파티션의 데이터 비교하여 corefile생성
     * @param regDate 서버 재 구동하여 호출 시 넘겨주는 날짜 값으로 해당 값이 null이 아니면 등록날짜에 해당 값이 들어간다.
     *                22시-24시 사이에 재구동하여 호출 시 오늘로부터 내일날짜값이 넘어오고
     *                그 외의 시간에는 오늘날짜를 넘겨주고있음.
     * @author Moon
     * @exception IOException
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    private int insertMessageForm (String getToken, List<String>baseIdList, String compareDate, boolean duplicate, String regDate) throws ParseException, IOException {
        String getDate = UtilController.getDate();
        String formBasic = "formBasic_"+getDate+".txt";
        File fileMsgForm = new File(Constants.PATH_FILE_ROOT + formBasic);

        if (fileMsgForm.exists()) {
            fileMsgForm.createNewFile();
        }
        BufferedWriter fw =  new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileMsgForm.getPath()), StandardCharsets.UTF_8));
        int chkNum = 0;
        int insertCnt = 0;
        Map<Object,String> insertForm = new HashMap<>();

        Set<String> setYesterChatList = null;
        if(compareDate.equals("0")){
            Map<Object, String> map = new HashMap<>();
            map.put("partition",util.getTodayPartition(0));
            map.put("notInId",null);
            map.put("dateChk","0");
            map.put("product","1");
            setYesterChatList = templateService.yesterdayTemplateForm(map);
        }

        /*log.info("================baseIdList.size()"+baseIdList.size()+"======================================");*/
        for(int i = 0 ; baseIdList.size()> i; i++){
            String msgbaseId = baseIdList.get(i);

            String getUrl = MESSAGE_DETAIL_URL + msgbaseId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization",getToken);
            HttpEntity entity = new HttpEntity(headers);

            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET,entity,String.class);

            if(result.getStatusCodeValue() == 200){
                String body = result.getBody();
                JSONParser jsonParser = new JSONParser();

                Object obj = jsonParser.parse(body);
                JSONObject jsonObj = (JSONObject) obj;
                String resultObj = jsonObj.get("result").toString();
                obj = jsonParser.parse(resultObj);
                jsonObj = (JSONObject) obj;

                insertForm.put("form_id",jsonObj.get("messagebaseformId").toString());
                insertForm.put("card_type",jsonObj.get("cardType").toString());
                insertForm.put("media",null);
                insertForm.put("title",null);
                insertForm.put("description",null);
                insertForm.put("product_code",jsonObj.get("productCode").toString());
                insertForm.put("status",jsonObj.get("status").toString());
                insertForm.put("approval_result",jsonObj.get("approvalResult").toString());
                insertForm.put("max_title_size", String.valueOf(0));

                Iterator<String> keys = ((JSONObject) obj).keySet().iterator();
                while(keys.hasNext()){
                    String thisKey = keys.next();
                    if(thisKey.equals("params")){
                        String paramObject = jsonObj.get("params").toString();
                        Object paramObj = jsonParser.parse(paramObject);
                        List<Object> paramList = (List<Object>) paramObj;

                        for (Object thisParam : paramList) {
                            Iterator<String> pkList = ((JSONObject) thisParam).keySet().iterator();
                            while (pkList.hasNext()) {
                                String paramKey = pkList.next();

                                if (paramKey.equals("param")) {
                                    String param = ((JSONObject) thisParam).get("param").toString();

                                    if (param.equals("description")) {
                                        String mandatory = ((JSONObject) thisParam).get("isMandatory").toString();
                                        insertForm.put("description", mandatory);
                                    }

                                    if (param.equals("media")) {
                                        String mandatory = ((JSONObject) thisParam).get("isMandatory").toString();
                                        insertForm.put("media", mandatory);
                                    }

                                    if (param.equals("title")) {
                                        String mandatory = ((JSONObject) thisParam).get("isMandatory").toString();
                                        insertForm.put("title", mandatory);
                                    }

                                    //card가 1개 이상인 경우 title, media, description각 항목에 1을 붙인 변수로 해당항목 체크
                                    if (param.equals("description1")) {
                                        String mandatory = ((JSONObject) thisParam).get("isMandatory").toString();
                                        insertForm.put("description", mandatory);
                                    }

                                    if (param.equals("media1")) {
                                        String mandatory = ((JSONObject) thisParam).get("isMandatory").toString();
                                        insertForm.put("media", mandatory);
                                    }

                                    if (param.equals("title1")) {
                                        String mandatory = ((JSONObject) thisParam).get("isMandatory").toString();
                                        insertForm.put("title", mandatory);
                                    }
                                }
                            }
                        }
                    }
                }

                String policyInfo = jsonObj.get("policyInfo").toString();
                //cardCount, maxTitleSize,maxButtonCount,buttonsAllowed,adHeaderAllowed,adBodyAllowed
                Object policyObj = jsonParser.parse(policyInfo);
                JSONObject policyJson = (JSONObject) policyObj;

                if(!insertForm.get("product_code").equals("sms")){
                    insertForm.put("max_title_size",String.valueOf(policyJson.get("maxTitleSize")));
                }
                insertForm.put("card_cnt",policyJson.get("cardCount").toString());
                insertForm.put("max_btn_cnt",policyJson.get("maxButtonCount").toString());
                insertForm.put("buttons_allowed",policyJson.get("buttonsAllowed").toString());
                insertForm.put("ad_header_allowed",policyJson.get("adHeaderAllowed").toString());
                insertForm.put("ad_body_allowed",policyJson.get("adBodyAllowed").toString());
                insertForm.put("description_size",policyJson.get("maxDescriptionSize").toString());//



                String guideInfo = jsonObj.get("guideInfo").toString();
                Object guideObj = jsonParser.parse(guideInfo);
                JSONObject guideJson = (JSONObject) guideObj;

                insertForm.put("card_description_size",guideJson.get("maxDescriptionSize").toString());
                if(insertForm.get("product_code").equals("sms")){
                    insertForm.put("guide_line_cnt",null);//SMS는 -1으로 들어감
                }else{
                    insertForm.put("guide_line_cnt",guideJson.get("maxLineCount").toString());
                }
                insertForm.put("btn_name_size",guideJson.get("maxDisplayText").toString());

                String buttons_allowed = insertForm.get("buttons_allowed");
                if(buttons_allowed.equals("true")){
                    insertForm.put("buttons_allowed", "0");
                }else if(buttons_allowed.equals("false")){
                    insertForm.put("buttons_allowed", "1");
                }

                String ad_header_allowed = insertForm.get("ad_header_allowed");
                if(ad_header_allowed.equals("true")){
                    insertForm.put("ad_header_allowed", "0");
                }else if(ad_header_allowed.equals("false")){
                    insertForm.put("ad_header_allowed", "1");
                }

                String ad_body_allowed = insertForm.get("ad_body_allowed");
                if(ad_body_allowed.equals("true")){
                    insertForm.put("ad_body_allowed", "0");
                }else if(ad_body_allowed.equals("false")){
                    insertForm.put("ad_body_allowed", "1");
                }

                String product_code = insertForm.get("product_code");
                if(product_code.equals("sms")){
                    insertForm.put("product_code", "1");
                }else if(product_code.equals("lms")) {
                    insertForm.put("product_code", "2");
                }else if(product_code.equals("mms")) {
                    insertForm.put("product_code", "3");
                }else{
                    insertForm.put("product_code", "0");
                }



                String media = insertForm.get("media");
                if(media != null){
                    if(media.equals("false")){
                        insertForm.put("media", "0");
                    }else{
                        insertForm.put("media", "1");
                    }
                }else{
                    insertForm.put("media", "0");
                }

                String title = insertForm.get("title");
                if(title != null) {
                    if (title.equals("false")) {
                        insertForm.put("title", "0");
                    } else {
                        insertForm.put("title", "1");
                    }
                }else{
                    insertForm.put("title", "0");
                }

                String description = insertForm.get("description");
                if(description != null) {
                    if (description.equals("false")) {
                        insertForm.put("description", "0");
                    } else {
                        insertForm.put("description", "1");
                    }
                }else{
                    insertForm.put("description", "0");
                }
            }

            String status = insertForm.get("status");
            if(status.equals("ready")){
                insertForm.put("status","0");
            }else{
                insertForm.put("status","1");
            }

            String approval_result = insertForm.get("approval_result");
            switch (approval_result) {
                case "승인대기":
                    insertForm.put("approval_result", "0");
                    break;
                case "승인":
                    insertForm.put("approval_result", "1");
                    break;
                case "반려":
                    insertForm.put("approval_result", "2");
                    break;
                case "검수완료":
                    insertForm.put("approval_result", "3");
                    break;
                case "저장":
                    insertForm.put("approval_result", "4");
                    break;
            }

            log.info("insertForm - SMS, LMS, MMS {} :", insertForm);

            int chkCnt;
            if(!duplicate){
                chkCnt  =0;
            }else{
                chkCnt = templateService.selectTemplateFormId(insertForm.get("form_id"));
            }

            if(chkCnt == 0){
                Map<Object, String> writeTxt = UtilController.writeTxt(insertForm);
                String colListBtn = writeTxt.get("colListBtn");
                String valListBtn = writeTxt.get("valListBtn");

                if (chkNum == 0) {
                    fw.write(colListBtn);
                    fw.write(valListBtn);
                    chkNum += 1;
                } else {
                    fw.write(valListBtn);
                }
                if(compareDate.equals("0")){
                    util.coreFile("TEMPLATE_FORM", insertForm.get("form_id"), "insert");
                    setYesterChatList.remove(insertForm.get("form_id"));
                }
            }else{
                String concat =insertForm.get("product_code")+insertForm.get("card_type")+insertForm.get("media")
                        +insertForm.get("title")+insertForm.get("description")+insertForm.get("card_cnt")+insertForm.get("approval_result")+insertForm.get("status")+insertForm.get("max_title_size");

                int chkDefaultData = templateMapper.selectTemplateFormUpdateChk(insertForm.get("form_id"),concat,util.getTodayPartition(0));
                if(compareDate.equals("0")){
                    setYesterChatList.remove(insertForm.get("form_id"));
                }
                if(chkDefaultData == 0) {
                    util.coreFile("TEMPLATE_FORM", insertForm.get("form_id"), "update");
                    templateService.updateTemplateForm(insertForm);
                }
            }
        }

        fw.close();

        if(compareDate.equals("0")){
            Map<Object,String> delMap = new HashMap<>();
            if(setYesterChatList != null && setYesterChatList.size() > 0) {
                String messagebaseIdList = setYesterChatList.toString().replace(",", "','").replace("[", "'").replace("]", "'").replaceAll(" ", "");
                util.coreFile("TEMPLATE_FORM", (setYesterChatList.toString()).replace("[", "").replace("]", "").replace(" ", ""), "delete");
                delMap.put("dateChk", "0");
                delMap.put("partition", util.getTodayPartition(0));
                delMap.put("formId", messagebaseIdList);
                delMap.put("product", "1");

                int vla = templateMapper.updateTemplateFormDelete(delMap);

                if(vla > 0){
                    log.info("[TEMPLATE_FORM UPDATE SUCCESS]");
                }else {
                    log.info("[TEMPLATE_FORM(SMS, LMS, MMS) UPDATE FAIL] List : {}" , delMap);
                }

            }
        }

        String temPath =Constants.PATH_FILE_ROOT + formBasic;

        Map<String, Object> insertMap = new HashMap<>();
        insertMap.put("data",temPath);
        insertMap.put("date",compareDate);
        insertMap.put("regDate",regDate);
        insertCnt = templateService.insertTemplateBasicAuto(insertMap);

        if(insertCnt == baseIdList.size()){
            //INSERT성공시에만 해당 파일 삭제
            fileMsgForm.delete();
            log.info("SMS,LMS,MMS FORM INSERT SUCCESS");
            System.out.println("SMS,LMS,MMS 양식 INSERT 성공");
        }else if(compareDate.equals("0")){
            fileMsgForm.delete();
        }
        return insertCnt;
    }

    //sms, lms, mms정보를 가져오기 위해 messagebaseId가져오기
    private String getMessagebaseId (String getToken) throws ParseException{
        String getUrl = MESSAGE_URL;
        String messagebaseId ="";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization",getToken);
        HttpEntity entity = new HttpEntity(headers);

        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET,entity,String.class);

        String body = result.getBody();

        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(body);
        JSONObject jsonObject = (JSONObject) obj;
        String resultObj =jsonObject.get("result").toString();
        obj = jsonParser.parse(resultObj);
        jsonObject = (JSONObject)  obj;
        resultObj  = (jsonObject.get("messageBases").toString()).replace("[{","").replace("}]","").replace("},{","_!@#split!@#_");
        List<String> items = Arrays.asList(resultObj.split("_!@#split!@#_"));
        int itemLen = items.size();
        JSONArray ja = (JSONArray) jsonObject.get("messageBases");
        for(int i = 0; ja.size()> i ; i++){
            JSONObject getItem = (JSONObject) ja.get(i);
            if(i == 0){
                messagebaseId = getItem.get("messagebaseformId").toString();
            }else {
                messagebaseId += "," + getItem.get("messagebaseformId").toString();
            }
        }
        return messagebaseId;

    }
    //등록템플릿 정보 insert
    public int insertTemplateAuto(Map<String,Object> map){
        return templateMapper.insertTemplateAuto(map);
    }

    //등록 템플릿의 버튼정보 insert
    public int insertTemplateBtn(String date){
        return templateMapper.insertTemplateBtn(date);
    }


    //cell,desc타입의 템플릿양식 정보 insert
    public int insertTemplateFormAuto(Map<String,Object> map){
        int insertCnt = templateMapper.insertTemplateFormAuto(map);

        return insertCnt;
    }
    //sms, lms, mms타입의 템플릿 양식 정보 insert
    public int insertTemplateBasicAuto(Map<String,Object> map){
        map.get("data");
        int insertCnt =templateMapper.insertTemplateBasicAuto(map);
        return insertCnt;
    }

    public int selectTemplateId(String Id){
        Map<String, Object> map = new HashMap<>();
        map.put("id",Id);
        map.put("partition", util.getTodayPartition(0));


        return templateMapper.selectTemplateId(map);
    }

    public int selectTemplateFormId(String Id){
        return templateMapper.selectTemplateFormId(Id);
    }

    public int updatetemplate(Map<Object,String> params){
        int returnCode = -99;

        params.put("partition", util.getTodayPartition(0));
        int updateData = templateMapper.updateTemplate(params);

        if(updateData > 0){
            returnCode = 100;
        }
        return returnCode;
    }

    public int insertTemplate(Map<Object,String> params){
        int returnCode = -99;
        params.put("partition", util.getTodayPartition(0));
        int updateData = templateMapper.insertTemplate(params);

        if(updateData > 0){
            returnCode = 100;
        }
        return returnCode;
    }

    public int updateTemplateForm(Map<Object,String> params){
        int returnCode = -99;

        int updateData = templateMapper.updateTemplateForm(params);

        if(updateData > 0){
            returnCode = 100;
        }
        return returnCode;
    }

    public int updateTemplateBtnList(Map<Object,String> params){
        int returnCode = -99;

        int updateData = templateMapper.updateTemplateBtn(params);

        if(updateData > 0){
            returnCode = 100;
        }
        return returnCode;
    }


    public int insertTemplateForm(Map<Object,String> params){
        int returnCode = -99;
        int updateData = templateMapper.insertTemplateForm(params);

        if(updateData > 0){
            returnCode = 100;
        }
        return returnCode;
    }


    //기존데이터 삭제 (어제 일자에는 있지만 현재 오늘 일자에는 없는 데이터)
    public String selectDelTmpl(int date){
        String delId =null;
        Map<Object,String> setPartition = new HashMap<>();
        setPartition.put("today",util.getTodayPartition(0));
        setPartition.put("yesterday",util.getTodayPartition(date));

        delId = templateMapper.selectDelTmpl(setPartition);

        return delId;

    }

    //새로운 데이터 등록 (어제 일자에는 없지만 현재 오늘 일자에는 있는 데이터)
    public String selectNewTmpl(int date){
        String delId =null;
        Map<Object,String> setPartition = new HashMap<>();
        setPartition.put("today",util.getTodayPartition(0));
        setPartition.put("yesterday",util.getTodayPartition(date));

        delId = templateMapper.selectNewTmpl(setPartition);

        return delId;

    }

    //기존데이터 삭제 (어제 일자에는 있지만 현재 오늘 일자에는 없는 데이터)
    public String selectDelTmplBtn(int date){
        String delId =null;
        Map<Object,String> setPartition = new HashMap<>();
        setPartition.put("today",util.getTodayPartition(0));
        setPartition.put("yesterday",util.getTodayPartition(date));

        delId = templateMapper.selectDelTmplBtn(setPartition);

        return delId;

    }

    //새로운 데이터 등록 (어제 일자에는 없지만 현재 오늘 일자에는 있는 데이터)
    public String selectNewTmplBtn(int date){
        String delId =null;
        Map<Object,String> setPartition = new HashMap<>();
        setPartition.put("today",util.getTodayPartition(0));
        setPartition.put("yesterday",util.getTodayPartition(date));

        delId = templateMapper.selectNewTmplBtn(setPartition);

        return delId;

    }

    //수정 데이터 확인을 위한 현재 날짜의 데이터 가져오기 (delete데이터, insert데이터 제외)
    public List<Map<Object, String>> todayTemplateList(Map<Object,String> map){
        return templateMapper.todayTemplateList(map);
    }

    //수정 데이터 확인을 위한 어제 날짜의 데이터 가져오기 (delete데이터, insert데이터 제외)
    public Set<String> yesterdayTemplate(Map<Object,String> map){
        List<Map<Object, String>> mapData= templateMapper.todayTemplateList(map);
        String dateChk = map.get("dateChk");
        Set<String> setData = new HashSet<>();
        int size = mapData.size();
        if(dateChk != null && dateChk.equals("0")){
            for (Map<Object, String> mapDatum : mapData) {
                String thisCol = mapDatum.get("MESSAGEBASE_ID");
                setData.add(thisCol);
            }
        }else{
            for (Map<Object, String> mapDatum : mapData) {
                String thisCol = mapDatum.get("colAll");
                setData.add(thisCol);
            }
        }

        return setData;
    }


    //수정 데이터 확인을 위한 현재 날짜의 데이터 가져오기 (delete데이터, insert데이터 제외)
    public List<Map<String, Object>> todayTemplateBtnList(Map<Object,String> map){
        return templateMapper.todayTemplateBtnList(map);
    }

    //수정 데이터 확인을 위한 어제 날짜의 데이터 가져오기 (delete데이터, insert데이터 제외)
    public Set<String> yesterdayTemplateBtn(Map<Object,String> map){
        List<Map<String, Object>> mapData= templateMapper.todayTemplateBtnList(map);
        String dateChk = map.get("dateChk");
        Set<String> setData = new HashSet<>();
        int size = mapData.size();
        if(dateChk != null && dateChk.equals("0")){
            for (Map<String, Object> thisMap : mapData) {
                String thisCol = thisMap.get("MESSAGEBASE_ID").toString();
                setData.add(thisCol);
            }
        }else{
            for (Map<String, Object> thisMap : mapData) {
                String thisCol = thisMap.get("DEL_YN").toString();
                setData.add(thisCol);
            }
        }

        return setData;
    }


    //기존데이터 삭제 (어제 일자에는 있지만 오늘 일자에는 없는 데이터)
    public String selectDelTmplForm(int date){
        String delId =null;
        Map<Object,String> setPartition = new HashMap<>();
        setPartition.put("today",util.getTodayPartition(0));
        setPartition.put("yesterday",util.getTodayPartition(date));

        delId = templateMapper.selectDelTmplForm(setPartition);

        return delId;
    }

    //새로운 데이터 등록 (어제 일자에는 없지만 오늘 일자에는 있는 데이터)
    public String selectNewTmplForm(int date){
        String delId =null;
        Map<Object,String> setPartition = new HashMap<>();
        setPartition.put("today",util.getTodayPartition(0));
        setPartition.put("yesterday",util.getTodayPartition(date));

        delId = templateMapper.selectNewTmplForm(setPartition);

        return delId;
    }

    //수정 데이터 확인을 위한 현재 날짜의 데이터 가져오기 (delete데이터, insert데이터 제외한 데이터)
    public List<Map<Object, String>> todayTemplateFormList(Map<Object,String> map){
        return templateMapper.todayTemplateFormList(map);
    }

    //수정 데이터 확인을 위한 어제 날짜의 데이터 가져오기 (delete데이터, insert데이터 제외한 데이터)
    public Set<String> yesterdayTemplateForm(Map<Object,String> map){
        List<Map<Object, String>> mapData= templateMapper.todayTemplateFormList(map);

        String dateChk = map.get("dateChk");
        Set<String> setData = new HashSet<>();
        int size = mapData.size();
        if(dateChk != null && dateChk.equals("0")){
            for (Map<Object, String> thisMap : mapData) {
                String thisCol = thisMap.get("FORM_ID");
                setData.add(thisCol);
            }
        }else{
            for (Map<Object, String> thisMap : mapData) {
                String thisCol = thisMap.get("colAll");
                setData.add(thisCol);
            }
        }
        return setData;
    }


    //등록된 ID리스트 가져오기
    public Set<String> getOldTmplIdList(Map<Object,String> map){
        map.put("del_YN", "N");
        Set<String> oldListSet = new HashSet<>();
        List<Map<Object, String>> oldList = templateMapper.todayTemplateList(map);

        int size = oldList.size();
        for (Map<Object, String> objectStringMap : oldList) {
            String thisCol = objectStringMap.get("MESSAGEBASE_ID");
            oldListSet.add(thisCol);
        }
        return oldListSet;
    }

    //등록된 ID리스트 가져오기
    public Set<String> getOldTmplBtnList(Map<Object,String> map){
        Set<String> oldListSet = new HashSet<>();
        map.put("notInId",null);
        List<Map<String, Object>> oldList = templateMapper.todayTemplateBtnList(map);
        int size = oldList.size();

        for (Map<String, Object> stringObjectMap : oldList) {
            String thisCol = stringObjectMap.get("MESSAGEBASE_ID").toString();
            oldListSet.add(thisCol);
        }
        return oldListSet;
    }

}
