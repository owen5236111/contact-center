package org.zhongweixian.api.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.cti.cc.entity.CallLog;
import org.cti.cc.mapper.CallDetailMapper;
import org.cti.cc.mapper.CallDeviceMapper;
import org.cti.cc.mapper.CallDtmfMapper;
import org.cti.cc.mapper.CallLogMapper;
import org.cti.cc.mapper.base.BaseMapper;
import org.cti.cc.po.CallLogPo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zhongweixian.api.service.CallLogService;

import java.util.List;
import java.util.Map;

/**
 * Created by caoliang on 2021/9/5
 */
@Service
public class CallLogServiceImpl extends BaseServiceImpl<CallLog> implements CallLogService {

    @Autowired
    private CallDeviceMapper callDeviceMapper;

    @Autowired
    private CallLogMapper callLogMapper;

    @Autowired
    private CallDetailMapper callDetailMapper;

    @Autowired
    private CallDtmfMapper callDtmfMapper;

    @Override
    BaseMapper<CallLog> baseMapper() {
        return callLogMapper;
    }

    @Override
    public void subTable(Long start, Long end, String month) {
        // cc_call_log
        callLogMapper.createNewTable(start, end, month);
        callLogMapper.clearTable(start, end);


        //cc_call_device
        callDeviceMapper.createNewTable(start, end, month);
        callDeviceMapper.clearTable(start, end);

    }

    @Override
    public PageInfo<CallLogPo> calllogList(Map<String, Object> params) {
        Integer pageNum = (Integer) params.get("pageNum");
        Integer pageSize = (Integer) params.get("pageSize");
        PageHelper.startPage(pageNum, pageSize);
        List<CallLogPo> list = callLogMapper.getcallList(params);
        return new PageInfo<>(list);
    }


}
