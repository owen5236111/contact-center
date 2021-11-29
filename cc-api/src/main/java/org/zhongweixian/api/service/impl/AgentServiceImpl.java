package org.zhongweixian.api.service.impl;

import cn.afterturn.easypoi.excel.ExcelExportUtil;
import cn.afterturn.easypoi.excel.entity.ExportParams;
import cn.afterturn.easypoi.excel.entity.enmus.ExcelType;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.cti.cc.entity.*;
import org.cti.cc.enums.ErrorCode;
import org.cti.cc.mapper.*;
import org.cti.cc.mapper.base.BaseMapper;
import org.cti.cc.po.AgentInfo;
import org.cti.cc.po.AgentSipPo;
import org.cti.cc.po.CompanyInfo;
import org.cti.cc.vo.AgentBindSkill;
import org.cti.cc.vo.AgentSipVo;
import org.cti.cc.vo.AgentVo;
import org.cti.cc.vo.BatchAddAgentVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.zhongweixian.api.exception.BusinessException;
import org.zhongweixian.api.service.AgentService;
import org.zhongweixian.api.util.BcryptUtil;
import org.zhongweixian.api.vo.excel.AgentImportExcel;
import org.zhongweixian.api.vo.excel.ExcelAgentEntity;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentServiceImpl extends BaseServiceImpl<Agent> implements AgentService {

    @Value("${sip.prefix:89}")
    private Integer sipPrefix;

    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private AgentSipMapper agentSipMapper;

    @Autowired
    private CompanyMapper companyMapper;

    @Autowired
    private SkillMapper skillMapper;

    @Autowired
    private SkillAgentMapper skillAgentMapper;

    @Override
    BaseMapper<Agent> baseMapper() {
        return agentMapper;
    }


    @Override
    public int saveOrUpdate(AgentVo agentVo) {
        Company company = companyMapper.selectByPrimaryKey(agentVo.getCompanyId());
        String agentKey = agentVo.getAgentId() + "@" + company.getCompanyCode();
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", agentVo.getCompanyId());

        Agent agent = new Agent();
        BeanUtils.copyProperties(agentVo, agent);
        if (agentVo.getId() == null) {
            params.put("agentId", agentVo.getAgentId());
            List<Agent> list = agentMapper.selectListByMap(params);

            if (!CollectionUtils.isEmpty(list)) {
                if (agentVo.getId() == null || !agentVo.getId().equals(list.get(0).getId())) {
                    throw new BusinessException(ErrorCode.DUPLICATE_EXCEPTION.getCode(), "数据已存在");
                }
            }
            //查看当前企业坐席总数
            Long agentCount = agentMapper.selectCountByMap(params);
            if (agentCount >= company.getAgentLimit()) {
                throw new BusinessException(ErrorCode.AGENT_OVER_LIMIT);
            }
            //新增是需要对坐席密码校验
            if (StringUtils.isBlank(agentVo.getPasswd())) {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "坐席密码不能为空");
            }
            agentVo.setPasswd(BcryptUtil.encrypt(agentVo.getPasswd()));
            agent.setAgentKey(agentKey);
            agent.setCts(Instant.now().getEpochSecond());
            return agentMapper.addAgent(agent);
        }
        params.put("id", agentVo.getId());
        Agent exist = agentMapper.selectAgentInfo(params);
        if (exist == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST);
        }
        if (!StringUtils.isBlank(agentVo.getPasswd())) {
            agent.setPasswd(BcryptUtil.encrypt(agentVo.getPasswd()));
        }
        agent.setUts(Instant.now().getEpochSecond());
        return agentMapper.updateByPrimaryKeySelective(agent);
    }

    @Override
    public Integer batchAddAgent(BatchAddAgentVo addAgentVo) {
        Company company = companyMapper.selectByPrimaryKey(addAgentVo.getCompanyId());
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", addAgentVo.getCompanyId());
        Long agentCount = agentMapper.selectCountByMap(params);
        if (agentCount >= company.getAgentLimit()) {
            throw new BusinessException(ErrorCode.AGENT_OVER_LIMIT);
        }

        List<Agent> agents = new ArrayList<>();
        List<Map<String, Object>> agentSips = new ArrayList<>();
        Integer result = 0;
        Agent agent = null;
        AgentSip agentSip = null;
        Map<String, Object> sipMap = null;
        for (Integer i = 0; i < addAgentVo.getCount(); i++) {
            agent = new Agent();
            agent.setCompanyId(addAgentVo.getCompanyId());
            agent.setAgentId(addAgentVo.getPrefix() + (addAgentVo.getStart() + i));
            agent.setPasswd(BcryptUtil.encrypt(addAgentVo.getPasswd()));
            agent.setAgentKey(agent.getAgentId() + "@" + company.getCompanyCode());
            agent.setAgentName(agent.getAgentId());
            agent.setAgentType(1);
            agent.setCts(Instant.now().getEpochSecond());
            agent.setStatus(1);
            agents.add(agent);

            //添加sip号
            sipMap = new HashMap<>();
            sipMap.put("cts", agent.getCts());
            sipMap.put("companyId", addAgentVo.getCompanyId());
            sipMap.put("sip", sipPrefix + "" + Instant.now().toEpochMilli());
            sipMap.put("agentKey", agent.getAgentKey());
            sipMap.put("sipPwd", RandomStringUtils.randomNumeric(16));
            agentSips.add(sipMap);

            result = result + 1;
        }
        agentMapper.batchInsert(agents);
        agentSipMapper.batchInsert(agentSips);
        return result;
    }

    @Override
    public AgentInfo getAgentInfo(Long companyId, Long id) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("id", id);
        params.put("companyId", companyId);
        AgentInfo agentInfo = agentMapper.selectAgentInfo(params);
        if (agentInfo == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST);
        }
        return agentInfo;
    }

    @Override
    public int agentBindSkill(Long companyId, Long skillId, List<Long> ids, Integer rank) {
        Skill skill = skillMapper.selectById(skillId, companyId);
        if (skill == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST);
        }
        if (CollectionUtils.isEmpty(ids)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        List<SkillAgent> skillAgents = new ArrayList<>();
        for (Long id : ids) {
            SkillAgent skillAgent = new SkillAgent();
            skillAgent.setAgentId(id);
            skillAgent.setSkillId(skillId);
            skillAgent.setCompanyId(companyId);
            skillAgent.setRankValue(rank);
            skillAgent.setStatus(1);
            skillAgents.add(skillAgent);
        }
        skillAgentMapper.batchInsert(skillAgents);
        return 0;
    }

    @Override
    public int deleteAgent(Long companyId, Long id) {
        Agent agent = agentMapper.selectByPrimaryKey(id);
        if (agent == null || agent.getStatus() == 0 || !agent.getCompanyId().equals(companyId)) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST);
        }
        agent.setAgentKey(agent.getAgentKey() + randomDelete());
        agent.setUts(Instant.now().getEpochSecond());
        agent.setStatus(0);
        return agentMapper.deleteAgent(agent);
    }

    @Override
    public PageInfo<AgentSipPo> agentSipList(Map<String, Object> params) {
        Integer pageNum = (Integer) params.get("pageNum");
        Integer pageSize = (Integer) params.get("pageSize");
        PageHelper.startPage(pageNum, pageSize);
        List<AgentSipPo> list = agentSipMapper.selectAgentSip(params);
        return new PageInfo<>(list);
    }

    @Override
    public int saveOrUpdateAgentSip(AgentSipVo agentSipVo) {
        AgentSip agentSip = new AgentSip();
        BeanUtils.copyProperties(agentSipVo, agentSip);

        Map<String, Object> params = new HashMap<>();
        params.put("sip", agentSipVo.getSip());
        List<AgentSipPo> list = agentSipMapper.selectAgentSip(params);
        if (!CollectionUtils.isEmpty(list)) {
            if (agentSipVo.getId() == null || !agentSipVo.getId().equals(list.get(0).getId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_EXCEPTION);
            }
        }
        if (agentSipVo.getId() == null) {
            agentSip.setCts(Instant.now().getEpochSecond());
            return agentSipMapper.insertSelective(agentSip);
        }
        //修改sip账号时，判断是否存在
        AgentSip exist = agentSipMapper.selectById(agentSipVo.getCompanyId(), agentSipVo.getId());
        if (exist == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST);
        }
        agentSip.setUts(Instant.now().getEpochSecond());
        if (agentSipVo.getAgentId() != null) {
            //判断agentId是否存在
            params.put("id", agentSipVo.getAgentId());
            params.put("companyId", agentSipVo.getCompanyId());
            Agent agent = agentMapper.selectAgentInfo(params);
            if (agent == null) {
                throw new BusinessException(ErrorCode.DATA_NOT_EXIST);
            }
        }
        return agentSipMapper.updateByPrimaryKeySelective(agentSip);
    }

    @Override
    public int deleteSip(Long companyId, Long id) {
        //修改sip账号时，判断是否存在
        AgentSip agentSip = agentSipMapper.selectById(companyId, id);
        if (agentSip == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST);
        }
        //判断是否被坐席使用
        Agent agent = agentMapper.selectByPrimaryKey(agentSip.getAgentId());
        if (agent != null) {
            throw new BusinessException(ErrorCode.DATA_IS_USED);
        }
        agentSip.setUts(Instant.now().getEpochSecond());
        agentSip.setStatus(0);
        agentSip.setSip(agentSip.getSip() + randomDelete());
        return agentSipMapper.updateByPrimaryKeySelective(agentSip);
    }

    @Override
    public void agentExport(HttpServletResponse response, Map<String, Object> params) throws IOException {
        List<Agent> agentList = agentMapper.selectListByMap(params);
//        agentList.forEach(agent->agent.setCts(FormatDateUtil.stampToTime(Long.parseLong(e.getUpdateTime()))));
        Workbook workbook = ExcelExportUtil.exportExcel(new ExportParams(
                null, "坐席列表", ExcelType.XSSF), ExcelAgentEntity.class, agentList);
        String filename = URLEncoder.encode("坐席列表.xlsx", "UTF8");
        response.setHeader("content-disposition", "attachment;Filename=" + filename);
        response.setContentType("application/vnd.ms-excel");
        ServletOutputStream out = response.getOutputStream();
        workbook.write(out);
        out.flush();
    }

    @Override
    public int agentImport(List<AgentImportExcel> agentList, Long companyId) {
        if (CollectionUtils.isEmpty(agentList)) {
            return 0;
        }
        CompanyInfo companyInfo = companyMapper.selectById(companyId);
        if (companyInfo.getAgentSize() + agentList.size() > companyInfo.getAgentLimit()) {
            throw new BusinessException(ErrorCode.AGENT_OVER_LIMIT);
        }

        Integer result = 0;
        for (AgentImportExcel agentImportExcel : agentList) {
            if (StringUtils.isBlank(agentImportExcel.getAgentKey())) {
                continue;
            }
            Agent existAgent = agentMapper.selectAgent(agentImportExcel.getAgentKey() + "@" + companyInfo.getCompanyCode());
            AgentSip existSip = agentSipMapper.selectBySip(agentImportExcel.getSip());
            if (existAgent != null || existSip != null) {
                continue;
            }
            Agent agent = new Agent();
            agent.setCompanyId(companyId);
            agent.setAgentKey(agentImportExcel.getAgentKey() + "@" + companyInfo.getCompanyCode());
            agent.setCts(Instant.now().getEpochSecond());
            agent.setAgentId(agentImportExcel.getAgentKey());
            agent.setAgentName(agentImportExcel.getAgentName());
            agent.setSipPhone(agentImportExcel.getSipPhone());
            agent.setPasswd(StringUtils.isBlank(agentImportExcel.getPasswd()) ?
                    BcryptUtil.encrypt(agentImportExcel.getAgentKey()) :
                    BcryptUtil.encrypt(agentImportExcel.getPasswd()));
            agent.setAgentType(1);
            agentMapper.addAgent(agent);
            result = result + 1;

            if (StringUtils.isBlank(agentImportExcel.getSip())) {
                continue;
            }
            AgentSip agentSip = new AgentSip();
            agentSip.setSip(agentImportExcel.getSip());
            agentSip.setAgentId(agent.getId());
            agentSip.setCts(Instant.now().getEpochSecond());
            agentSip.setCompanyId(companyId);
            agentSipMapper.insertSelective(agentSip);
        }
        return result;
    }

    @Override
    public int agentBindSkill(AgentBindSkill agentBindSkill) {
        /**
         * 先判断坐席
         */
        Skill skill = skillMapper.selectById(agentBindSkill.getCompanyId(), agentBindSkill.getSkillId());
        if (skill == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", agentBindSkill.getCompanyId());
        params.put("ids", agentBindSkill.getAgentIds());
        List<Agent> agents = agentMapper.selectListByMap(params);
        if (CollectionUtils.isEmpty(agents)) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST);
        }
        //删除已有数据
        skillAgentMapper.deleteSkillAgent(agentBindSkill);

        List<SkillAgent> skillAgents = new ArrayList<>();
        for (Agent agent : agents) {
            SkillAgent skillAgent = new SkillAgent();
            skillAgent.setCompanyId(agentBindSkill.getCompanyId());
            skillAgent.setAgentId(agent.getId());
            skillAgent.setSkillId(agentBindSkill.getSkillId());
            skillAgent.setRankValue(agentBindSkill.getRankValue());
            skillAgents.add(skillAgent);
        }
        return skillAgentMapper.batchInsert(skillAgents);
    }

}
