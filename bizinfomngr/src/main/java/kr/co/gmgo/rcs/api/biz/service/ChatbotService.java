package kr.co.gmgo.rcs.api.biz.service;


import kr.co.gmgo.rcs.api.biz.mapper.ChatbotMapper;
import kr.co.gmgo.rcs.api.biz.common.Constants;
import kr.co.gmgo.rcs.api.biz.controller.UtilController;
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
public class ChatbotService {

    private Logger log = LoggerFactory.getLogger("log.rcsLog");
    @Autowired
    ChatbotMapper chatbotMapper;

    @Autowired
    BrandService brandService;
    @Autowired
    UtilController util;

    /**
     * 해당 일자 Partition CHATBOT테이블 내에 chatbot_id 유무 check
     * @author Moon
     * @return 등록된 chatbot_id 수
     */
    public int selectChatbotId(String chatbotId){
        return chatbotMapper.selectChatbotId(chatbotId);
    }

    /**
     * 챗봇 테이블 update(개별)
     * @author Moon
     * @return 성공여부(-99 : 실패 , 100 : 성공)
     */
    public int updateChatbot(Map<Object,String> params){
        int returnCode = -99;

        params.put("partition", util.getTodayPartition(0));
        int updateData = chatbotMapper.updateChatbot(params);

        if(updateData > 0){
            returnCode = 100;
        }
        return returnCode;
    }


    /**
     * 챗봇 insert(개별)
     * @author Moon
     * @return 성공여부(-99 : 실패 , 100 : 성공)
     */
    public int insertChatbot(Map<Object,String> params){
        int returnCode= - 99;

        params.put("partition", util.getTodayPartition(0));
        int updateData = chatbotMapper.insertChatbot(params);

        if(updateData > 0){
            returnCode = 100;
        }
        return returnCode;
    }


