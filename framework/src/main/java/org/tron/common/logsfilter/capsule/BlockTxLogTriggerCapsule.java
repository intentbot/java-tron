package org.tron.common.logsfilter.capsule;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.common.logsfilter.EventPluginLoader;
import org.tron.common.logsfilter.trigger.BlockTxLogTrigger;
import org.tron.common.logsfilter.trigger.LogPojo;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.args.Args;
import org.tron.protos.Protocol;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
@Setter
public class BlockTxLogTriggerCapsule extends TriggerCapsule {
  BlockTxLogTrigger blockTxLogTrigger;

  public BlockTxLogTriggerCapsule(BlockCapsule block) {
    blockTxLogTrigger = new BlockTxLogTrigger();
    List<String> contractAddressList = Args.getInstance().getEventFilter().getContractAddressList();
    byte[] txRetBytes = block.getResult().getData();
    Protocol.TransactionRet transactionRet = null;
    try {
      transactionRet = Protocol.TransactionRet.parseFrom(txRetBytes);
    } catch (InvalidProtocolBufferException e) {
      logger.error("BlockTxLogTriggerCapsule parseFrom error", e);
    }
    assert transactionRet != null;
    ArrayList<LogPojo> logPojoList = new ArrayList<>();
    int customerTxIndex = 0;
    for (Protocol.TransactionInfo tx : transactionRet.getTransactioninfoList()) {
      if (tx.getContractAddress() == com.google.protobuf.ByteString.EMPTY) {
        continue;
      }
      for (int index = 0; index < tx.getLogCount(); index++) {
        Protocol.TransactionInfo.Log log = tx.getLogList().get(index);
        LogPojo logPojo = new LogPojo();

        String hexAddress = "41".concat(Hex.toHexString(log.getAddress().toByteArray()));
        String contractAddress = StringUtil.encode58Check(Hex.decode(hexAddress));
        if (!contractAddressList.contains(contractAddress)) {
          continue;
        }
        logPojo.setAddress(contractAddress);
        logPojo.setData(Hex.toHexString(log.getData().toByteArray()));

        List<String> topics = new ArrayList<>();
        for (int i = 0; i < log.getTopicsCount(); i++) {
          topics.add(Hex.toHexString(log.getTopics(i).toByteArray()));
        }
        logPojo.setTopicList(topics);

        logPojo.setTransactionHash(Hex.toHexString(tx.getId().toByteArray()));
        logPojo.setLogIndex(index);
        logPojo.setTransactionIndex(customerTxIndex);

        logPojoList.add(logPojo);
        customerTxIndex++;
      }
    }
    blockTxLogTrigger.setLogPojoList(logPojoList);
    blockTxLogTrigger.setBlockNumber(block.getNum());
    blockTxLogTrigger.setTimeStamp(block.getTimeStamp());
    blockTxLogTrigger.setBlockHash(block.getBlockId().toString());
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postBlockTxLogTrigger(blockTxLogTrigger);
  }
}