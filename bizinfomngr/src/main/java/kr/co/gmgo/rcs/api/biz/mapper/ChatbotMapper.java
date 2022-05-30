package kr.co.gmgo.rcs.api.biz.mapper;


import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatbotMapper {
    int selectChatbotId(String chatbotId);
    int updateChatbot(Map<Object,String> params);
    int insertChatbot(Map<Object,String> params);

    int insertChatbotAuto(Map<String,Object> map);
    int insertChatbots(String date);

    //webHook delete처리
    int updateChatbotDelete(Map<Object,String> params);


    String selectDelChat(Map<Object, String> params);
    String selectNewChat(Map<Object, String> params);

    List<Map<Object, String>> todayChatList(Map<Object, String> params);

    //브랜드 delete후 del_yn =N처리 된 챗봇리스트
    String selectDelChatList(Map<Object, String> params);
    int selectChatbotUpdateChk(String chatbot_id,String concat, String partition);


}
