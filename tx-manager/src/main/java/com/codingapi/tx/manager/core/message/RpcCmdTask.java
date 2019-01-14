package com.codingapi.tx.manager.core.message;

import com.codingapi.tx.logger.TxLogger;
import com.codingapi.tx.manager.support.ManagerRpcBeanHelper;
import com.codingapi.tx.client.spi.message.RpcClient;
import com.codingapi.tx.client.spi.message.dto.MessageDto;
import com.codingapi.tx.client.spi.message.dto.RpcCmd;
import com.codingapi.tx.client.spi.message.exception.RpcException;
import com.codingapi.tx.client.spi.message.LCNCmdType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Description:
 * Date: 2018/12/12
 *
 * @author ujued
 */
@Slf4j
public class RpcCmdTask implements Runnable {

    private final RpcCmd rpcCmd;

    private final ManagerRpcBeanHelper rpcBeanHelper;

    private final RpcClient rpcClient;

    private final TxLogger txLogger;

    public RpcCmdTask(ManagerRpcBeanHelper rpcBeanHelper, RpcCmd rpcCmd) {
        this.rpcBeanHelper = rpcBeanHelper;
        this.rpcCmd = rpcCmd;
        this.rpcClient = rpcBeanHelper.getByType(RpcClient.class);
        this.txLogger = rpcBeanHelper.getByType(TxLogger.class);
    }

    @Override
    public void run() {
        TransactionCmd transactionCmd = parser(rpcCmd);
        String action = transactionCmd.getMsg().getAction();
        RpcExecuteService rpcExecuteService = rpcBeanHelper.loadManagerService(transactionCmd.getType());
        MessageDto messageDto = null;
        try {
            Object message = rpcExecuteService.execute(transactionCmd);
            messageDto = MessageCreator.notifyGroupOkResponse(message,action);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            messageDto = MessageCreator.notifyGroupFailResponse(e,action);
            txLogger.trace(transactionCmd.getGroupId(),"","rpccmd","error->"+messageDto.getAction());
        } finally {
            // 对需要响应信息的请求做出响应
            if (rpcCmd.getKey() != null) {
                assert Objects.nonNull(messageDto);
                try {
                    messageDto.setGroupId(rpcCmd.getMsg().getGroupId());
                    rpcCmd.setMsg(messageDto);
                    rpcClient.send(rpcCmd);
                    txLogger.trace(transactionCmd.getGroupId(),"","rpccmd","success->"+messageDto.getAction());
                } catch (RpcException ignored) {
                }
            }
        }
    }

    private TransactionCmd parser(RpcCmd rpcCmd) {
        TransactionCmd cmd = new TransactionCmd();
        cmd.setRequestKey(rpcCmd.getKey());
        cmd.setRemoteKey(rpcCmd.getRemoteKey());
        cmd.setType(LCNCmdType.parserCmd(rpcCmd.getMsg().getAction()));
        cmd.setGroupId(rpcCmd.getMsg().getGroupId());
        cmd.setMsg(rpcCmd.getMsg());
        return cmd;
    }
}
