package kr.co.gmgo.rcs.api.biz.mapper;


import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface BrandMapper {
    int insertFile(Map<Object, String> paramMap);

    //자동(스케줄러  or BIZALL)
    int insertBrandAuto(Map<String,Object> insertData);
    int insertBrandFileAuto(String insertData);

    //개별
    int updateBrand(Map<Object,String> params);
    int insertBrand(Map<Object,String> params);


    //brand테이블에서 brandId확인
    int selectBrandIdInbrand(String brandId, String partition);
    //브랜드 리스트
    List<Map<String,String>> selectBrandIdList(String today);
    //브랜드 key
    String selectBrandKey(String brandId);

    //webHook delete처리
    int updateBrandDelete(Map<Object, String> params);

    int selectBrandUpdateChk(String brandId,String concat, String partition);

    //List<Map<String,String>> todayBrandList(String today);
    //Set<String> getBrandList(Map<Object, String> map);
}
