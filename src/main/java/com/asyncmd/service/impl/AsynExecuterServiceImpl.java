
package com.asyncmd.service.impl;

import com.asyncmd.config.AsynGroupConfig;
import com.asyncmd.dao.AsynCmdDAO;
import com.asyncmd.dao.AsynCmdHistoryDAO;
import com.asyncmd.enums.AsynStatus;
import com.asyncmd.model.AbstractAsynExecuter;
import com.asyncmd.model.AsynCmd;
import com.asyncmd.model.AsynCmdDO;
import com.asyncmd.model.AsynCmdHistoryDO;
import com.asyncmd.model.AsynUpdateParam;
import com.asyncmd.model.Frequency;
import com.asyncmd.service.AsynExecuterService;
import com.asyncmd.utils.AsynExecuterUtil;
import com.asyncmd.utils.LocalHostUtil;
import com.asyncmd.utils.SubTableUtil;
import com.asyncmd.utils.TransactionTemplateUtil;
import com.asyncmd.utils.convert.AsynCmdConvert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author wangwendi
 * @version $Id: AsynExecuterService.java, v 0.1 2018年09月30日 wangwendi Exp $
 */
@Service
public class AsynExecuterServiceImpl  implements AsynExecuterService {

    @Autowired
    private AsynCmdDAO asynCmdDAO;

    @Autowired
    private AsynCmdHistoryDAO asynCmdHistoryDAO;
    @Autowired
    private AsynGroupConfig asynGroupConfig;
    /**
     * 插入异步命令
     * @param asynCmd
     * @return
     */
    public long saveCmd(AsynCmd asynCmd){
        AsynCmdDO asynCmdDO = AsynCmdConvert.toDo(asynCmd);
        return asynCmdDAO.saveCmd(asynCmdDO);
    }




    /**
     * 备份异步命令
     * @param asynCmd
     */
    public void backupCmd(final AsynCmd asynCmd){
        //重新捞取最新的一条数据
        final AsynCmd newAsynCmd = getAsynCmdByBizId(asynCmd.getBizId(), asynCmd.getClass());
        if (newAsynCmd == null){
            return;
        }
        TransactionTemplateUtil.newInstance().getTemplate().execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {

                asynCmdDAO.delCmd(asynCmd.getBizId());
                asynCmdHistoryDAO.saveCmd(AsynCmdConvert.toHistoryCmd(newAsynCmd));

            }
        });
    }

    public boolean updateStatus(AsynUpdateParam param) {
        buildUpdateParam(param);
        long sum = asynCmdDAO.updateStatus(param);
        if (sum > 0){
            return true;
        }
        return false;
    }

    public boolean batchUpdateStatus(AsynUpdateParam param, Integer tableIndex) {
        buildUpdateParam(param);

        Long sum = asynCmdDAO.batchUpdateStatus(tableIndex,param);
        if (sum > 0){
            return true;
        }
        return false;

    }

    public List<AsynCmd> queryAsynCmd(int limit,int tableIndex,AsynStatus status,Date whereNextTime) {

        Boolean desc = asynGroupConfig.getAsynConfig().getDesc();
        List<AsynCmdDO> asynCmdDOS = asynCmdDAO.queryAsynCmd(tableIndex,status.getStatus(),limit, whereNextTime,desc);
        return AsynCmdConvert.toCmdList(asynCmdDOS);
    }


    public Date getNextTime(List<Frequency> executerFrequencyList,AsynCmd asynCmd) {
        int retry = asynCmd.getExecuteNum() - 1;
        if (!CollectionUtils.isEmpty(executerFrequencyList)){
            return nextTime(retry,executerFrequencyList,asynCmd);
        }
        List<Frequency> groupExecuterFrequencys = asynGroupConfig.getAsynConfig().getExecuterFrequency();
        return nextTime(retry,groupExecuterFrequencys,asynCmd);
    }

    private Date nextTime(int retry,List<Frequency> executerFrequencyList,AsynCmd asynCmd){
        Frequency frequency;
        if (executerFrequencyList.size() < retry){
            frequency = executerFrequencyList.get(executerFrequencyList.size() - 1);
        }else {
            frequency = executerFrequencyList.get(retry);
        }
        return frequency.getNextTime(asynCmd.getNextTime());
    }

    public AsynCmd getAsynCmdByBizId(String bizId,Class<? extends AsynCmd> asynCmdClass) {
        return AsynCmdConvert.toCmd(asynCmdDAO.getAsynCmdByBizId(bizId),asynCmdClass);
    }

    private void buildUpdateParam(AsynUpdateParam param){
        param.setUpdateIp(LocalHostUtil.getIp());
        param.setUpdateHostName(LocalHostUtil.getHostName());
    }
}