package kr.co.gmgo.rcs.api.biz.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Aaron
 * Client Reuest에대한 Response(http status code, result code, result message, etc..)를 빌드한다.
 */
public class ResponseBuilder {


    /**
     * result code key name
     */
    private static final String KEY_RESULT_CODE = "code";
    /**
     * result message key name
     */
    private static final String KEY_MESSAGE = "message";
    /**
     * result message key name
     */
    private static final String BIZ_STATUS = "status";

    /**
     * response header
     */
    private HttpHeaders headers;

    /**
     * byte type resource of response
     */
    private byte[] resource;
    /**
     * result data presets(http status code, result code, message)
     */
    private Result result;
    /**
     * result message
     */
    private String message;
    /**
     * result code, result message map
     */
    private Map<String, Object> resultMap;

    /**
     * data map
     */
    private Map<String, Object> dataMap;

    /**
     * result data presets(http status code, result code, message)
     */
    private String status;
    /**
     * result data presets enum
     */
    public enum Result {
        SUCCESS("1000", "Success","200", HttpStatus.OK),
        FAIL("1001", "FAIL","",HttpStatus.OK),
        BIZ_STAUTS_SUCCESS("200","Success","200", HttpStatus.OK);//webhook수신후 BIZ Center쪽에 전달하는 코드(정상)

        private String code;
        private String message;
        private HttpStatus httpStatus;
        private String status;

        Result(String code, String message, String status, HttpStatus httpStatus){
            this.code = code;
            this.message = message;
            this.httpStatus = httpStatus;
            this.status = status;
        }

        public String getCode(){
            return code;
        }

        public String getMessage(){
            return message;
        }
        public String getStatus(){
            return status;
        }

        public void setMessage(String message){
            this.message = message;
        }

        public HttpStatus getHttpStatus(){
            return httpStatus;
        }

        public void setStatus(String status){
            this.status = status;
        }
    }


    /**
     * constructor
     */
    public ResponseBuilder(){
        dataMap = new HashMap<>();
    }

    /**
     * return response http header
     * @return http header
     */
    public HttpHeaders getHeaders() {
        return headers;
    }

    /**
     * set http header for response
     * @param headers http header for response
     * @return ResponseBuilder instance
     */
    public ResponseBuilder setHeaders(HttpHeaders headers) {
        this.headers = headers;
        return this;
    }

    /**
     * return response resource
     * @return response resource
     */
    public Object getResource() {
        return resource;
    }

    /**
     * set resource for response
     * @param resource resource for response
     * @return ResponseBuilder instance
     */
    public ResponseBuilder setResource(byte[] resource){
        this.resource = resource;
        return this;
    }

    /**
     * set response result
     * @param result response result
     * @return ResponseBuilder instance
     */
    public ResponseBuilder setResult(Result result){
        this.result = result;
        return this;
    }

    /**
     * set response message
     * @param message response message
     * @return ResponseBuilder instance
     */
    public ResponseBuilder setMessage(String message){
        this.message = message;
        return this;
    }
    /**
     * set response message
     * @param status response message
     * @return ResponseBuilder instance
     */
    public ResponseBuilder setStatus(String status){
        this.status = status;
        return this;
    }
    /**
     * add response header
     * @param name header name(key)
     * @param value header value
     * @return ResponseBuilder instance
     */
    public ResponseBuilder addHeader(String name, String value){
        if(headers == null) headers = new HttpHeaders();
        headers.add(name, value);
        return this;
    }

    /**
     * add response data
     * @param data response data to add ex) model object, map, etc.
     * @return ResponseBuilder instance
     */
    public ResponseBuilder addData(Object data){
        String className = data.getClass().getSimpleName();
        dataMap.put(className.substring(0, 1).toLowerCase() + className.substring(1), data);
        return this;
    }

    /**
     * add response data
     * @param name data name
     * @param data data value
     * @return ResponseBuilder instance
     */
    public ResponseBuilder addData(String name, Object data){
        dataMap.put(name, data);
        /*if(data instanceof Collection || data instanceof Map || data instanceof Object[]){
            dataMap.put(name, data);
        }else{
            addData(data);
        }*/
        return this;
    }

    /**
     * build response entity
     * @return built response entity
     */
    public ResponseEntity<Object> build(){
        resultMap = new HashMap<>();

        if(result == null) return null;
        if(message != null) result.setMessage(message);


        if(resource == null){
            resultMap.put(KEY_RESULT_CODE, result.getCode());
            resultMap.put(KEY_MESSAGE, result.getMessage());
            resultMap.put(BIZ_STATUS, result.getStatus());
            resultMap.putAll(dataMap);

            if(headers != null) return new ResponseEntity<Object>(resultMap, headers, result.getHttpStatus());

            return new ResponseEntity<Object>(resultMap, result.getHttpStatus());
        }else{

            if(headers != null) return new ResponseEntity<Object>(resource, headers, result.getHttpStatus());

            return new ResponseEntity<Object>(resource, result.getHttpStatus());
        }

    }
}
