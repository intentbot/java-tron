package org.tron.common.logsfilter.trigger;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Setter
@Getter
public class BlockTxLogTrigger extends Trigger {
  private long blockNumber;

  private String blockHash;

  private ArrayList<LogPojo> logPojoList;

  public BlockTxLogTrigger() {
    setTriggerName(Trigger.BLOCK_TX_TRIGGER_NAME);
  }

  @Override
  public String toString() {
    return new StringBuilder().append("triggerName: ").append(getTriggerName())
        .append("timestamp: ")
        .append(timeStamp)
        .append(", blockNumber: ")
        .append(blockNumber)
        .append(", blockhash: ")
        .append(blockHash)
        .append(", transactionList: ")
        .append(logPojoList).toString();
  }
}