    /**
     * 챗봇 리스트 정보 DB insert
     * 스케줄러 or 수동 호출을 통해 자동 insert (매일 24시)
     *
     * @param compareDate coreFile 생성시 비교할 이전 날짜 ex)date= 1 > 현재 insert하는 데이터와 어제 파티션의 데이터 비교하여 corefile생성
     * @param regDate 서버 재 구동하여 호출 시 넘겨주는 날짜 값으로 해당 값이 null이 아니면 등록날짜에 해당 값이 들어간다.
     *                22시-24시 사이에 재구동하여 호출 시 오늘로부터 내일날짜값이 넘어오고
     *                그 외의 시간에는 오늘날짜를 넘겨주고있음.
     * @author Moon
     * @exception IOException
     * @exception net.minidev.json.parser.ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    public int getChatbotList(String compareDate, String regDate) throws ParseException, IOException {
        log.info("Call Chatbot API");

        boolean duplicate = util.compareDate(regDate);
        int returnVal = -99;
        String getToken = util.getTokenVal();
        Map<String,String> brData =  brandService.selectBrandIdList();
        String getDate = UtilController.getDate();
        String fileNameChatbot = "chatbotList_"+getDate+".txt";
        File fileChat = new File(Constants.PATH_FILE_ROOT + fileNameChatbot);
        BufferedWriter bwChat = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileChat.getPath()), StandardCharsets.UTF_8));
        int chkNum = 0;
        if(fileChat.exists()){
            fileChat.createNewFile();
        }
        Set<String> setYesterCgatList = null;
        if(compareDate.equals("0")){
            Map<Object, String> map = new HashMap<>();
            map.put("partition",util.getTodayPartition(0));
            map.put("notInId",null);
            map.put("dateChk","0");
            setYesterCgatList = yesterdayChat(map);
        }

        if(brData != null){
            List<String> idArr = Arrays.asList(brData.get("brandId").split(","));
            for (String brandId : idArr) {
                String brandKey = brandService.selectBrandKey(brandId);

                String getUrl = Constants.BIZ_API + "brand/" + brandId + "/chatbot";

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
                    JSONArray chatbotList = (JSONArray) jsonObj.get("chatbots");

                    for (int j = 0; chatbotList.size() > j; j++) {
                        JSONObject thisChat = (JSONObject) chatbotList.get(j);
                        log.info("CHATBOT Arr {}", thisChat);
                        Map<Object, String> insertChatbot = formatChatList(thisChat);

                        int chkCnt;
                        if (!duplicate) {
                            chkCnt = 0;
                        } else {
                            chkCnt = selectChatbotId(insertChatbot.get("chatbot_id"));
                        }

                        if (chkCnt == 0) {
                            Map<Object, String> writeTxt = UtilController.writeTxt(insertChatbot);
                            String colList = writeTxt.get("colListBtn");
                            String valList = writeTxt.get("valListBtn");

                            if (chkNum == 0 && j == 0) {
                                bwChat.write(colList);
                                bwChat.write(valList);
                                chkNum += 1;
                            } else {
                                bwChat.write(valList);
                            }
                            if (compareDate.equals("0")) {
                                int updateResult = util.coreFile("CHATBOT", insertChatbot.get("chatbot_id"), "insert");
                                setYesterCgatList.remove(insertChatbot.get("chatbot_id"));
                            }
                        } else {
                            //brand_id,approval_result
                            String concat = insertChatbot.get("brand_id") + insertChatbot.get("approval_result");
                            int chkDefaultData = chatbotMapper.selectChatbotUpdateChk(insertChatbot.get("chatbot_id"), concat, util.getTodayPartition(0));
                            if (compareDate.equals("0")) {
                                setYesterCgatList.remove(insertChatbot.get("chatbot_id"));
                            }
                            if (chkDefaultData == 0) {
                                int updateResult = util.coreFile("CHATBOT", insertChatbot.get("chatbot_id"), "update");
                                updateChatbot(insertChatbot);
                            }

                        }
                    }
                }
            }
            bwChat.close();

            if(compareDate.equals("0")){
                Map<Object,String> delMap = new HashMap<>();
                if(setYesterCgatList != null && setYesterCgatList.size() > 0){
                    String messagebaseIdList = setYesterCgatList.toString().replace(",","','").replace("[","'").replace("]","'").replaceAll(" ","");
                    util.coreFile("CHATBOT", (setYesterCgatList.toString()).replace("[","").replace("]","").replace(" ",""), "delete");
                    delMap.put("dateChk","0");
                    delMap.put("partition",util.getTodayPartition(0));
                    delMap.put("chatbotId",messagebaseIdList);
                    delMap.put("brand_null",null);
                    int vla = chatbotMapper.updateChatbotDelete(delMap);
                    if(vla > 0){
                        log.info("[CHATBOT UPDATE SUCCESS]");
                    }else {
                        log.info("[CHATBOT UPDATE FAIL] List : {} " , delMap);
                    }
                }
            }

            String chatbotPath = Constants.PATH_FILE_ROOT + fileNameChatbot;


            Map<String, Object> insertMap = new HashMap<>();
            insertMap.put("data",chatbotPath);
            insertMap.put("compareDate",compareDate);
            insertMap.put("regDate",regDate);
            int chatCount = insertChatbotAuto(insertMap);

            if(chatCount >= 1){
                log.info("[chatbot] Insert Success");
                //INSERT성공시에만 해당 파일 삭제
                fileChat.delete();
                returnVal = 100 ;
                log.info("[chatbot] returnVal : {}",returnVal);
            }else if(compareDate.equals("0")){
                fileChat.delete();
            }else{
                log.info("[chatbot] Insert Fail or NULL");
                returnVal = -99 ;
                log.info("[chatbot] returnVal : {}",returnVal);
            }
        }else{
            log.info("Chatbot Data empty");
            returnVal = -98 ;
            log.info("[chatbot] returnVal : {}",returnVal);
        }
        return returnVal;
    }

    /**
     * 호출된 template정보 insert작업 전 가공
     *
     * @author Moon
     * @param chatJson 템플릿 정보
     *
     * @return 가공된 template정보
     */
    public Map<Object,String> formatChatList(JSONObject chatJson){

        Map<Object,String> chatList = new HashMap<>();

        chatList.put("chatbot_id", chatJson.get("chatbotId").toString());
        chatList.put("brand_id", chatJson.get("brandId").toString());
        chatList.put("sub_title", chatJson.get("subTitle").toString());
        chatList.put("display", chatJson.get("display").toString());
        chatList.put("chatbot_reg_dt", chatJson.get("registerDate").toString().replace("T", " ").substring(0, 19));
        chatList.put("update_dt", chatJson.get("updateDate").toString().replace("T", " ").substring(0, 19));

        if (chatJson.get("service").toString().equals("a2p")) {
            chatList.put("service", "0");
        } else {
            chatList.put("service", "1");
        }

        //chatList.put("service", chatJson.get("service").toString());
        chatList.put("reg_id", chatJson.get("registerId").toString());
        chatList.put("sub_num", chatJson.get("subNum").toString());

        if(chatJson.get("updateId") != null && chatJson.get("updateId") != "" ){
            chatList.put("update_id", chatJson.get("updateId").toString());
        }else{
            chatList.put("update_id", null);
        }

        if(chatJson.get("approvalDate") != null && chatJson.get("approvalDate") != "" ){
            chatList.put("approval_dt", chatJson.get("approvalDate").toString().replace("T", " ").substring(0, 19));
        }else{
            chatList.put("approval_dt", null);
        }

        String approval_result =  chatJson.get("approvalResult").toString();

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


        String main_num = chatJson.get("isMainNum").toString();
        if(main_num.equals("true")){
            chatList.put("main_num", "0");
        }else if(main_num.equals("false")){
            chatList.put("main_num", "1");
        }

        return chatList;
    }

