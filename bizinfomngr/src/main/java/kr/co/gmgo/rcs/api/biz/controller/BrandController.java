package kr.co.gmgo.rcs.api.biz.controller;


import kr.co.gmgo.rcs.api.biz.common.Constants;
import kr.co.gmgo.rcs.api.biz.common.ResponseBuilder;
import kr.co.gmgo.rcs.api.biz.service.BrandService;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.ParseException;
import net.minidev.json.parser.JSONParser;
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
import java.util.*;


@Controller
@RequestMapping("/brand")
public class BrandController {

    private Logger log = LoggerFactory.getLogger("log.rcsLog");
    @Autowired
    BrandService brandService;

    @Autowired
    UtilController util;


    /**
     * 브랜드 개별 조회 후 DB upload
     *
     * @throws ParseException 변환하려는 패턴이 입력되는 패턴과 같지 않을 경우
     * @author Moon
     */
    @GetMapping("/{agencyId}/detail/{brandId}")
    public ResponseEntity<Object> getBrand(@PathVariable(value = "agencyId") String agencyId,
                                           @PathVariable(value = "brandId") String brandId,
                                           HttpServletRequest req){
        Map<String,String> data = new HashMap<>();

        ResponseBuilder builder = new ResponseBuilder();
        boolean ipCheck = util.ipCheck(req);
        if(ipCheck){
            Map<String,String> response = null;
            try {
                response = getBrandDetail(agencyId, brandId);
                String bizCode = response.get("bizResult");
                String message = response.get("message");
                String code = response.get("result");

                if(bizCode.equals("200")){
                    if(!code.equals("100")){//실패
                        log.info("message :{}","BRAND " + message);
                        log.info("Bizcode :{}", bizCode);
                        builder.setResult(ResponseBuilder.Result.FAIL).setMessage(message).build();
                    }else{
                        log.info("message :{}","BRAND " + message);
                        log.info("Bizcode :{}", bizCode);
                        builder.setResult(ResponseBuilder.Result.SUCCESS).build();
                    }
                }else{
                    log.info("message :{}", message);
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
    } ;


    public  Map<String,String> getBrandDetail(
            @PathVariable(value = "agencyId") String agencyId,
            @PathVariable(value = "brandId") String brandId) throws ParseException {
        log.info("method name is {}", "/{agencyId}/detail/{brandId}");

        String getToken = util.getTokenVal();
        String getUrl =  Constants.BIZ_API+"agency/" + agencyId + "/brand/" + brandId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", getToken);
        HttpEntity<String> entity = new HttpEntity(headers);

        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> result = rest.exchange(getUrl, HttpMethod.GET, entity, String.class);
        Object bizResult = result.getStatusCodeValue();
        Map<String,String> returnVal = new HashMap<>();

        if (result.getStatusCodeValue() == 200) {

            String js = result.getBody();
            JSONParser jsonParser = new JSONParser();

            Object obj = jsonParser.parse(js);
            JSONObject jsonObj = (JSONObject) obj;
            String resultObj = jsonObj.get("result").toString();

            Object thisList = jsonParser.parse(resultObj);
            jsonObj = (JSONObject) thisList;

            Map<Object, String> insertBrandData = new HashMap<>();
            insertBrandData.put("brandId", brandId);
            insertBrandData.put("brand_name", jsonObj.get("name").toString());
            insertBrandData.put("brand_reg_dt", jsonObj.get("registerDate").toString().replace("T", " ").substring(0, 19));
            insertBrandData.put("update_dt", jsonObj.get("updateDate").toString().replace("T", " ").substring(0, 19));
            insertBrandData.put("brand_key", jsonObj.get("brandKey").toString());
            //insertBrandData.put("corp_id",jsonObj.get("corpId").toString());
            String status = jsonObj.get("status").toString();

            switch (status) {
                case "승인대기":
                    insertBrandData.put("status", "0");
                    break;
                case "승인":
                    insertBrandData.put("status", "1");
                    break;
                case "반려":
                    insertBrandData.put("status", "2");
                    break;
                case "검수완료":
                    insertBrandData.put("status", "3");
                    break;
                case "저장":
                    insertBrandData.put("status", "4");
                    break;
            }

            int chkCnt = brandService.selectBrandIdInbrand(brandId,util.getTodayPartition(0));

            if (chkCnt > 0) {
                int updateData = brandService.updateBrand(insertBrandData);
                if (updateData == 100) {
                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","100");
                    returnVal.put("message","Update Success");
                        /*int coreFile = utilController.coreFile("BRAND", brandId, "update");

                        if (coreFile == 100){
                            log.info("[SUCCESS_BRAND_UPDATE_CORE_FILE] brandId{}: ",brandId);
                        }else{
                            log.info("[FAIL_BRAND_UPDATE_CORE_FILE] brandId{}: ",brandId);
                        }*/

                } else {
                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","-99");
                    returnVal.put("message","Update Fail");
                }
            } else {//insert
                int insertData = brandService.insertBrand(insertBrandData);
                if (insertData == 100) {
                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","100");
                    returnVal.put("message","Insert Success");

                       /* int coreFile = utilController.coreFile("BRAND", brandId, "insert");

                        if (coreFile == 100){
                            log.info("[SUCCESS_BRAND_UPDATE_CORE_FILE] brandId{}: ",brandId);
                        }else{
                            log.info("[FAIL_BRAND_UPDATE_CORE_FILE] brandId{}: ",brandId);
                        }*/

                } else {
                    returnVal.put("bizResult",bizResult.toString());
                    returnVal.put("result","-99");
                    returnVal.put("message","Insert Fail");
                }
            }
        } else {

            returnVal.put("bizResult",bizResult.toString());
            returnVal.put("result","-98");
            returnVal.put("message","API CODE NOT EQUALS 200 [BIZ CODE] : "+result.getStatusCodeValue());
        }
        return returnVal;
    }
}

