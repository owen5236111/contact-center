package org.zhongweixian.api.service;

import com.github.pagehelper.PageInfo;
import org.cti.cc.entity.CallLog;
import org.cti.cc.po.CallLogPo;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Created by caoliang on 2021/9/5
 */
public interface CallLogService extends BaseService<CallLog> {


    /**
     * 按月分表
     *
     * @param month 当前月份
     */
    void subTable(String month);


    /**
     * 分页
     *
     * @param params
     * @return
     */
    PageInfo<CallLogPo> calllogList(Map<String, Object> params);

    /**
     * 话单导出
     *
     * @param response
     * @param params
     */
    void calllogExport(HttpServletResponse response, Map<String, Object> params) throws IOException;

    /**
     * 删除历史数据
     *
     * @param time
     * @return
     */
    int cleaDayOfData(Long time);
}