    /**
     * 챗봇 테이블 스케줄러를 통해 자동 insert(매일 24시, file, 대량)
     * @author Moon
     */
    public int insertChatbotAuto(Map<String,Object> map){
        return  chatbotMapper.insertChatbotAuto(map);
    }

    public int insertChatbot(String date) {
        int insertCnt =  chatbotMapper.insertChatbots(date);
        return insertCnt;
    }


    //기존데이터 삭제 (어제 일자에는 있지만 현재 오늘 일자에는 없는 데이터)
    public String selectDelChat(int date){
        String delId = null;
        Map<Object,String> setPartition = new HashMap<>();
        setPartition.put("today",util.getTodayPartition(0));
        setPartition.put("yesterday",util.getTodayPartition(date));

        delId = chatbotMapper.selectDelChat(setPartition);

        return delId;
    }


    //새로운 데이터 등록 (어제 일자에는 없지만 현재 오늘 일자에는 있는 데이터)
    public String selectNewChat(int date){
        String delId =null;
        Map<Object,String> setPartition = new HashMap<>();
        setPartition.put("today",util.getTodayPartition(0));
        setPartition.put("yesterday",util.getTodayPartition(date));

        delId = chatbotMapper.selectNewChat(setPartition);

        return delId;

    }

    //수정 데이터 확인을 위한 현재 날짜의 데이터 가져오기 (delete데이터, insert데이터 제외)
    public List<Map<Object, String>> todayChatList(Map<Object,String> map){
        return chatbotMapper.todayChatList(map);
    }

    //수정 데이터 확인을 위한 어제 날짜의 데이터 가져오기 (delete데이터, insert데이터 제외)
    public Set<String> yesterdayChat(Map<Object,String> map){
        List<Map<Object, String>> mapdata= chatbotMapper.todayChatList(map);
        String dateChk = map.get("dateChk");
        Set<String> setData = new HashSet<>();
        int size = mapdata.size();
        if(dateChk != null && dateChk.equals("0")){
            for (Map<Object, String> thisMap : mapdata) {
                String thisCol = thisMap.get("CHATBOT_ID");
                setData.add(thisCol);
            }
        }else{
            for (Map<Object, String> thisMap : mapdata) {
                String thisCol = thisMap.get("colAll");
                setData.add(thisCol);
            }
        }
        return setData;
    }

    public Set<String> getOldchatbotIdList(Map<Object,String> map){
        Set<String> oldListSet = new HashSet<>();
        List<Map<Object, String>> oldList = chatbotMapper.todayChatList(map);

        int size = oldList.size();
        for (Map<Object, String> objectStringMap : oldList) {
            String thisCol = objectStringMap.get("CHATBOT_ID");
            oldListSet.add(thisCol);
        }
        return oldListSet;
    }
}
