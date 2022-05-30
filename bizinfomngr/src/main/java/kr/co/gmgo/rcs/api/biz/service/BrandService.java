package kr.co.gmgo.rcs.api.biz.service;


import kr.co.gmgo.rcs.api.biz.controller.UtilController;
import kr.co.gmgo.rcs.api.biz.mapper.BrandMapper;
import kr.co.gmgo.rcs.api.biz.common.Constants;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class BrandService {

    private Logger log = LoggerFactory.getLogger("log.rcsLog");
    @Autowired
    BrandMapper brandMapper;
    @Autowired
    UtilController util;

    /**
     * 해당 일자 Partition BRAND테이블 내에 brand_id 유무 check
     * @author Moon
     * @return 등록된 brand_id의 수
     */
    public int selectBrandIdInbrand(String brandId, String partition){
        return brandMapper.selectBrandIdInbrand(brandId, partition);
    }

    /**
     * 브랜드 테이블 update(개별)
     * @author Moon
     * @return 성공여부(-99 : 실패 , 100 : 성공)
     */
    public int updateBrand(Map<Object,String> params){
        int returnCode = -99;
        int updateData = brandMapper.updateBrand(params);

        if(updateData > 0){
            returnCode = 100;
        }
        return returnCode;
    }

    /**
     * 브랜드 테이블 insert(개별)
     * @author Moon
     * @return 성공여부(-99 : 실패 , 100 : 성공)
     */
    public int insertBrand(Map<Object,String> params){
        int returnCode = -99;
        int updateData = brandMapper.insertBrand(params);

        if(updateData > 0){
            returnCode = 100;
        }
        return returnCode;
    }


    /**
     * 등록 브랜드 정보 DB insert
     * 스케줄러를 통해 자동 insert (매일 24시)
     *
     * @param compareDate coreFile 생성시 비교할 이전 날짜 ex)date= 1 > 현재 insert하는 데이터와 어제 파티션의 데이터 비교하여 corefile생성
     * @param regDate 서버 재 구동하여 호출 시 넘겨주는 날짜 값으로 해당 값이 null이 아니면 등록날짜에 해당 값이 들어간다.
     *                22시-24시 사이에 재구동하여 호출 시 오늘로부터 내일날짜값이 넘어오고
     *                그 외의 시간에는 오늘날짜를 넘겨주고있음.
     * @author Moon
     * @exception IOException
     * @exception  ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     *
     */
    public int getBrandDetail(String compareDate, String regDate) throws ParseException {
        log.info("Call Brand API");
        int returnVal = -99;

        boolean duplicate = util.compareDate(regDate);
        log.info("duplicate : " +duplicate );
        String getToken = util.getTokenVal();
        String agencyId = Constants.clientId;
        Map<String, Object> brandInfo = util.getBrandInfo();
        String getBrandList = brandInfo.get("brandList").toString();
        String getCorpList = brandInfo.get("corpList").toString();
        List<String> brandArr = Arrays.asList(getBrandList.split(","));
        List<String> corpArr = Arrays.asList(getCorpList.split(","));
        String getDate = UtilController.getDate();
        String fileNamebrand = "brand_"+getDate+".txt";
        File fileBrand = new File(Constants.PATH_FILE_ROOT + fileNamebrand);

        if (fileBrand.exists()) {
            try {
                fileBrand.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

       /* String fileNamefile = "brandFile_"+getDate+".txt";
        File fileBrandFile = new File(Constants.PATH_FILE_ROOT + fileNamefile);

        if (fileBrandFile.exists()) {
            fileBrandFile.createNewFile();
        }*/

        BufferedWriter fw = null;
        try {
            fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileBrand.getPath()), StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int chkNum = 0;
        //BufferedWriter fwFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileBrandFile.getPath()), "UTF-8"));
        for (int i = 0; brandArr.size() > i; i++) {
            String brandId = brandArr.get(i);
            String corpId = corpArr.get(i);
            String getUrl = Constants.BIZ_API + "agency/" + agencyId + "/brand/" + brandId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", getToken);
            HttpEntity<String> entity = new HttpEntity(headers);

            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET, entity, String.class);

            if (result.getStatusCodeValue() == 200) {
                String js = result.getBody();
                JSONParser jsonParser = new JSONParser();

                Object obj = jsonParser.parse(js);
                net.minidev.json.JSONObject jsonObj = (net.minidev.json.JSONObject) obj;
                String resultObj = jsonObj.get("result").toString();

                Object thisList = jsonParser.parse(resultObj);
                jsonObj = (net.minidev.json.JSONObject) thisList;
                String brand_reg_dt = jsonObj.get("registerDate").toString();
                String status = jsonObj.get("status").toString();


                /*
                브랜드 파일 관련 코드. 현재는 DB에 쌓고있지 않음

               JSONArray mediaUrl = (JSONArray) jsonObj.get("mediaUrl");

                net.minidev.json.JSONObject items = null;
                Map<Object, String> paramMap = new HashMap<>();


               String insertFileArr = "";
                for (int j = 0; mediaUrl.size() > j; j++) {
                    items = (net.minidev.json.JSONObject) mediaUrl.get(j);
                    paramMap.put("brand_id", brandId);
                    paramMap.put("file_id", items.get("fileId").toString());
                    paramMap.put("file_url", items.get("url").toString());
                    paramMap.put("file_reg_dt", brand_reg_dt);

                    if (items.get("typeName").toString().equals("icon")) {
                        paramMap.put("type_name", "0");
                    } else if (items.get("typeName").toString().equals("profile")) {
                        paramMap.put("type_name", "1");
                    } else if (items.get("typeName").toString().equals("background")) {
                        paramMap.put("type_name", "2");
                    }
                    //브랜드 파일 데이터 현재 쌓고있지 않음
                    insertFile = brandService.insertFile(paramMap);
                    String insertFileId = items.get("fileId").toString();
                    if (j == 0) {
                        insertFileArr += insertFileId;
                    } else {
                        insertFileArr += "," + insertFileId;
                    }
                    Map<Object, String> writeTxt = UtilController.writeTxt(paramMap);
                    String colListBtn = writeTxt.get("colListBtn");
                    String valListBtn = writeTxt.get("valListBtn");

                    if (i == 0 && j == 0) {
                        fwFile.write(colListBtn);
                        fwFile.write(valListBtn);
                    } else {
                        fwFile.write(valListBtn);
                    }
                }*/

                Map<Object, String> insertBrandData = new HashMap<>();
                insertBrandData.put("brand_id", brandId);
                insertBrandData.put("brand_name", jsonObj.get("name").toString());
                insertBrandData.put("brand_reg_dt", jsonObj.get("registerDate").toString().replace("T", " ").substring(0, 19));
                insertBrandData.put("update_dt", jsonObj.get("updateDate").toString().replace("T", " ").substring(0, 19));
                insertBrandData.put("brand_key", jsonObj.get("brandKey").toString());
                insertBrandData.put("corp_id",corpId);

                if(status.startsWith("승인대기")){
                    insertBrandData.put("status", "0");

                }else if(status.startsWith("승인")){
                    insertBrandData.put("status", "1");

                }else if(status.startsWith("반려")){
                    insertBrandData.put("status", "2");

                }else if(status.startsWith("검수완료")){
                    insertBrandData.put("status", "3");

                }else if(status.startsWith("저장")){
                    insertBrandData.put("status", "4");

                }

                log.info("insertBrandData  :  {} :", insertBrandData);

                int chkId;
                if (!duplicate) {
                    chkId = 0;
                } else {
                    chkId = brandMapper.selectBrandIdInbrand(brandId, util.getTodayPartition(0));
                }

                if (chkId == 0) { //insert
                    Map<Object, String> writeTxt = UtilController.writeTxt(insertBrandData);
                    String colListBtn = writeTxt.get("colListBtn");
                    String valListBtn = writeTxt.get("valListBtn");

                    if (chkNum <= 0) {
                        try {
                            fw.write(colListBtn);
                            fw.write(valListBtn);
                            chkNum += 1;
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
                } else { //update
                    String concat = insertBrandData.get("brand_key") + insertBrandData.get("brand_name") + insertBrandData.get("status");

                    log.info("=======concat" + concat);
                    int chkDefaultData = brandMapper.selectBrandUpdateChk(brandId, concat, util.getTodayPartition(0));

                    log.info("=======chkDefaultData" + chkDefaultData);
                    if (chkDefaultData == 0) {
                        insertBrandData.put("brandId", brandId);
                        int updateData = updateBrand(insertBrandData);
                        log.info("updateData" + updateData);
                    }
                }
            }
        }

        try {
            fw.close();
            //fwFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }



        String branPath = Constants.PATH_FILE_ROOT + fileNamebrand;
        // String brandFilePath = Constants.PATH_BRAND_FILE_ROOT +  fileNamefile;

        Map<String, Object> insertMap = new HashMap<>();

        insertMap.put("regDate",regDate);
        insertMap.put("data",branPath);
        insertMap.put("compareDate",compareDate);
        int brandCnt = insertBrandAuto(insertMap);
        //int brandFileCnt = insertBrandFileAuto(brandFilePath);

        if(brandCnt >= 1){
            log.info("[BRAND] Insert Success");
            //INSERT성공시에만 해당 파일 삭제
            fileBrand.delete();
            returnVal = 100;
            log.info("[BRAND] returnVal : {}",returnVal);
        }else if(compareDate.equals("0")){
            fileBrand.delete();
        } else{
            log.info("[BRAND] Insert Fail or NULL");
            returnVal = -99;
            log.info("[BRAND] returnVal : {}",returnVal);
        }

        /*if(brandFileCnt >= 1){
            log.info("[BRAND_FILE] Insert Success");
            //INSERT성공시에만 해당 파일 삭제
            fileBrandFile.delete();
        }else{
            log.info("[BRAND_FILE] Insert Fail or NULL");
        }*/
        return returnVal;
    }



    /**
     * 브랜드 테이블 스케줄러를 통해 자동 insert(매일 24시, file, 대량)
     * @author Moon
     */
    public int insertBrandAuto(Map<String,Object> map){
        return brandMapper.insertBrandAuto(map);
    }

    //public int insertBrandFileAuto(String date){return brandMapper.insertBrandFileAuto(date); }


    /**
     * 해당 브랜드의 brand_key조회
     * @author Moon
     * @return brand_key
     */
    public String selectBrandKey(String brandId){
        return brandMapper.selectBrandKey(brandId);
    }


    /**
     * 해당일자의 파티션 브랜드에 등록된 brand_id List조회
     * @author Moon
     * @return brand_id List
     */
    public Map<String,String> selectBrandIdList(){
        Map<String,String> brandData = new HashMap<>();
        String todayPartition = util.getTodayPartition(0);//"p"+today;

        List<Map<String,String>> idList = brandMapper.selectBrandIdList(todayPartition);

        String idArr = "";
        String keyArr = "";

        //해당날짜의 파티션에 브랜드 데이터가 없을경우 전일자의 데이터 가져오기
        if(idList.size() == 0){
            todayPartition = util.getTodayPartition(-1);
            idList = brandMapper.selectBrandIdList(todayPartition);
        }

        if(idList.size() > 0){
            for (int i = 0; idList.size() > i; i++){
                String thisId = idList.get(i).get("BRAND_ID");
                String thisKey = idList.get(i).get("BRAND_KEY");
                if(i == 0){
                    idArr += thisId;
                    keyArr += thisKey;
                }else{
                    idArr += ","+thisId;
                    keyArr += ","+thisKey;
                }
            }
            brandData.put("brandId",idArr);
            brandData.put("brandKey",keyArr);

        }else {
            brandData = null;
        }
        log.info("GET BRANDID, BRANDKEY : {}", brandData.toString());
        return brandData;
    }

}
