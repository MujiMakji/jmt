package kr.co.gmgo.rcs.api.biz.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * 레벨별 로그 기록을 위한 필터링 설정 클래스
 * @author Aaron
 */
public class CustomLogFilter extends Filter<ILoggingEvent> {

    private String levels;

    public String getLevels() {
        return levels;
    }

    public void setLevels(String levels) {
        this.levels = levels;
    }

    private Level[] level;

    @Override
    public FilterReply decide(ILoggingEvent arg0) {

        if (level == null && levels != null) {
            setLevels();
        }
        if (level != null) {
            for (Level lev : level) {
                if (lev == arg0.getLevel()) {
                    return FilterReply.ACCEPT;
                }
            }
        }

        return FilterReply.DENY;
    }

    private void setLevels() {

        if (!levels.isEmpty()) {
            level = new Level[levels.split("\\|").length];
            int i = 0;
            for (String str : levels.split("\\|")) {
                level[i] = Level.valueOf(str);
                i++;
            }
        }
    }
}