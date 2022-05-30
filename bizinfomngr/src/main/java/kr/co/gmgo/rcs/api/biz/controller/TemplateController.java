package kr.co.gmgo.rcs.api.biz.controller;

import kr.co.gmgo.rcs.api.biz.common.Constants;
import kr.co.gmgo.rcs.api.biz.common.ResponseBuilder;
import kr.co.gmgo.rcs.api.biz.service.BrandService;
import kr.co.gmgo.rcs.api.biz.service.SchedulerService;
import kr.co.gmgo.rcs.api.biz.service.TemplateService;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("/template")
public class TemplateController {
    private Logger log = LoggerFactory.getLogger("log.rcsLog");
    @Autowired
    TemplateService templateService;

    @Autowired
    BrandService brandService;

    @Autowired
    UtilController util;

    @Autowired
    SchedulerService schedulerService;

    private String MESSAGE_DETAIL_URL = Constants.BIZ_API+"messagebase/common/";
    private String TEMPLATEFORM_DETAIL_URL = Constants.BIZ_API+"messagebase/messagebaseform/";

    /**
     * Biz center 템플릿 API호출 DB Insert(수동)
     * file로 생성 후 insert
     *
     * @author Moon
     * @exception IOException
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */

    @GetMapping("/templateAll")
    public ResponseEntity<Object> templateAll(@RequestParam(value = "date", required = false)String date,
                                              @RequestParam(value = "regDate", required = false)String regDate,
                                              HttpServletRequest req){
        log.info("method name is {}", "/biz/all");

        ResponseBuilder builder = new ResponseBuilder();

        boolean ipCheck = util.ipCheck(req);
        if(ipCheck){
            int tmplResult = -99;
            try {
                if(date == null){
                    date = "1";
                }
                if(regDate == null || regDate.equals("")){//오늘 날짜
                    Calendar cal = Calendar.getInstance();
                    SimpleDateFormat format = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss");
                    regDate =format.format(cal.getTime());
                }

                tmplResult = templateService.getTemplateList(date, regDate);

                int insertInt = 0;
                if(date != null && !date.equals("0")){
                    insertInt = Integer.parseInt(date);
                }else if(date == null){
                    insertInt = 1;
                }

                if(tmplResult == 100){
                    log.info("template insert Success");
                    schedulerService.dataGetFile("getTemplateData");
                }

                File Template = new File(Constants.PATH_FILE_ROOT + "getTemplateData");

                boolean isExistsTemplate = Template.exists();

                if(isExistsTemplate){
                    schedulerService.coreFileTemplateForm(insertInt);
                    boolean formDelete = Template.delete();
                    System.out.println("formDelete :" +formDelete);
                }

                if(tmplResult == 100) {
                    log.info("templateAll Success");
                    builder.setMessage("SUCCESS");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                }else{
                    log.info("templateAll CALL Fail");
                    builder.setMessage("bizAll CALL Fail");
                    builder.setResult(ResponseBuilder.Result.FAIL).build();
                }

            } catch (IOException e) {
                e.printStackTrace();
                log.info("IOException - templateAll FAIL");
            } catch (ParseException e) {
                e.printStackTrace();
                log.info("ParseException - templateAll FAIL");
            }

        }else{
            builder.setMessage("No permission");
            builder.setResult(ResponseBuilder.Result.FAIL).build();
            log.info("ipCheck NO PERMISSION");
        }
        return builder.build();
    }

