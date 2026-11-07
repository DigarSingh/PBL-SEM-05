package com.shopsmart.storage;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps files and database rows consistent: files written during a transaction are removed if
 * it rolls back; files of deleted rows are removed only once the deletion has committed.
 */
@Component
public class StorageCleanup {

    private final StorageService storage;

    public StorageCleanup(StorageService storage) {
        this.storage = storage;
    }

    public void deleteOnRollback(List<String> keys) {
        register(keys, TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    public void deleteAfterCommit(List<String> keys) {
        register(keys, TransactionSynchronization.STATUS_COMMITTED);
    }

    private void register(List<String> keys, int statusToAct) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (statusToAct == TransactionSynchronization.STATUS_COMMITTED) {
                keys.forEach(storage::delete);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == statusToAct) {
                    keys.forEach(storage::delete);
                }
            }
        });
    }
}
