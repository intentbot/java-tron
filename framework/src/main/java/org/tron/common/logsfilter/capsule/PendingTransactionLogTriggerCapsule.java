package org.tron.common.logsfilter.capsule;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.common.logsfilter.EventPluginLoader;
import org.tron.common.logsfilter.trigger.InternalTransactionPojo;
import org.tron.common.logsfilter.trigger.LogPojo;
import org.tron.common.logsfilter.trigger.PendingTransactionLogTrigger;
import org.tron.common.runtime.InternalTransaction;
import org.tron.common.runtime.ProgramResult;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.db.TransactionTrace;
import org.tron.protos.Protocol;
import org.tron.protos.contract.SmartContractOuterClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.tron.protos.Protocol.Transaction.Contract.ContractType.CreateSmartContract;

@Getter
@Setter
@Slf4j
public class PendingTransactionLogTriggerCapsule extends TriggerCapsule {
  private PendingTransactionLogTrigger pendingTransactionLogTrigger;

  public PendingTransactionLogTriggerCapsule(TransactionCapsule trxCapsule) {
    this(trxCapsule, 0, 0, null, 0);
  }

  public PendingTransactionLogTriggerCapsule(TransactionCapsule trxCapsule,
                                             int txIndex, long preCumulativeLogCount,
                                             Protocol.TransactionInfo transactionInfo, long energyUnitPrice) {
    pendingTransactionLogTrigger = new PendingTransactionLogTrigger();

    String transactionHash = trxCapsule.getTransactionId().toString();
    pendingTransactionLogTrigger.setTransactionId(transactionHash);
    pendingTransactionLogTrigger.setData(Hex.toHexString(trxCapsule
            .getInstance().getRawData().getData().toByteArray()));

    TransactionTrace trxTrace = trxCapsule.getTrxTrace();

    //result
    if (Objects.nonNull(trxCapsule.getContractRet())) {
      pendingTransactionLogTrigger.setResult(trxCapsule.getContractRet().toString());
    }

    Protocol.Transaction.raw rawData = trxCapsule.getInstance().getRawData();
    Protocol.Transaction.Contract.ContractType contractType = null;

    if (!rawData.equals(Protocol.Transaction.raw.getDefaultInstance())) {
      // fee limit
      pendingTransactionLogTrigger.setFeeLimit(rawData.getFeeLimit());

      Protocol.Transaction.Contract contract = rawData.getContract(0);
      Any contractParameter = null;

      // contract type
      contractType = contract.getType();
      if (!contractType.equals(Protocol.Transaction.Contract.ContractType.TriggerSmartContract)) {
        return;
      }
      pendingTransactionLogTrigger.setContractType(contractType.toString());

      contractParameter = contract.getParameter();

      pendingTransactionLogTrigger.setContractCallValue(TransactionCapsule.getCallValue(contract));

      if (!contractParameter.equals(com.google.protobuf.Any.getDefaultInstance())) {
        try {
          SmartContractOuterClass.TriggerSmartContract triggerSmartContract = contractParameter
                  .unpack(SmartContractOuterClass.TriggerSmartContract.class);

          if (!triggerSmartContract.getOwnerAddress().equals(com.google.protobuf.ByteString.EMPTY)) {
            pendingTransactionLogTrigger.setFromAddress(
                    StringUtil.encode58Check(triggerSmartContract.getOwnerAddress().toByteArray()));
          }

          if (!triggerSmartContract.getContractAddress().equals(com.google.protobuf.ByteString.EMPTY)) {
            pendingTransactionLogTrigger.setToAddress(StringUtil
                    .encode58Check(triggerSmartContract.getContractAddress().toByteArray()));
          }
          List<String> filterContractAddress = Args.getInstance().getEventFilter().getContractAddressList();
          if (!filterContractAddress.contains(pendingTransactionLogTrigger.getToAddress())) {
            return;
          }
          if (!triggerSmartContract.getData().equals(com.google.protobuf.ByteString.EMPTY)) {
            pendingTransactionLogTrigger.setData(StringUtil
                    .encode58Check(triggerSmartContract.getData().toByteArray()));
          }
        } catch (Exception e) {
          logger.error("failed to load contract, error '{}'", e.getMessage());
        }
      }
    }

    // program result
    if (Objects.nonNull(trxTrace) && Objects.nonNull(trxTrace.getRuntime()) && Objects
            .nonNull(trxTrace.getRuntime().getResult())) {
      ProgramResult programResult = trxTrace.getRuntime().getResult();
      ByteString contractResult = ByteString.copyFrom(programResult.getHReturn());
      ByteString contractAddress = ByteString.copyFrom(programResult.getContractAddress());

      if (Objects.nonNull(contractResult) && contractResult.size() > 0) {
        pendingTransactionLogTrigger.setContractResult(Hex.toHexString(contractResult.toByteArray()));
      }

      if (Objects.nonNull(contractAddress) && contractAddress.size() > 0) {
        if (Objects.nonNull(transactionInfo)
                && contractType != null && contractType != CreateSmartContract) {
          pendingTransactionLogTrigger.setContractAddress(null);
        } else {
          pendingTransactionLogTrigger
                  .setContractAddress(StringUtil.encode58Check((contractAddress.toByteArray())));
        }
      }

      // internal transaction
      pendingTransactionLogTrigger.setInternalTransactionList(
              getInternalTransactionList(programResult.getInternalTransactions()));
    }

    // process transactionInfo list, only enabled when ethCompatible is true
    if (Objects.nonNull(transactionInfo)) {
      pendingTransactionLogTrigger.setEnergyUnitPrice(energyUnitPrice);

      List<LogPojo> logPojoList = new ArrayList<>();
      for (int index = 0; index < transactionInfo.getLogCount(); index++) {
        Protocol.TransactionInfo.Log log = transactionInfo.getLogList().get(index);
        LogPojo logPojo = new LogPojo();

        logPojo.setAddress((log.getAddress() != com.google.protobuf.ByteString.EMPTY)
                ? Hex.toHexString(log.getAddress().toByteArray()) : "");
        logPojo.setBlockNumber(trxCapsule.getBlockNum());
        logPojo.setData(Hex.toHexString(log.getData().toByteArray()));
        logPojo.setLogIndex(preCumulativeLogCount + index);

        List<String> topics = new ArrayList<>();
        for (int i = 0; i < log.getTopicsCount(); i++) {
          topics.add(Hex.toHexString(log.getTopics(i).toByteArray()));
        }
        logPojo.setTopicList(topics);

        logPojo.setTransactionHash(transactionHash);
        logPojo.setTransactionIndex(txIndex);

        logPojoList.add(logPojo);
      }
      pendingTransactionLogTrigger.setLogList(logPojoList);
    }
  }

  private List<InternalTransactionPojo> getInternalTransactionList(
          List<InternalTransaction> internalTransactionList) {
    List<InternalTransactionPojo> pojoList = new ArrayList<>();

    internalTransactionList.forEach(internalTransaction -> {
      InternalTransactionPojo item = new InternalTransactionPojo();

      item.setHash(Hex.toHexString(internalTransaction.getHash()));
      item.setCallValue(internalTransaction.getValue());
      item.setTokenInfo(internalTransaction.getTokenInfo());
      item.setCaller_address(Hex.toHexString(internalTransaction.getSender()));
      item.setTransferTo_address(Hex.toHexString(internalTransaction.getTransferToAddress()));
      item.setData(Hex.toHexString(internalTransaction.getData()));
      item.setRejected(internalTransaction.isRejected());
      item.setNote(internalTransaction.getNote());
      item.setExtra(internalTransaction.getExtra());

      pojoList.add(item);
    });

    return pojoList;
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postPendingTransactionTrigger(pendingTransactionLogTrigger);
  }
}
