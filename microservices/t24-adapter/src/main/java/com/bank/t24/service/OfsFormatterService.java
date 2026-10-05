package com.bank.t24.service;

import com.bank.t24.dto.T24TransferRequest;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Builds Temenos T24 OFS (Open Financial Service) message strings for funds transfer.
 *
 * Example OFS string output:
 *   FUNDS.TRANSFER,PAYPINK-TX-PH-1791004427-abc1234/I/PROCESS,,DEBIT.ACCT.NO::1000100001,CREDIT.ACCT.NO::1000100002,AMOUNT::15000.00,CURRENCY::PHP
 */
@Service
public class OfsFormatterService {

    public String buildFundsTransferOfs(T24TransferRequest request) {
        String ofsReference = "PAYPINK-" + request.getReferenceNo();
        return String.format(
                Locale.US,
                "FUNDS.TRANSFER,%s/I/PROCESS,,DEBIT.ACCT.NO::%s,CREDIT.ACCT.NO::%s,AMOUNT::%.2f,CURRENCY::%s",
                ofsReference,
                request.getDebitAccountNo(),
                request.getCreditAccountNo(),
                request.getAmount(),
                request.getCurrency().toUpperCase()
        );
    }
}
