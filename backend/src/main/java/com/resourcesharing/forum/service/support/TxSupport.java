package com.resourcesharing.forum.service.support;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Service
public class TxSupport {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;

    public TxSupport(
            ObjectProvider<JdbcTemplate> jdbcProvider,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider
    ) {
        this.jdbcProvider = jdbcProvider;
        this.transactionManagerProvider = transactionManagerProvider;
    }

    public JdbcTemplate jdbc() {
        return jdbcProvider.getIfAvailable();
    }

    public <T> T required(Supplier<T> action) {
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            return action.get();
        }
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    public <T> T requiresNew(Supplier<T> action) {
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            return action.get();
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }
}
