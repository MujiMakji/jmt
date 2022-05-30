package kr.co.gmgo.rcs.api.biz.mapper;


import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface SchedulerMapper {
   int updateBrand(String num);
   void updateBrandFile(String num);
   int updateTemplate(String num);
   //void updateTemplateBtn(String num);
   int updateChatbot(String num);
   int updateTemplateForm(String num);

   int selectBtnMaxIdx();


}
