package kr.co.gmgo.rcs.api.biz.mapper;


import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface TemplateMapper {

    int selectTemplateId(Map<String, Object> params);
    int selectTemplateFormId(String id);

    //자동
    int insertTemplateAuto(Map<String, Object> map);
    int insertTemplateBtn(String data);
    int insertTemplateFormAuto(Map<String, Object> params);
    int insertTemplateBasicAuto(Map<String, Object> params);

    //수동
    int updateTemplate(Map<Object,String> params);
    int updateTemplateBtn(Map<Object,String> params);
    int insertTemplate(Map<Object,String> params);
    int updateTemplateForm(Map<Object,String> params);
    int insertTemplateForm(Map<Object,String> params);

    //webHook delete처리
    int updateTemplateDelete(Map<Object, String> params);
    int updateTemplateBtnDelete(Map<Object, String> params);
    int updateTemplateFormDelete(Map<Object, String> params);



    //update TEMPLATE file create for core
    String selectDelTmpl(Map<Object, String> params);
    String selectNewTmpl(Map<Object, String> params);
    List<Map<Object, String>> todayTemplateList(Map<Object, String> params);
    List<Map<String, Object>> todayTemplateBtnList(Map<Object, String> params);

    //update TEMPLATE BTN file create for core
    String selectDelTmplBtn(Map<Object, String> params);
    String selectNewTmplBtn(Map<Object, String> params);


    //update TEMPLATEFORM file create for core
    String selectDelTmplForm(Map<Object, String> params);
    String selectNewTmplForm(Map<Object, String> params);
    List<Map<Object, String>> todayTemplateFormList(Map<Object, String> params);

    //브랜드 delete후 del_yn = N처리 된 챗봇리스트
    String selectDelTmplList(Map<Object, String> params);

    String selectDelTmplBtnList(Map<Object, String> params);

    int selectTemplateFormUpdateChk(String formId,String concat, String partition);

    int selectTemplateUpdateChk(String messagebaseId,String concat, String partition);

    int selectBtnChk(String messagebaseId,String partition);

    int updateTemplateListDelete(String partition,String value);

    int updateTemplateFormListDelete(String partition,String id,String product);

}
