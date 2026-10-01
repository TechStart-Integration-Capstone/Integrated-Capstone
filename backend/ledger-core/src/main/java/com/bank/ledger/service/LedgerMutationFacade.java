package com.bank.ledger.service;

import com.bank.ledger.dto.MutationRequest;
import com.bank.ledger.dto.MutationResponse;
import com.bank.ledger.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class LedgerMutationFacade {

    private final LedgerMutationService ledgerMutationService;
    private final FailedTransactionRecorder failedTransactionRecorder;

    public LedgerMutationFacade(LedgerMutationService ledgerMutationService,
                                FailedTransactionRecorder failedTransactionRecorder) {
        this.ledgerMutationService = ledgerMutationService;
        this.failedTransactionRecorder = failedTransactionRecorder;
    }

    /**
     * Non-transactional facade orchestrating mutation and post-rollback failure recording.
     * Prevents connection pool deadlocks by recording failed attempts after the main transaction rolls back.
     */
    public MutationResponse executeMutation(MutationRequest request, String username) {
        try {
            return ledgerMutationService.mutateBalance(request, username);
        } catch (BusinessException ex) {
            failedTransactionRecorder.recordFailure(request, ex);
            throw ex;
        }
    }
}
