package kr.co.gmgo.rcs.api.biz.common;

import kr.co.gmgo.rcs.api.biz.Application;
import org.springframework.boot.system.ApplicationHome;

import java.io.File;

public class Constants {


    /*
    1.insert 파일 생성 경로
    */
    //local
//    public static final String PATH_FILE_ROOT = "C:\\TestFileFolder\\";

    // [98번서버] SERVER insert파일 경로
    //public static final String PATH_FILE_ROOT =  File.separator + "DATA" + File.separator + "webmsg" + File.separator + "rcs"+ File.separator + "biz"+ File.separator;

    //[REAL & 208]1.240.13.203insert파일 경로
    public static final String PATH_FILE_ROOT = File.separator + "data2" + File.separator + "rcsmsg" + File.separator + "database"+ File.separator + "biz"+ File.separator + "insert"+ File.separator;



    /*
    2.core file 파일 생성 경로(208서버와 REAL서버 동일한 경로임)
   */
    //local
//    public static final String PATH_CORE_FILE_ROOT = "C:\\TestFileFolder\\";

    //상용
    public static final String PATH_CORE_FILE_ROOT = File.separator + "data2" + File.separator + "rcsmsg" + File.separator + "database"+ File.separator + "core"+ File.separator + "sync"+ File.separator;



    /*
    3.BIZ Center 계정 (clientId, clientSecret)
    */

    public static final String clientId = "gemtek";

    //[REAL] clientSecret
    public static final String clientSecret = "SK.583Ybjf6kINH1mV";

    //[TEST] clientSecret
//    public static final String clientSecret = "SK.MkejvTBQFvJWy01";


    /*
    4.BIZ API 개발/운영
    */
    //개발
//    public static final String BIZ_API = "https://api-qa.rcsbizcenter.com/api/1.0/";
    //운영
    public static final String BIZ_API = "https://api.rcsbizcenter.com/api/1.0/";



    /*
    5. API Call IP Check
    */
    //local
//    public static final String IP="192.168.0.223";
    //개발
//    public static final String IP="192.168.0.208";
    //운영
    public static final String IP="1.240.13.203";



}
