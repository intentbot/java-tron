package org.tron.common.logsfilter.trigger;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@Getter
public class PendingTransactionLogTrigger extends Trigger {

  private String transactionId;
  //contract
  private String result;
  private String contractAddress;
  private String contractType;
  private long feeLimit;
  private long contractCallValue;
  private String contractResult;
  // transfer contract
  private String fromAddress;
  private String toAddress;
  private String assetName;
  private long assetAmount;
  private long latestSolidifiedBlockNumber;
  private long expiration;
  private long localTime;
  //internal transaction
  private List<InternalTransactionPojo> internalTransactionList;
  private String data;
  private List<LogPojo> logList;
  private long energyUnitPrice;

  private Map<String, Long> extMap;

  public PendingTransactionLogTrigger() {
    setTriggerName(Trigger.PENDING_TRANSACTION_TRIGGER_NAME);
  }

  @Override
  public void setTimeStamp(long ts) {
    super.timeStamp = ts;
  }
}