    @RequestMapping("/{brandId}/detail/{messagebaseId}")
    public ResponseEntity<Object> getTemplate(@PathVariable("brandId") String brandId,
                                              @PathVariable("messagebaseId") String messagebaseId,
                                              HttpServletRequest req){

        ResponseBuilder builder = new ResponseBuilder();
        boolean ipCheck = util.ipCheck(req);
        if(ipCheck){
            try {
                Map<String,String> response = getTemplateDetail(brandId, messagebaseId);
                String bizCode = response.get("bizResult");
                String message = response.get("message");
                String code = response.get("result");

                if(bizCode.equals("200")){
                    if(!code.equals("100")){//실패
                        log.info("message :{}","TEMPLATE "+ message);
                        log.info("Bizcode :{}", bizCode);
                        builder.setResult(ResponseBuilder.Result.FAIL).setMessage(message).build();
                    }else{
                        log.info("message :{}","TEMPLATE "+  message);
                        log.info("Bizcode :{}", bizCode);
                        builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                    }
                }else{
                    log.info("message :{}","TEMPLATE"+ message);
                    log.info("Bizcode :{}", bizCode);
                    builder.setResult(ResponseBuilder.Result.FAIL).setMessage(message).build();
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }else{
            builder.setResult(ResponseBuilder.Result.FAIL).setMessage("No permission").build();
        }
        return builder.build();

    }
    /**
     * 템플릿 정보 개별조회 Insert
     *
     * @author Moon
     * @exception IOException
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */

    public Map<String,String> getTemplateDetail(@PathVariable("brandId") String brandId,
                                                @PathVariable("messagebaseId") String messagebaseId) throws ParseException {
        log.info("method name is {}", "/template/{brandId}/detail/{messagebaseId}");
        ResponseBuilder builder = new ResponseBuilder();

        String getToken = util.getTokenVal();
        Object brandKey = brandService.selectBrandKey(brandId);

        String getUrl =  Constants.BIZ_API+"brand/"+brandId+"/messagebase/"+messagebaseId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization",getToken);
        headers.set("x-rcs-brandkey",brandKey.toString());
        HttpEntity entity = new HttpEntity(headers);

        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET,entity,String.class);
        Object bizResult = result.getStatusCodeValue();
        Map<String,String> returnVal = new HashMap<>();
        //List<Map<String,String>> btnInsertList = new ArrayList<>();
        //int btnInsertCnt = 0;

        if(result.getStatusCodeValue() == 200){
            String body = result.getBody();

            JSONParser jsonParser = new JSONParser();

            Object obj = jsonParser.parse(body);
            JSONObject jsonObj = (JSONObject) obj;
            String resultObj = jsonObj.get("result").toString();

            obj = jsonParser.parse(resultObj);
            jsonObj = (JSONObject) obj;
            JSONArray templateList = (JSONArray) jsonObj.get("tmplts");

            obj = jsonParser.parse(resultObj);
            jsonObj = (JSONObject)obj;

            Map<Object,String> temList = new HashMap<>();

            temList.put("messagebase_id",jsonObj.get("messagebaseId").toString());
            temList.put("template_name",jsonObj.get("tmpltName").toString());
            temList.put("messagebase_form_id",jsonObj.get("messagebaseformId").toString());
            temList.put("brand_id", jsonObj.get("brandId").toString());
            temList.put("template_reg_dt",jsonObj.get("registerDate").toString().replace("T", " ").substring(0, 19));
            if(jsonObj.get("updateId") != null && jsonObj.get("updateId") != "" ){
                temList.put("update_id", jsonObj.get("updateId").toString());
            }else{
                temList.put("update_id", "null");
            }
            temList.put("update_dt",jsonObj.get("updateDate").toString().replace("T", " ").substring(0, 19));
            temList.put("reg_id",jsonObj.get("registerId").toString());

            if(jsonObj.get("approvalReason") != null && jsonObj.get("approvalReason") != "" ){
                temList.put("approval_reason", jsonObj.get("approvalReason").toString());
            }else{
                temList.put("approval_reason", null);
            }

            if(jsonObj.get("approvalDate") != null && jsonObj.get("approvalDate") != "" ){
                temList.put("approval_dt", jsonObj.get("approvalDate").toString().replace("T", " ").substring(0, 19));
            }else{
                temList.put("approval_dt", null);
            }

            String approval_result = jsonObj.get("approvalResult").toString();
            switch (approval_result) {
                case "승인대기":
                    temList.put("approval_result", "0");
                    break;
                case "승인":
                    temList.put("approval_result", "1");
                    break;
                case "반려":
                    temList.put("approval_result", "2");
                    break;
                case "검수완료":
                    temList.put("approval_result", "3");
                    break;
                case "저장":
                    temList.put("approval_result", "4");
                    break;
            }


            String status = jsonObj.get("status").toString();

            if(status.equals("ready")){
                temList.put("status", "0");
            }else if(status.equals("pause")){
                temList.put("status", "1");
            }

            temList.put("card_type",jsonObj.get("cardType").toString());

            int chkCnt =templateService.selectTemplateId(messagebaseId);

            /*if(temList.get("approval_result").equals("1")){
                Map<String,String> btnInfo =new HashMap<>();
                btnInfo.put("brandId",temList.get("brand_id"));
                btnInfo.put("messagebaseId",temList.get("messagebase_id"));
                btnInsertList.add(btnInsertCnt,btnInfo);
                templateService.insertTemplateBtn(btnInsertList, "0");
            }*/

            if (chkCnt > 0) {
                int updateData = templateService.updatetemplate(temList);
                if (updateData == 100) {
                    builder.setMessage("Update Success");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","100");
                    returnVal.put("message","Update Success");

                    int coreFile = util.coreFile("TEMPLATE", messagebaseId, "update");

                    if (coreFile == 100){
                        log.info("[SUCCESS_TEMPLATE_UPDATE_CORE_FILE] messagebaseId : {} ",messagebaseId);
                    }else{
                        log.info("[FAIL_TEMPLATE_UPDATE_CORE_FILE] messagebaseId : {} ",messagebaseId);
                    }

                } else {
                    builder.setMessage("Update Fail");
                    builder.setResult(ResponseBuilder.Result.FAIL).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","-99");
                    returnVal.put("message","Update Fail");
                }

            } else {//insert
                int insertData = templateService.insertTemplate(temList);
                if (insertData == 100) {
                    builder.setMessage("Insert Success");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","100");
                    returnVal.put("message","Insert Success");

                    int coreFile = util.coreFile("TEMPLATE", messagebaseId, "insert");

                    if (coreFile == 100){
                        log.info("[SUCCESS_TEMPLATE_UPDATE_CORE_FILE] messagebaseId : {} ",messagebaseId);
                    }else{
                        log.info("[FAIL_TEMPLATE_UPDATE_CORE_FILE] messagebaseId : {} ",messagebaseId);
                    }

                } else {
                    builder.setMessage("Insert Fail");
                    builder.setResult(ResponseBuilder.Result.FAIL).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","-99");
                    returnVal.put("message","Insert Fail");
                }
            }
        } else {
            builder.setMessage("API ACCESS FAIL [CODE] : "+result.getStatusCodeValue()) ;
            builder.setResult(ResponseBuilder.Result.FAIL).build();

            returnVal.put("bizResult",bizResult.toString());
            returnVal.put("result","-98");
            returnVal.put("message","API CODE NOT 200 [CODE] : "+result.getStatusCodeValue());

        }
        return returnVal;
    }

    /**
     * 템플릿 양식 개별 조회 및 insert
     *(cell, description)
     *
     * @author Moon
     * @exception IOException
     * @exception ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    @GetMapping("/templateForm/{id}")
    public ResponseEntity<Object> templateForm(@PathVariable("id") String formId,
                                               HttpServletRequest req){
        ResponseBuilder builder = new ResponseBuilder();
        boolean ipCheck = util.ipCheck(req);
        if(ipCheck){
            try {
                Map<String,String> response = getTemplateForm(formId);
                String Bizcode = response.get("bizResult");
                String message = response.get("message");
                String code = response.get("result");

                if(Bizcode.equals("200")){
                    if(!code.equals("100")){//실패
                        log.info("message :{}","TEMPLATEFORM "+ message);
                        log.info("Bizcode :{}", Bizcode);
                        builder.setResult(ResponseBuilder.Result.FAIL).setMessage(message).build();
                    }else{
                        log.info("message :{}","TEMPLATEFORM "+  message);
                        log.info("Bizcode :{}", Bizcode);
                        builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                    }
                }else{
                    log.info("message :{}","TEMPLATEFORM "+ message);
                    log.info("Bizcode :{}", Bizcode);
                    builder.setResult(ResponseBuilder.Result.FAIL).setMessage(message).build();
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        return builder.build();
    }



    public Map<String,String> getTemplateForm(@PathVariable("id") String formId) throws ParseException {
        log.info("method name is {}", "/template/templateForm/{id}");
        ResponseBuilder builder = new ResponseBuilder();

        String getToken = util.getTokenVal();

        String url = TEMPLATEFORM_DETAIL_URL + formId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization",getToken);
        HttpEntity entity = new HttpEntity(headers);

        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> result = rest.exchange(url, HttpMethod.GET,entity,String.class);

        Object bizResult = result.getStatusCodeValue();
        Map<String,String> returnVal = new HashMap<>();

        if(result.getStatusCodeValue() == 200){
            String body = result.getBody();
            JSONParser jsonParser = new JSONParser();

            Object obj = jsonParser.parse(body);
            JSONObject jsonObj = (JSONObject) obj;
            String resultObj = jsonObj.get("result").toString();

            obj = jsonParser.parse(resultObj);
            jsonObj = (JSONObject) obj;

            Map<Object,String> insertForm = new HashMap<>();
            if(jsonObj.get("productCode").toString().equals("tmplt")){
                insertForm.put("product_code","4");
            }else{
                insertForm.put("product_code","0");
            }
            insertForm.put("card_type",jsonObj.get("cardType").toString());
            insertForm.put("form_id",formId);
            insertForm.put("media",null);
            insertForm.put("title",null);
            insertForm.put("description",null);
            insertForm.put("ad_header_allowed",null);

            Iterator<String> keys = ((JSONObject) obj).keySet().iterator();
            while(keys.hasNext()){
                String thisKey = keys.next();
                if(thisKey.equals("params")){//param,isMandatorymedia,verification chk
                    String paramObject = jsonObj.get("params").toString();
                    Object paramObj = jsonParser.parse(paramObject);
                    List<Object> paramList = (List<Object>) paramObj;

                    for (Object thisParam : paramList) {
                        Iterator<String> pkList = ((JSONObject) thisParam).keySet().iterator();
                        while (pkList.hasNext()) {
                            String paramKey = pkList.next();

                            if (paramKey.equals("param")) {//
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


            String policyInfo = jsonObj.get("policyInfo").toString();
            //cardCount,maxTitleSize,maxDescriptionSize,maxButtonCount,buttonsAllowed,adHeaderAllowed,adBodyAllowed

            Object policyObj = jsonParser.parse(policyInfo);
            JSONObject policyJson = (JSONObject) policyObj;
            insertForm.put("card_cnt",policyJson.get("cardCount").toString());
            insertForm.put("description_size",policyJson.get("maxDescriptionSize").toString());
            insertForm.put("buttons_allowed",policyJson.get("buttonsAllowed").toString());
            insertForm.put("ad_header_allowed",policyJson.get("adHeaderAllowed").toString());
            insertForm.put("ad_body_allowed",policyJson.get("adBodyAllowed").toString());
            insertForm.put("max_btn_cnt",policyJson.get("maxButtonCount").toString());


            String guideInfo = jsonObj.get("guideInfo").toString();
            //maxLineCount,maxDisplayText,maxDescriptionSize
            Object guideObj = jsonParser.parse(guideInfo);
            JSONObject guideJson = (JSONObject) guideObj;
            insertForm.put("guide_line_cnt",guideJson.get("maxLineCount").toString());
            insertForm.put("btn_name_size",guideJson.get("maxDisplayText").toString());
            insertForm.put("card_description_size",guideJson.get("maxDescriptionSize").toString());

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

            /*cell 타입에서는 title, media, description이 아닌
                    cell1...cell11과같은 형식으로 데이터를 내려주기때문에
                    해당 key값을 체크하여 description에 값을 담아주고있음*/
            if(insertForm.get("card_type").equals("cell")){
                insertForm.put("description","0");
                insertForm.put("media","0");
                insertForm.put("title","0");
            }else{
                String media = insertForm.get("media");
                if(media != null){
                    if(media.equals("false")){
                        insertForm.put("media", "0");
                    }else {
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

            int chkCnt =templateService.selectTemplateFormId(formId);

            if (chkCnt > 0) {
                int updateData = templateService.updateTemplateForm(insertForm);
                if (updateData == 100) {
                    builder.setMessage("Update Success");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","100");
                    returnVal.put("message","Update Success");

                    int coreFile = util.coreFile("TEMPLATE_FORM", formId, "update");

                    if (coreFile == 100){
                        log.info("[SUCCESS_TEMPLATE_FORM_UPDATE_CORE_FILE] formId : {} ",formId);
                    }else{
                        log.info("[FAIL_TEMPLATE_FORM_UPDATE_CORE_FILE] formId : {} ",formId);
                    }

                } else {
                    builder.setMessage("Update Fail");
                    builder.setResult(ResponseBuilder.Result.FAIL).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","-99");
                    returnVal.put("message","Update Fail");
                }
            } else {//insert
                int updateData = templateService.insertTemplateForm(insertForm);
                if (updateData == 100) {
                    builder.setMessage("Insert Success");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","100");
                    returnVal.put("message","Insert Success");

                    int coreFile = util.coreFile("TEMPLATE_FORM", formId, "insert");

                    if (coreFile == 100){
                        log.info("[SUCCESS_TEMPLATE_FORM_UPDATE_CORE_FILE] formId : {} ",formId);
                    }else{
                        log.info("[FAIL_TEMPLATE_FORM_UPDATE_CORE_FILE] formId : {} ",formId);
                    }

                } else {
                    builder.setMessage("Insert Fail");
                    builder.setResult(ResponseBuilder.Result.FAIL).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","-99");
                    returnVal.put("message","Insert Fail");
                }
            }
        }else {
            builder.setMessage("API ACCESS FAIL [CODE] : "+ result.getStatusCodeValue()) ;
            builder.setResult(ResponseBuilder.Result.FAIL).build();

            returnVal.put("bizResult",bizResult.toString());
            returnVal.put("result","-98");
            returnVal.put("message","API CODE NOT 200 [CODE] : "+ result.getStatusCodeValue());
        }

        return returnVal;

    }

    /**
     * 템플릿 양식 개별 조회 및 insert
     *(SMS,LMS,MMS)
     *
     * @author Moon
     * @exception IOException
     * @exception ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    @GetMapping("/messageForm/{id}")
    public ResponseEntity<Object> messageForm(@PathVariable("id") String msgbaseId,
                                              HttpServletRequest req){

        ResponseBuilder builder = new ResponseBuilder();
        boolean ipCheck = util.ipCheck(req);
        if(ipCheck){
            Map<String,String> response = null;
            try {
                response = messageForm(msgbaseId);
                String Bizcode = response.get("bizResult");
                String message = response.get("message");
                String code = response.get("result");

                if(Bizcode.equals("200")){
                    if(!code.equals("100")){//실패
                        log.info("message :{}","MESSAGEFORM "+ message);
                        log.info("Bizcode :{}", Bizcode);
                        builder.setResult(ResponseBuilder.Result.FAIL).setMessage(message).build();
                    }else{
                        log.info("message :{}","MESSAGEFORM "+  message);
                        log.info("Bizcode :{}", Bizcode);
                        builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                    }
                }else{
                    log.info("message :{}","MESSAGEFORM "+ message);
                    log.info("Bizcode :{}", Bizcode);
                    builder.setResult(ResponseBuilder.Result.FAIL).setMessage(message).build();
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }else{
            builder.setResult(ResponseBuilder.Result.FAIL).setMessage("No permission").build();
        }
        return builder.build();
    }


    public Map<String,String> messageForm(@PathVariable("id") String msgbaseId) throws ParseException {
        log.info("method name is {}", "/template/messageForm/{id}");
        ResponseBuilder builder = new ResponseBuilder();

        String getToken = util.getTokenVal();

        String url = MESSAGE_DETAIL_URL + msgbaseId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", getToken);
        HttpEntity entity = new HttpEntity(headers);

        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> result = rest.exchange(url, HttpMethod.GET,entity,String.class);
        Object bizResult = result.getStatusCodeValue();
        Map<String,String> returnVal = new HashMap<>();

        if(result.getStatusCodeValue() == 200){
            Map<Object,String> insertForm = new HashMap<>();
            String body = result.getBody();
            JSONParser jsonParser = new JSONParser();

            Object obj = jsonParser.parse(body);
            JSONObject jsonObj = (JSONObject) obj;
            String resultObj = jsonObj.get("result").toString();
            obj = jsonParser.parse(resultObj);
            jsonObj = (JSONObject) obj;

            insertForm.put("form_id",jsonObj.get("messagebaseformId").toString());
            insertForm.put("card_type",jsonObj.get("cardType").toString());
            insertForm.put("product_code",jsonObj.get("productCode").toString());
            insertForm.put("media",null);
            insertForm.put("title",null);
            insertForm.put("description",null);
            insertForm.put("status",jsonObj.get("status").toString());
            insertForm.put("approval_result",jsonObj.get("approvalResult").toString());

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


            String policyInfo = jsonObj.get("policyInfo").toString();/////////////////////////////////////////
            //cardCount, maxTitleSize,maxButtonCount,buttonsAllowed,adHeaderAllowed,adBodyAllowed
            Object policyObj = jsonParser.parse(policyInfo);
            JSONObject policyJson = (JSONObject) policyObj;

            insertForm.put("card_cnt",policyJson.get("cardCount").toString());
            insertForm.put("max_btn_cnt",policyJson.get("maxButtonCount").toString());
            insertForm.put("buttons_allowed",policyJson.get("buttonsAllowed").toString());
            insertForm.put("ad_header_allowed",policyJson.get("adHeaderAllowed").toString());
            insertForm.put("ad_body_allowed",policyJson.get("adBodyAllowed").toString());
            insertForm.put("description_size",policyJson.get("maxDescriptionSize").toString());

            String guideInfo = jsonObj.get("guideInfo").toString();
            Object guideObj = jsonParser.parse(guideInfo);
            JSONObject guideJson = (JSONObject) guideObj;

            insertForm.put("card_description_size",guideJson.get("maxDescriptionSize").toString());
            insertForm.put("guide_line_cnt",guideJson.get("maxLineCount").toString());
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


            String status = insertForm.get("status");
            if(status.equals("ready")){
                insertForm.put("status","0");
            }else{
                insertForm.put("status","1");
            }

            String approval_result = insertForm.get("approval_result");
            if(approval_result.equals("승인대기")){
                insertForm.put("approval_result", "0");
            }else if(approval_result.equals("승인")){
                insertForm.put("approval_result", "1");
            }else if(approval_result.equals("반려")){
                insertForm.put("approval_result", "2");
            }else if(approval_result.equals("검수완료")){
                insertForm.put("approval_result", "3");
            }else if(approval_result.equals("저장")){
                insertForm.put("approval_result", "4");
            }
            String title_size = policyJson.get("maxTitleSize").toString();
            insertForm.put("max_title_size",title_size);
            //중복 템플릿 폼 아이디 확인
            int chkCnt =templateService.selectTemplateFormId(msgbaseId);

            if (chkCnt > 0) {
                int updateData = templateService.updateTemplateForm(insertForm);
                if (updateData == 100) {
                    builder.setMessage("Update Success");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","100");
                    returnVal.put("message","Update Success");

                    int coreFile = util.coreFile("TEMPLATE_FORM", msgbaseId, "update");

                    if (coreFile == 100){
                        log.info("[SUCCESS_TEMPLATE_FORM_UPDATE_CORE_FILE] msgbaseId{}: ",msgbaseId);
                    }else{
                        log.info("[FAIL_TEMPLATE_FORM_UPDATE_CORE_FILE] msgbaseId{}: ",msgbaseId);
                    }

                } else {
                    builder.setMessage("Update Fail");
                    builder.setResult(ResponseBuilder.Result.FAIL).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","-99");
                    returnVal.put("message","Update Fail");
                }
            } else {//insert
                int insertData = templateService.insertTemplateForm(insertForm);
                if (insertData == 100) {
                    builder.setMessage("Insert Success");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","100");
                    returnVal.put("message","Insert Success");

                    int coreFile = util.coreFile("TEMPLATE_FORM", msgbaseId, "insert");

                    if (coreFile == 100){
                        log.info("[SUCCESS_TEMPLATE_FORM_UPDATE_CORE_FILE] brandId{}: ",msgbaseId);
                    }else{
                        log.info("[FAIL_TEMPLATE_FORM_UPDATE_CORE_FILE] brandId{}: ",msgbaseId);
                    }

                } else {
                    builder.setMessage("Insert Fail");
                    builder.setResult(ResponseBuilder.Result.FAIL).build();

                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","-99");
                    returnVal.put("message","Insert Fail");
                }
            }

        }else {
            builder.setMessage("API ACCESS FILE [CODE] : "+result.getStatusCodeValue()) ;
            builder.setResult(ResponseBuilder.Result.FAIL).build();
            returnVal.put("bizResult",bizResult.toString());
            returnVal.put("result","-98");
            returnVal.put("message","API CODE NOT 200 [CODE] : "+result.getStatusCodeValue());
        }
        return returnVal;
    }


    /**브랜드 권한 재위임 - 해당 브랜드내에 있는 템플릿 정보 다시 조회하여 TEMPLATE테이블 업로드
     * (webhook으로 brand타입이 created이면서 해당 brandID값이 브랜드 테이블에 있을 경우 실행됨)
     * 1. 특정브랜드 재 권한위임시 그 안에 속해있는 템플릿 del_yn->N처리 및
     * 2. 새로운데이터/수정데이터 여부 확인하여 insert or update작업,
     * 3. core 전달 파일 생성
     *
     * (index_text, card_type, spec, product_code, agency_id,btn_idx 값들은
     * 'getBrandTemplateDetail'(템플릿 정보 상세 조회)을 통해 받아올 수 있음)
     *
     * @author Moon
     * @exception IOException
     * @exception ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    public void reCreatedTemplateChk(String brandId){
        log.info("Call reCreatedTemplateChk");
        int returnVal = -99;
        //List<Map<String,String>> btnInsertList = new ArrayList<>();
        int btnInsertCnt = 0;

        String getToken = util.getTokenVal();

        Map<Object, String> map = new HashMap<>();
        map.put("partition",util.getTodayPartition(0));
        map.put("brand_id",brandId);
        //현재일자의 해당 브랜드 아이디 값을 가지고 있으며, del_yn값이 1인 ID값 가져오기

        Set<String> oldTmplId = templateService.getOldTmplIdList(map);
        String insertIdList = "";
        String updateIdList = "";
        //String tmplBtnUpdateList = "";

        String brand_key = brandService.selectBrandKey(brandId);
        String getUrl =   Constants.BIZ_API+"brand/" + brandId + "/messagebase";
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
            Object obj = null;
            try {
                obj = jsonParser.parse(body);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            JSONObject jsonObj = (JSONObject) obj;

            String resultObj = jsonObj.get("result").toString();

            try {
                obj = jsonParser.parse(resultObj);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            jsonObj = (JSONObject) obj;
            JSONArray templateList = (JSONArray) jsonObj.get("tmplts");

            for (Object o : templateList) {
                JSONObject thisTmpt = (JSONObject) o;

                Map<Object, String> insertTemplate = templateService.formatTemplateList(thisTmpt);
                log.info("insertTemplate {} :", insertTemplate);

                String messagebase_id = insertTemplate.get("messagebase_id");

              /*  if(insertTemplate.get("approval_result").equals("1")){
                    Map<String,String> btnInfo =new HashMap<>();
                    btnInfo.put("brandId",insertTemplate.get("brand_id"));
                    btnInfo.put("messagebaseId",insertTemplate.get("messagebase_id"));
                    btnInsertList.add(btnInsertCnt,btnInfo);
                    btnInsertCnt++;
                }*/

                if (oldTmplId.remove(messagebase_id)) {//update
                    if ("".equals(updateIdList)) {
                        updateIdList += messagebase_id;
                    } else {
                        updateIdList += "," + messagebase_id;
                    }


                    int updateData = templateService.updatetemplate(insertTemplate);

                    if (updateData == 100) {
                        log.info("[TEMPLATE UPDATE SUCCESS] messgebase_id : {}", messagebase_id);
                    } else {
                        log.info("[TEMPLATE UPDATE FAIL] messgebase_id : {}", messagebase_id);
                    }

                } else {//insert
                    if ("".equals(insertIdList)) {
                        insertIdList += messagebase_id;
                    } else {
                        insertIdList += "," + messagebase_id;
                    }
                    int insertData = templateService.insertTemplate(insertTemplate);

                    if (insertData == 100) {
                        log.info("[TEMPLATE INSERT SUCCESS] messgebase_id : {}", messagebase_id);
                    } else {
                        log.info("[TEMPLATE INSERT FAIL] messgebase_id : {}", messagebase_id);
                    }
                }
            }
           /* if(btnInsertList.size() >0){
                //버튼 insesrt작업
                int btncnt = templateService.insertTemplateBtn(btnInsertList,"0");
            }*/

           /* if(!tmplBtnUpdateList.equals("")){
                int updateTempBtn = util.coreFile("TEMPLATE_BTN", tmplBtnUpdateList, "update");

                if (updateTempBtn == 100){
                    log.info("[reCreated - SUCCESS_TEMPLATE_BTN UPDATE_CORE_FILE] messagebaseId List: {} ",updateIdList);
                }else{
                    log.info("[reCreated - SUCCESS_TEMPLATE_BTN UPDATE_CORE_FILE] messagebas`eId List: {} ",updateIdList);
                }
            }*/

            if(!updateIdList.equals("")){
                int updateTemp = util.coreFile("TEMPLATE", updateIdList, "update");
                if (updateTemp == 100){
                    log.info("[reCreated - SUCCESS_TEMPLATE UPDATE_CORE_FILE] messagebaseId List: {} ",updateIdList);
                }else{
                    log.info("[reCreated - SUCCESS_TEMPLATE UPDATE_CORE_FILE] messagebas`eId List: {} ",updateIdList);
                }
            }

            if(!insertIdList.equals("")){
                int insertTemp =  util.coreFile("TEMPLATE", insertIdList, "insert");
                if (insertTemp == 100){
                    log.info("[reCreated - SUCCESS_TEMPLATE INSERT_CORE_FILE] messagebaseId List: {} ",insertIdList);
                }else{
                    log.info("[reCreated - SUCCESS_TEMPLATE INSERT_CORE_FILE] messagebaseId List: {} ",insertIdList);
                }
            }
        }
    }

}
