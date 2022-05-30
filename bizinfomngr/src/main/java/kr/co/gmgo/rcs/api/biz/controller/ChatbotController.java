package kr.co.gmgo.rcs.api.biz.controller;

import kr.co.gmgo.rcs.api.biz.common.Constants;
import kr.co.gmgo.rcs.api.biz.common.ResponseBuilder;
import kr.co.gmgo.rcs.api.biz.service.BrandService;
import kr.co.gmgo.rcs.api.biz.service.ChatbotService;
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
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/chatbot")
public class ChatbotController {
    private Logger log = LoggerFactory.getLogger("log.rcsLog");

    @Autowired
    ChatbotService chatbotService;
    @Autowired
    BrandService brandService;
    @Autowired
    UtilController util;


    @GetMapping("{brandId}/detail/{chatbotId}")
    public ResponseEntity<Object> getChatbot(@PathVariable("brandId") String brandId,
                                             @PathVariable("chatbotId") String chatbotId,
                                             HttpServletRequest req){
        ResponseBuilder builder = new ResponseBuilder();
        boolean ipCheck = util.ipCheck(req);
        if(ipCheck){
            try {
                Map<String,String> response = getChatDetail(brandId, chatbotId);
                String bizCode = response.get("bizResult");
                String message = response.get("message");
                String code = response.get("result");

                if(bizCode.equals("200")){
                    if(!code.equals("100")){//실패
                        log.info("message :{}","CHATBOT "+ message);
                        log.info("bizCode :{}", bizCode);
                        builder.setResult(ResponseBuilder.Result.FAIL).setMessage(message).build();
                    }else{
                        log.info("message :{}","CHATBOT "+  message);
                        log.info("bizCode :{}", bizCode);
                        builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                    }
                }else{
                    log.info("message :{}","CHATBOT "+ message);
                    log.info("bizCode :{}", bizCode);
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
     * 챗봇 정보 개별 조회
     *
     * @throws IOException
     * @throws ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     * @author Moon
     */
    public Map<String, String> getChatDetail(@PathVariable("brandId") String brandId,
                                             @PathVariable("chatbotId") String chatbotId) throws ParseException {
        log.info("method name is {}", "/chatbot/{brandId}/detail/{chatbotId}");
        ResponseBuilder builder = new ResponseBuilder();

        String getToken = util.getTokenVal();
        Object brandKey = brandService.selectBrandKey(brandId);
        String getUrl =  Constants.BIZ_API+"brand/" + brandId + "/chatbot/" + chatbotId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", getToken);
        headers.set("x-rcs-brandkey", brandKey.toString());
        HttpEntity entity = new HttpEntity(headers);

        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET, entity, String.class);
        Object bizResult = result.getStatusCodeValue();
        Map<String, String> returnVal = new HashMap<>();

        if (result.getStatusCodeValue() == 200) {
            String body = result.getBody();

            JSONParser jsonParser = new JSONParser();
            Object obj = jsonParser.parse(body);
            JSONObject jsonObj = (JSONObject) obj;
            String resultObj = jsonObj.get("result").toString();

            obj = jsonParser.parse(resultObj);
            jsonObj = (JSONObject) obj;
            JSONArray chatbotList = (JSONArray) jsonObj.get("chatbots");

            obj = jsonParser.parse(resultObj);
            jsonObj = (JSONObject) obj;

            Map<Object, String> chatList = new HashMap<>();

            chatList.put("chatbot_id", jsonObj.get("chatbotId").toString());
            chatList.put("brand_id", jsonObj.get("brandId").toString());
            chatList.put("sub_title", jsonObj.get("subTitle").toString());
            chatList.put("display", jsonObj.get("display").toString());
            chatList.put("chatbot_reg_dt", jsonObj.get("registerDate").toString().replace("T", " ").substring(0, 19));
            chatList.put("update_dt", jsonObj.get("updateDate").toString().replace("T", " ").substring(0, 19));
            chatList.put("reg_id", jsonObj.get("registerId").toString());
            chatList.put("sub_num", jsonObj.get("subNum").toString());

            if (jsonObj.get("updateId") != null && jsonObj.get("updateId") != "") {
                chatList.put("update_id", jsonObj.get("updateId").toString());
            } else {
                chatList.put("update_id", "null");
            }

            if (jsonObj.get("approvalDate") != null && jsonObj.get("approvalDate") != "") {
                chatList.put("approval_dt", jsonObj.get("approvalDate").toString().replace("T", " ").substring(0, 19));
            } else {
                chatList.put("approval_dt", "null");
            }


            String service = jsonObj.get("service").toString();
            if (service.equals("a2p")) {
                chatList.put("service", "0");
            } else {
                chatList.put("service", "1");
            }


            String approval_result = jsonObj.get("approvalResult").toString();
            switch (approval_result) {
                case "승인대기":
                    chatList.put("approval_result", "0");
                    break;
                case "승인":
                    chatList.put("approval_result", "1");
                    break;
                case "반려":
                    chatList.put("approval_result", "2");
                    break;
                case "검수완료":
                    chatList.put("approval_result", "3");
                    break;
                case "저장":
                    chatList.put("approval_result", "4");
                    break;
            }

            String main_num = jsonObj.get("isMainNum").toString();
            if (main_num.equals("true")) {
                chatList.put("main_num", "0");
            } else if (main_num.equals("false")) {
                chatList.put("main_num", "1");
            }

            int chkCnt = chatbotService.selectChatbotId(chatbotId);

            if (chkCnt > 0) {
                int updateData = chatbotService.updateChatbot(chatList);
                if (updateData == 100) {
                    builder.setMessage("Update Success");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();

                    returnVal.put("bizResult", bizResult.toString());
                    returnVal.put("result", "100");
                    returnVal.put("message", "Update Success");

                    int coreFile = util.coreFile("CHATBOT", chatbotId, "update");

                    if (coreFile == 100) {
                        log.info("[SUCCESS_CHATBOT_UPDATE_CORE_FILE] chatbotId : {} ", chatbotId);
                    } else {
                        log.info("[FAIL_CHATBOT_UPDATE_CORE_FILE] chatbotId : {} ", chatbotId);
                    }

                } else {
                    builder.setMessage("Update Fail");
                    builder.setResult(ResponseBuilder.Result.FAIL).build();

                    returnVal.put("bizResult", bizResult.toString());
                    returnVal.put("result", "-99");
                    returnVal.put("message", "Update Fail");
                }
            } else {//insert
                int updateData = chatbotService.insertChatbot(chatList);
                if (updateData == 100) {
                    builder.setMessage("Insert Success");
                    builder.setResult(ResponseBuilder.Result.SUCCESS).build();

                    returnVal.put("bizResult", bizResult.toString());
                    returnVal.put("result", "100");
                    returnVal.put("message", "Insert Success");

                    int coreFile = util.coreFile("CHATBOT", chatbotId, "insert");

                    if (coreFile == 100) {
                        log.info("[SUCCESS_CHATBOT_UPDATE_CORE_FILE] chatbotId : {} ", chatbotId);
                    } else {
                        log.info("[FAIL_CHATBOT_UPDATE_CORE_FILE] chatbotId : {} ", chatbotId);
                    }

                } else {
                    builder.setMessage("Insert Fail");
                    builder.setResult(ResponseBuilder.Result.FAIL).build();
                    returnVal.put("bizResult", bizResult.toString());
                    returnVal.put("result", "-99");
                    returnVal.put("message", "Insert Fail");
                }
            }
        } else {
            builder.setMessage("API ACCESS FILE [CODE] : " + result.getStatusCodeValue());
            builder.setResult(ResponseBuilder.Result.FAIL).build();
            returnVal.put("bizResult", bizResult.toString());
            returnVal.put("result", "-98");
            returnVal.put("message", "API CODE NOT 200 [CODE] : " + result.getStatusCodeValue());
        }
        return returnVal;
    }


    /**
     * 전체 챗봇 정보 DB insert
     *
     * @throws IOException
     * @throws ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     * @author Moon
     */
    @GetMapping("/chatbotDetail")
    public int getChatbot(HttpServletRequest req) throws  ParseException {
        log.info("method name is {}", "/chatbotDetail");

        int returnCnt = -99;

        Map<Object, String> insertChatbotMap = new HashMap<>();
        String getToken = util.getTokenVal();
        Map<String, Object> getBrandInfo = util.getBrandInfo();
        String getBrandList = getBrandInfo.get("getBrandList").toString();
        List<String> brandArr = Arrays.asList(getBrandList.split(","));

        Map<Object, String> getData = chatbotList(getToken, brandArr);
        String getDate = UtilController.getDate();
        String fileNamechat = "chatbot" + getDate + ".txt";
        File fileTem = new File(Constants.PATH_FILE_ROOT + fileNamechat);

        if (fileTem.exists()) {
            try {
                fileTem.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        BufferedWriter fw = null;
        try {
            fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileTem.getPath()), StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        List<String> brandList = Arrays.asList(getData.get("brandList").split(","));
        List<String> chatbotList = Arrays.asList(getData.get("chatbotList").split(","));

        for (int i = 0; chatbotList.size() > i; i++) {
            String brandId = brandList.get(i);
            String thisChat = chatbotList.get(i);

            String brandKey = brandService.selectBrandKey(brandId);
            String getUrl =  Constants.BIZ_API+"brand/" + brandId + "/chatbot/" + thisChat;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", getToken);
            headers.set("x-rcs-brandkey", brandKey);
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

                insertChatbotMap.put("chatbot_id", jsonObj.get("chatbotId").toString());
                insertChatbotMap.put("brand_id", jsonObj.get("brandId").toString());
                insertChatbotMap.put("sub_num", jsonObj.get("subNum").toString());
                insertChatbotMap.put("main_num", jsonObj.get("isMainNum").toString());
                insertChatbotMap.put("sub_title", jsonObj.get("subTitle").toString());
                insertChatbotMap.put("display", jsonObj.get("display").toString());
                insertChatbotMap.put("approval_result", jsonObj.get("approvalResult").toString());
                insertChatbotMap.put("chatbot_reg_dt", jsonObj.get("registerDate").toString().replace("T", " ").substring(0, 19));
                insertChatbotMap.put("update_dt", jsonObj.get("updateDate").toString().replace("T", " ").substring(0, 19));

                if (jsonObj.get("updateId") != null && jsonObj.get("updateId") != "") {
                    insertChatbotMap.put("update_id", jsonObj.get("updateId").toString());
                } else {
                    insertChatbotMap.put("update_id", "null");
                }

                if (jsonObj.get("service").toString().equals("a2p")) {
                    insertChatbotMap.put("service", "0");
                } else {
                    insertChatbotMap.put("service", "1");
                }
                //insertChatbotMap.put("service", jsonObj.get("service").toString());
                insertChatbotMap.put("reg_id", jsonObj.get("registerId").toString());


                if (insertChatbotMap.get("approvalDate") != null && !insertChatbotMap.get("approvalDate").equals("")) {
                    insertChatbotMap.put("approval_dt", insertChatbotMap.get("approvalDate").toString().replace("T", " ").substring(0, 19));
                } else {
                    insertChatbotMap.put("approval_dt", "null");
                }
            }

            String approval_result = insertChatbotMap.get("approval_result");
            if (approval_result.equals("승인대기")) {
                insertChatbotMap.put("approval_result", "0");
            } else if (approval_result.equals("승인")) {
                insertChatbotMap.put("approval_result", "1");
            } else if (approval_result.equals("반려")) {
                insertChatbotMap.put("approval_result", "2");
            } else if (approval_result.equals("검수완료")) {
                insertChatbotMap.put("approval_result", "3");
            } else if (approval_result.equals("저장")) {
                insertChatbotMap.put("approval_result", "4");
            }

            String main_num = insertChatbotMap.get("main_num");
            if (main_num.equals("true")) {
                insertChatbotMap.put("main_num", "0");
            } else if (main_num.equals("false")) {
                insertChatbotMap.put("main_num", "1");
            }

            String service = insertChatbotMap.get("service");
            if (service.equals("a2p")) {
                insertChatbotMap.put("service", "0");
            } else {
                insertChatbotMap.put("service", "1");
            }

            Map<Object, String> writeTxt = UtilController.writeTxt(insertChatbotMap);
            String colListBtn = writeTxt.get("colListBtn");
            String valListBtn = writeTxt.get("valListBtn");

            if (i == 0) {
                try {
                    fw.write(colListBtn);
                    fw.write(valListBtn);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                try {
                    fw.write(valListBtn);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String brandPath = Constants.PATH_FILE_ROOT + fileNamechat;
        int insertCnt = chatbotService.insertChatbot(brandPath);

        if(insertCnt > 0){
            returnCnt = 100;
            log.info("[/chatbot/chatbotDetail]CHATBOT INSERT SUCCESS");
        }else{
            log.info("[/chatbot/chatbotDetail]CHATBOT INSERT");
        }

        return returnCnt;
    }

    /**
     * BIZ Center 등록 챗봇ID와 브랜드ID 매핑하여 가져오기
     *
     * @throws IOException
     * @throws ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     * @author Moon
     *
     * @return 챗봇ID List, 브랜드ID List
     */
    private Map<Object, String> chatbotList(String getToken, List<String> getBrandList) throws ParseException {
        log.info("method name is {}", "chatbotList");
        Map<Object, String> chatList = new HashMap<>();

        String chatbotList = "";
        String brandList = "";
        for (int i = 0; getBrandList.size() > i; i++) {
            String brandId = getBrandList.get(i);
            String brandKey = brandService.selectBrandKey(brandId);
            String getUrl =  Constants.BIZ_API+"brand/" + brandId + "/chatbot";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", getToken);
            headers.set("x-rcs-brandkey", brandKey);
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
                resultObj = (jsonObj.get("chatbots").toString()).replace("[{", "").replace("}]", "").replace("},{", "_!@#split!@#_");

                List<String> items = Arrays.asList(resultObj.split("_!@#split!@#_"));
                String test = "";
                for (int j = 0; items.size() > j; j++) {
                    String thisItem = "{" + items.get(j) + "}";
                    Object thisOb = jsonParser.parse(thisItem);
                    JSONObject thisJson = (JSONObject) thisOb;
                    String thisChatbot = thisJson.get("chatbotId").toString();
                    String thisBrandId = thisJson.get("brandId").toString();
                    if (i == 0 && j == 0) {
                        chatbotList += thisChatbot;
                    } else {
                        chatbotList += "," + thisChatbot;
                    }
                    if (i == 0 && j == 0) {
                        brandList += thisBrandId;
                    } else {
                        brandList += "," + thisBrandId;
                    }
                }
            }
            chatList.put("chatbotList", chatbotList);
            chatList.put("brandList", brandList);
        }

        return chatList;
    }


    /**브랜드 권한 재위임 - 해당 브랜드내에 있는 챗봇 정보 다시 조회하여 CHATBOT테이블 업로드
     * (webhook으로 brand타입이 created이면서 해당 brandID값이 브랜드 테이블에 있을 경우 실행됨)
     * 1. 특정브랜드 재 권한위임시 그 안에 속해있는 챗봇 del_yn->N처리 및
     * 2. 새로운데이터/수정데이터 여부 확인하여 insert or update작업,
     * 3. core 전달 파일 생성
     *
     * (index_text, card_type, spec, product_code, agency_id,btn_idx 값들은
     * 'getBrandTemplateDetail'(템플릿 정보 상세 조회)을 통해 받아올 수 있음)
     *
     * @throws IOException
     * @throws ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     * @author Moon
     */
    public void reCreatedChatbotChk(String brandId){
        log.info("method name is {}", "reCreatedChatbotChk");
        int returnVal = -99;
        String getToken = null;
        getToken = util.getTokenVal();

        Map<Object, String> map = new HashMap<>();
        map.put("partition", util.getTodayPartition(0));
        map.put("brand_id", brandId);

        Set<String> oldChatbotId = chatbotService.getOldchatbotIdList(map);
        String insertIdList = "";
        String updateIdList = "";

        String brandKey = brandService.selectBrandKey(brandId);

        String getUrl =  Constants.BIZ_API+"brand/" + brandId + "/chatbot";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", getToken);
        headers.set("x-rcs-brandkey", brandKey);

        HttpEntity entity = new HttpEntity(headers);

        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET, entity, String.class);

        if (result.getStatusCodeValue() == 200) {
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
            JSONArray chatbotList = (JSONArray) jsonObj.get("chatbots");

            for (Object o : chatbotList) {
                JSONObject thisChat = (JSONObject) o;

                Map<Object, String> insertChatbot = chatbotService.formatChatList(thisChat);

                String service = insertChatbot.get("service");
                if (service.equals("a2p")) {
                    insertChatbot.put("service", "0");
                } else {
                    insertChatbot.put("service", "1");
                }

                String chatbot_id = insertChatbot.get("chatbot_id");
                if (oldChatbotId.remove(chatbot_id)) {//update
                    if ("".equals(updateIdList)) {
                        updateIdList += chatbot_id;
                    } else {
                        updateIdList += "," + chatbot_id;
                    }

                    int updateData = chatbotService.updateChatbot(insertChatbot);

                    if (updateData == 100) {
                        log.info("CHATBOT UPDATE SUCCESS chatbotId : {}", chatbot_id);
                    } else {
                        log.info("CHATBOT UPDATE FAIL chatbotId : {}", chatbot_id);
                    }

                } else {//insert
                    if (insertIdList.equals("")) {
                        insertIdList += chatbot_id;
                    } else {
                        insertIdList += "," + chatbot_id;
                    }
                    int insertData = chatbotService.insertChatbot(insertChatbot);

                    if (insertData == 100) {
                        log.info("CHATBOT UPDATE SUCCESS chatbotId : {}", chatbot_id);
                    } else {
                        log.info("CHATBOT UPDATE FAIL chatbotId : {}", chatbot_id);
                    }
                }
            }

            if (!updateIdList.equals("")) {
                int updateTemp = util.coreFile("CHATBOT", updateIdList, "update");
                if (updateTemp == 100) {
                    log.info("[reCreated - SUCCESS_CHATBOT UPDATE_CORE_FILE] chatbotId : {} ", updateIdList);
                } else {
                    log.info("[reCreated - SUCCESS_CHATBOT UPDATE_CORE_FILE] chatbotId : {} ", updateIdList);
                }
            }

            if (!insertIdList.equals("")) {
                int insertTemp = util.coreFile("CHATBOT", insertIdList, "insert");
                if (insertTemp == 100) {
                    log.info("[reCreated - SUCCESS_CHATBOT INSERT_CORE_FILE] chatbot_id : {} ", insertIdList);
                } else {
                    log.info("[reCreated - SUCCESS_CHATBOT INSERT_CORE_FILE] chatbot_id : {} ", insertIdList);
                }
            }
        }
    }
}
