package kr.co.gmgo.rcs.api.biz.controller;

import com.alibaba.fastjson.JSON;
import kr.co.gmgo.rcs.api.biz.common.Constants;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class UtilController {

    private Logger log = LoggerFactory.getLogger("log.rcsLog");
    /**
     * 등록된 대행사의 토큰값 호출
     * *
     * @author Moon
     * @exception  IOException
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     * @return 토큰값
     */
    public String getTokenVal(){

        String resultObj = null;
        String getUrl = Constants.BIZ_API+"token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<Object, String> client_info = new HashMap<>();
        String clientId = Constants.clientId;
        client_info.put("clientId",clientId);
        client_info.put("clientSecret",Constants.clientSecret);

        log.info("clientId : "+clientId);
        log.info("clientSecret : "+Constants.clientSecret);

        String bodyJson = JSON.toJSONString(client_info);
        HttpEntity param = new HttpEntity(bodyJson, headers);
        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> result = rest.postForEntity(getUrl, param, String.class);

        if (result.getStatusCodeValue() == 200) {
            log.info("result : {}",result.getStatusCodeValue());
            String js = result.getBody();
            JSONParser jsonParser = new JSONParser();

            Object obj = null;
            try {
                obj = jsonParser.parse(js);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            net.minidev.json.JSONObject jsonObj = (JSONObject) obj;
            resultObj = jsonObj.get("accessToken").toString();
            log.info("accessToken : "+resultObj);
        }else{
            log.info("accessToken null");
        }
        return resultObj;
    }


    /**
     * file에 쓰일 insert데이터 컬럼, 데이터간 구분자
     * @author Moon
     *
     * @return 구분자를 포함한 insert데이터
     */
    public static Map<Object, String> writeTxt(Map<Object, String> insertData){
        Map<Object,String> writeString = new HashMap<>();
        StringBuilder valListKey = new StringBuilder();
        StringBuilder valList = new StringBuilder();

        for (Map.Entry<Object, String> entry : insertData.entrySet()) {
            String insertKey = (String) entry.getKey();
            String insertValue = entry.getValue();

            if (insertKey == null) {
                valListKey.append("\\N"+"`[$*#!@^]`");
            } else {
                valListKey.append(insertKey + "`[$*#!@^]`");
            }
            if (insertValue == null) {
                valList.append("\\N"+"`[$*#!@^]`");
            } else {
                valList.append(insertValue + "`[$*#!@^]`");
            }
        }

        writeString.put("colListBtn", valListKey + "`{$*@%}`");
        writeString.put("valListBtn", valList + "`{$*@%}`");

        return writeString;

    }

    /**
     * 등록된 브랜드ID조회
     * *
     * @author Moon
     * @exception  IOException
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     * @return 등록된 브랜드ID 리스트
     * TODO 브랜드를 등록한 기업의 ID DB upload!!
     */
    public Map<String, Object> getBrandInfo(){
        Map<String, Object> returnMap = new HashMap<>();
        String brandList = "";
        String corpList = "";
        String getUrl = Constants.BIZ_API+"agency/" + Constants.clientId + "/contract";
        ResponseEntity<String> result = null;

        String getToken = getTokenVal();

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", getToken);
        HttpEntity<String> entity = new HttpEntity(headers);

        RestTemplate rest = new RestTemplate();
        result = rest.exchange(getUrl, HttpMethod.GET, entity, String.class);

        String js =  result.getBody();
        JSONParser jsonParser = new JSONParser();

        Object obj = null;
        try {
            obj = jsonParser.parse(js);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        JSONObject jsonObj = (JSONObject) obj;
        String resultObj = jsonObj.get("result").toString();

        resultObj = resultObj.replace("[{","{").replace("}]","}").replace("},{","}_!@#split!@#_{");
        List<String> items = Arrays.asList(resultObj.split("_!@#split!@#_"));

        for(int i = 0; items.size() > i ; i++){
            Object thisList = null;
            try {
                thisList = jsonParser.parse(items.get(i));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            jsonObj = (JSONObject) thisList;
            String brandId = jsonObj.get("brandId").toString();
            String corpId = jsonObj.get("corpId").toString();
            if(i == 0){
                brandList += brandId;
                corpList += corpId;
            }else{
                brandList += ","+brandId;
                corpList += ","+corpId;
            }
        }
        returnMap.put("brandList", brandList);
        returnMap.put("corpList", corpList);

        return returnMap;
    }


    /**
     * 현재날짜
     * @author Moon
     *
     * @return  yyyyMMdd 형식의 날짜
     */
    public static String getDate(){
        LocalDateTime current = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        String formatted = current.format(formatter);

        return formatted;
    }

    /**
     * 현재일자인 파티션 String 가져오기
     * @author Moon
     *
     * @return 현재일자의 파티션 String ex)p17
     */
    public String getTodayPartition(int sumDate){
        Calendar cal = Calendar.getInstance();
        int today;
        Object formatToday = "null";
        if(sumDate == 0){
            today = cal.get(Calendar.DATE);
        }else{
            today = cal.get(Calendar.DATE)-sumDate;
        }
        formatToday = "p"+today;
        return formatToday.toString();
    }


    /**
     * Core Send file 생성
     * @author Moon
     *
     * @return 파일생성 성공 여부  ( 100 : success , -99 : fail)
     */
    public int coreFile(String table, String id, String stats){
        int returnVal = -99;

        long time = System.currentTimeMillis();
        String tableName = time+"."+table;
        File file = new File(Constants.PATH_CORE_FILE_ROOT+ tableName);
        //PATH_CORE_FILE_ROOT

        try {
            BufferedWriter bwChat = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file.getPath()), StandardCharsets.UTF_8));

            JSONObject jo = new JSONObject(); //메인 JSON
            jo.put("table",table);
            jo.put("action",stats);

            JSONArray  ja_col = new JSONArray();
            switch (table) {
                case "CHATBOT":
                    ja_col.add("chatbot_id");
                    break;
                case "TEMPLATE":
                    ja_col.add("messagebase_id");
                    break;
                case "TEMPLATE_FORM":
                    ja_col.add("form_id");
                    break;
                case "TEMPLATE_BTN":
                    ja_col.add("messagebase_id");
                    break;
                default:
                    log.info("[CORE FILE]table name err!!! table : {}", table);
                    return returnVal;
            }

            JSONArray ja_rows = new JSONArray();

            String[] ids =  id.split(",");

            for(String idr : ids){
                JSONArray ja_row = new JSONArray();
                ja_row.add(idr);
                ja_rows.add(ja_row);
            }

            jo.put("columns",ja_col);
            jo.put("rows",ja_rows);

            bwChat.write(jo.toString());

            bwChat.close();

            log.info("---------"+file.getAbsolutePath());
            if(file.exists()){
                log.info("true");
                returnVal = 100;
            }else{
                log.info("false");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return  returnVal;
    }


    /**
     * 현재일자인 파티션 String 가져오기
     * @author Moon
     *
     * @return 현재일자의 파티션 String ex)p17
     */
    public boolean compareDate(String regDate){
        int compare;
        boolean duplicate;
        String checkDate = regDate.substring(0,10);

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat ( "yyyy-MM-dd");
        String today =format.format(cal.getTime());

        compare = today.compareTo(checkDate);

        //오늘보다 미래 -1
        //같을때 0
        //과거 1
        if(compare < 0){ //같거나 작을때만 중복체크
            duplicate = false;
        }else{
            duplicate = true;
        }
        return duplicate;
    }

    /**
     * 현재일자인 파티션 String 가져오기
     * @author Moon
     *
     * @return 현재일자의 파티션 String ex)현재날짜 7월 17일 -- p17
     */
    public boolean ipCheck(HttpServletRequest req){
        return true;
        /*boolean returnVal = false;
        InetAddress local = null;
        String myIp = null;

        try {
            local = InetAddress.getLocalHost();
            String clientIp = req.getHeader("X-Forwarded-For");
            if (clientIp == null) clientIp = req.getRemoteAddr();

            myIp = local.getHostAddress();
            String serverIp = Constants.IP;

            log.info("=====My IP :" + myIp);
            log.info("=====Server Ip :" + serverIp);
            log.info("=====clientIp :" + clientIp);

            if(serverIp.equals(clientIp)){
                returnVal = true;
                log.info("ipCheck Success");
            }else{
                log.info("ipCheck Fail");
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return returnVal;*/

    }
}
